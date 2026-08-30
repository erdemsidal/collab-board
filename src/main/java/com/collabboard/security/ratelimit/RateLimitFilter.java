package com.collabboard.security.ratelimit;

import com.collabboard.common.dto.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kimlik gerektirmeyen uçlara IP başına hız sınırı uygular.
 *
 * NEDEN EN ÖNDE? @Order(HIGHEST_PRECEDENCE): sınıra takılan istek, kimlik
 * doğrulama zincirine hiç girmesin. Şifre karşılaştırması (BCrypt) kasten
 * pahalıdır — saldırganın işlemci zamanımızı harcamasına izin vermenin anlamı yok.
 *
 * KOVALAR BELLEKTE. Redis'te tutulabilirdi ama şu hâliyle her sunucu kendi
 * sayacını tutuyor; iki sunucuda etkin sınır iki katına çıkar. Kabul edildi:
 * amaç kesin bir kota uygulamak değil, ucuz kötüye kullanımı pahalı hâle
 * getirmek. Sunucu sayısı arttığında bucket4j'in Redis desteğine geçilir.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    /**
     * Anahtar: "POLİTİKA|ip". Sınırsız büyümez çünkü politika sayısı sabit ve
     * IP sayısı pratikte sınırlı; yine de uzun ömürlü bir süreçte birikeceği
     * için ölçek büyüdüğünde süresi dolan girdilerin temizlenmesi gerekir.
     */
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public RateLimitFilter(ObjectMapper objectMapper,
                           @Value("${app.rate-limit.enabled:true}") boolean enabled) {
        this.objectMapper = objectMapper;
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

        Bucket bucket = buckets.computeIfAbsent(policy.name() + "|" + clientIp(request),
                key -> policy.newBucket());

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
            return;
        }

        log.warn("Hız sınırı aşıldı — uç: {}, ip: {}", policy.path(), clientIp(request));
        reject(response, policy);
    }

    /** Yalnızca POST istekleri sınırlanır; korunan uçların hepsi POST. */
    private RateLimitPolicy policyFor(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        String path = request.getRequestURI();
        for (RateLimitPolicy policy : RateLimitPolicy.values()) {
            if (policy.path().equals(path)) {
                return policy;
            }
        }
        return null;
    }

    /**
     * İstemcinin IP adresi.
     *
     * Canlıda uygulama bir ters vekil (Railway/Render yük dengeleyici) arkasında
     * çalışır; orada getRemoteAddr() vekilin adresini verir ve HERKES tek bir
     * IP'den geliyormuş gibi görünür — sınır de facto küresel olurdu. Bu yüzden
     * X-Forwarded-For'un ilk değeri tercih edilir.
     *
     * Başlık istemci tarafından uydurulabilir; ama vekil arkasında değilsek
     * zaten getRemoteAddr() doğrudur ve vekil arkasındaysak vekil bu başlığı
     * kendisi yazar. Kalan risk, sınırı atlatmak için başlık uyduran bir
     * saldırgandır — bu uçlarda kabul edilebilir bir artık risk.
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 429 Too Many Requests.
     *
     * Gövde için ApiErrorResponse kullanılıyor — elle kurulmuş bir Map değil.
     * Arayüz hata mesajını her yerde aynı alandan okuyor; biçimi burada elle
     * tekrarlamak, o kayıt değiştiğinde sessizce ayrışırdı.
     * Retry-After başlığı standarttır; ne kadar bekleneceğini söyler.
     */
    private void reject(HttpServletResponse response, RateLimitPolicy policy) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(policy.retryAfterSeconds()));

        objectMapper.writeValue(response.getWriter(), ApiErrorResponse.of(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                policy.message(),
                policy.path()));
    }
}
