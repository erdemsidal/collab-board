package com.collabboard.security.ratelimit;

import com.collabboard.common.dto.ApiErrorResponse;
import com.collabboard.i18n.AppLocale;
import com.collabboard.i18n.Messages;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.time.Duration;

/**
 * Kimlik gerektirmeyen uçlara IP başına hız sınırı uygular.
 *
 * NEDEN EN ÖNDE? @Order(HIGHEST_PRECEDENCE): sınıra takılan istek, kimlik
 * doğrulama zincirine hiç girmesin. Şifre karşılaştırması (BCrypt) kasten
 * pahalıdır — saldırganın işlemci zamanımızı harcamasına izin vermenin anlamı yok.
 *
 * İSTEMCİNİN IP'Sİ (ADR 0025): request.getRemoteAddr(). Canlıda uygulama
 * Railway'in vekilinin arkasında; IP'yi vekilin yazdığı X-Forwarded-For'dan
 * Tomcat'in RemoteIpValve'ı çıkarır (server.forward-headers-strategy: native).
 * Valve başlığı SAĞDAN okur ve yalnızca güvenilen vekillerin (özel ağ adresleri
 * ve TRUSTED_PROXIES) eklediği kısmı atlar. Eskiden burada başlığın İLK değeri
 * okunuyordu; o değeri istemcinin kendisi yazar, her istekte değiştirerek sınırı
 * sıfırlayabiliyordu.
 *
 * KOVALAR BELLEKTE, SINIRLI BİR ÖNBELLEKTE. Her sunucu kendi sayacını tutar; iki
 * sunucuda etkin sınır iki katına çıkar. Kabul edildi: amaç kesin bir kota değil,
 * ucuz kötüye kullanımı pahalı hâle getirmek. Hesap başına sınır ise Redis'te
 * ve sunucu sayısından bağımsız (AccountLoginThrottle).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    /**
     * En fazla bu kadar kova tutulur. Her kova birkaç yüz bayt; üst sınır
     * birkaç on megabayt eder. Sınır dolarsa en az kullanılanlar atılır.
     */
    private static final long MAX_BUCKETS = 50_000;

    /**
     * Bir saat kullanılmayan kova atılır. En uzun politika periyodu bir saat
     * (RateLimitPolicy); o kadar boşta kalan kova zaten tamamen dolmuştur, atmak
     * hiçbir sınırı gevşetmez.
     */
    private static final Duration IDLE_EXPIRY = Duration.ofHours(1);

    /** Yol eşleşmesi çözülmüş yolla: "/api/auth/%6cogin" de "/api/auth/login"dir. */
    private static final UrlPathHelper PATH_HELPER = UrlPathHelper.defaultInstance;

    private static final AntPathMatcher PATHS = new AntPathMatcher();

    /** Anahtar: "POLİTİKA|ip". */
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(MAX_BUCKETS)
            .expireAfterAccess(IDLE_EXPIRY)
            .build();
    private final ObjectMapper objectMapper;
    private final Messages messages;
    private final boolean enabled;

    public RateLimitFilter(ObjectMapper objectMapper, Messages messages,
                           @Value("${app.rate-limit.enabled:true}") boolean enabled) {
        this.objectMapper = objectMapper;
        this.messages = messages;
        this.enabled = enabled;
    }

    /**
     * Sınır kapatılabilir olmalı: entegrasyon testleri onlarca hesap açıp
     * giriş yapıyor ve hepsi aynı IP'den geliyor; saatlik kayıt sınırı bu
     * testleri anında keserdi. Varsayılan AÇIK — kapatmak bilinçli bir tercih
     * olmalı, unutulan bir ayar değil.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        RateLimitPolicy policy = policyFor(request);
        if (policy == null) {
            chain.doFilter(request, response);
            return;
        }

        String ip = request.getRemoteAddr();
        Bucket bucket = buckets.get(policy.name() + "|" + ip, key -> policy.newBucket());

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
            return;
        }

        log.warn("Hız sınırı aşıldı — uç: {}, ip: {}", policy.path(), ip);
        reject(request, response, policy);
    }

    /** Yalnızca POST istekleri sınırlanır; korunan uçların hepsi POST. */
    private RateLimitPolicy policyFor(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        // Ham getRequestURI() DEĞİL: kodlanmış bir yol ("/api/auth/%6cogin") Spring
        // tarafından çözülüp giriş ucuna ulaşıyor, ama ham hâli kalıba uymadığı için
        // sınıra takılmıyordu. Çözülmüş ve ";..." eklerinden arındırılmış yolla eşleşir.
        String path = PATH_HELPER.getPathWithinApplication(request);
        for (RateLimitPolicy policy : RateLimitPolicy.values()) {
            // Kalıp: "/api/boards/*/invitations" gibi yolun ortasında kimlik taşıyan uçlar için.
            if (PATHS.match(policy.path(), path)) {
                return policy;
            }
        }
        return null;
    }

    /**
     * 429 Too Many Requests.
     *
     * Gövde için ApiErrorResponse kullanılıyor — elle kurulmuş bir Map değil.
     * Arayüz hata mesajını her yerde aynı alandan okuyor; biçimi burada elle
     * tekrarlamak, o kayıt değiştiğinde sessizce ayrışırdı.
     * Retry-After başlığı standarttır; ne kadar bekleneceğini söyler.
     * Mesaj isteğin dilinde; filtre Spring MVC'den önce çalıştığı için dil
     * başlıktan burada okunur (ADR 0020).
     */
    private void reject(HttpServletRequest request, HttpServletResponse response, RateLimitPolicy policy)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(policy.retryAfterSeconds()));

        objectMapper.writeValue(response.getWriter(), ApiErrorResponse.of(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                messages.get(AppLocale.of(request), policy.messageKey()),
                request.getRequestURI()));
    }
}
