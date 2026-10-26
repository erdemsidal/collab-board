package com.collabboard.security;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Herkese açık geliştirme anahtarıyla gerçek bir ortamda çalışılmasını engeller.
 *
 * SORUN: application.yml'deki varsayılan JWT anahtarı depoda düz metin duruyor —
 * yani GitHub'ı açan herkes onu görebiliyor. O anahtarla canlıya çıkılırsa
 * isteyen kendine istediği kullanıcı için geçerli bir token üretir; şifreye
 * gerek kalmadan herkesin hesabına girilir.
 *
 * Varsayılanın kendisi bilinçli: projeyi klonlayan biri hemen çalıştırabilsin
 * diye. Tehlikeli olan varsayılan değil, ONUNLA CANLIYA ÇIKMAK.
 *
 * KURAL (ADR 0025): varsayılan anahtara yalnızca AÇIKÇA yerel bir çalışmada izin
 * verilir. Üç koşulun üçü de tutmalı:
 *  1. "dev" ya da "test" profili açık (yerel yığın dev'i, testler test'i açar);
 *  2. "prod" profili açık DEĞİL;
 *  3. APP_BASE_URL bu makineyi gösteriyor (http://localhost, 127.x, ::1).
 * Biri tutmazsa uygulama AÇILMAZ.
 *
 * Eskiden yalnızca "prod profili açıksa dur" deniyordu. Profil unutulursa
 * (ör. Railway'de yeni bir ortam açılırken) uygulama varsayılan anahtarla ve bir
 * WARN satırıyla açılırdı. Artık eksik ayar güvenli tarafta kalır: profil yoksa
 * da durur. Açılmayan bir uygulama fark edilir; sessiz bir uyarı edilmez.
 */
@Component
public class JwtSecretGuard {

    private static final Logger log = LoggerFactory.getLogger(JwtSecretGuard.class);

    /** application.yml'deki varsayılan — depoda açık olduğu için gerçek ortamda yasak. */
    static final String PUBLIC_DEV_SECRET =
            "ZGVmYXVsdC1kZXYtc2VjcmV0LWRvLW5vdC11c2UtaW4tcHJvZHVjdGlvbi1tdXN0LWJlLWF0LWxlYXN0LTY0LWNoYXJzLWxvbmc=";

    /** Varsayılan anahtara izin veren profiller: yerel geliştirme ve testler. */
    private static final Set<String> LOCAL_PROFILES = Set.of("dev", "test");

    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "::1", "[::1]");

    private final String secret;
    private final String baseUrl;
    private final Environment environment;

    public JwtSecretGuard(@Value("${app.jwt.secret}") String secret,
                          @Value("${app.base-url}") String baseUrl,
                          Environment environment) {
        this.secret = secret;
        this.baseUrl = baseUrl;
        this.environment = environment;
    }

    /**
     * @PostConstruct — ApplicationReadyEvent DEĞİL. İkincisinde bağlam çoktan
     * kurulmuş ve sunucu bağlantı noktaları açılmış olur; oradan hata fırlatmak
     * uygulamayı gerçekten durdurmaz. Burada fırlatılan hata bağlam kurulumunu
     * bozar, dolayısıyla hiçbir istek karşılanmaz.
     */
    @PostConstruct
    public void check() {
        if (!PUBLIC_DEV_SECRET.equals(secret)) {
            return;
        }

        List<String> profiles = Arrays.asList(environment.getActiveProfiles());
        String reason = whyNotLocal(profiles, baseUrl);
        if (reason != null) {
            throw new IllegalStateException("""

                    ┌─────────────────────────────────────────────────────────────┐
                    │  HERKESE AÇIK JWT ANAHTARIYLA AÇILMA REDDEDİLDİ             │
                    ├─────────────────────────────────────────────────────────────┤
                    │  Kullanılan anahtar application.yml'deki varsayılan; bu      │
                    │  değer depoda açıkça duruyor. Onunla gerçek bir ortamda      │
                    │  çalışmak, isteyen herkesin kendine geçerli token            │
                    │  üretebilmesi demek: şifresiz, bütün hesaplara erişim.       │
                    │                                                             │
                    │  Canlı ortamsa: kendi anahtarını üret, JWT_SECRET olarak ver │
                    │      openssl rand -base64 64                                │
                    │  Yerelde çalışıyorsan: dev profilini aç                     │
                    │      -Dspring-boot.run.profiles=dev  (ya da scripts/dev.ps1)│
                    │                                                             │
                    │  Uygulama bilerek başlatılmadı.                             │
                    └─────────────────────────────────────────────────────────────┘
                    Neden yerel sayılmadı: """ + reason);
        }

        log.warn("""

                ┌─────────────────────────────────────────────────────────────┐
                │  GELİŞTİRME JWT ANAHTARI KULLANILIYOR                       │
                ├─────────────────────────────────────────────────────────────┤
                │  Bu anahtar depoda açıkça duruyor; yerelde sorun değil ama   │
                │  canlıya çıkmadan önce JWT_SECRET verilmelidir. Profil dev   │
                │  ya da test değilse, prod ise ya da adres yerel değilse       │
                │  uygulama bu anahtarla açılmaz.                              │
                └─────────────────────────────────────────────────────────────┘""");
    }

    /**
     * Varsayılan anahtarla açılmak için çalışma neden yerel sayılmıyor?
     * Yerel sayılıyorsa null.
     */
    static String whyNotLocal(Collection<String> activeProfiles, String baseUrl) {
        if (activeProfiles.contains("prod")) {
            return "prod profili açık";
        }
        if (activeProfiles.stream().noneMatch(LOCAL_PROFILES::contains)) {
            return "dev ya da test profili açık değil (açık profiller: " + activeProfiles + ")";
        }
        if (!isLocalAddress(baseUrl)) {
            return "APP_BASE_URL bu makineyi göstermiyor: " + baseUrl;
        }
        return null;
    }

    /** http://localhost, http://127.0.0.1 ya da http://[::1]; https ya da başka bir makine değil. */
    static boolean isLocalAddress(String url) {
        try {
            URI uri = URI.create(url == null ? "" : url.trim());
            String host = uri.getHost();
            if (!"http".equalsIgnoreCase(uri.getScheme()) || host == null) {
                return false;
            }
            host = host.toLowerCase(Locale.ROOT);
            return LOOPBACK_HOSTS.contains(host) || host.matches("127(\\.\\d{1,3}){3}");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
