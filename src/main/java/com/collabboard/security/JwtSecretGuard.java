package com.collabboard.security;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Üretimde herkese açık geliştirme anahtarıyla çalışılmasını engeller.
 *
 * SORUN: application.yml'deki varsayılan JWT anahtarı depoda düz metin duruyor —
 * yani GitHub'ı açan herkes onu görebiliyor. O anahtarla canlıya çıkılırsa
 * isteyen kendine istediği kullanıcı için geçerli bir token üretir; şifreye
 * gerek kalmadan herkesin hesabına girilir.
 *
 * Varsayılanın kendisi bilinçli: projeyi klonlayan biri hemen çalıştırabilsin
 * diye. Tehlikeli olan varsayılan değil, ONUNLA CANLIYA ÇIKMAK.
 *
 * Bu yüzden koşula bağlı davranıyoruz:
 *  - dev: uyarı yeter, geliştirme akmaya devam etsin.
 *  - prod: AÇILIŞI DURDURUR. Sessiz bir uyarı, dağıtım loglarında kaybolur ve
 *    kimse fark etmeden aylarca açık kalır; açılmayan bir uygulama fark edilir.
 */
@Component
public class JwtSecretGuard {

    private static final Logger log = LoggerFactory.getLogger(JwtSecretGuard.class);

    /** application.yml'deki varsayılan — depoda açık olduğu için üretimde yasak. */
    private static final String PUBLIC_DEV_SECRET =
            "ZGVmYXVsdC1kZXYtc2VjcmV0LWRvLW5vdC11c2UtaW4tcHJvZHVjdGlvbi1tdXN0LWJlLWF0LWxlYXN0LTY0LWNoYXJzLWxvbmc=";

    private final String secret;
    private final Environment environment;

    public JwtSecretGuard(@Value("${app.jwt.secret}") String secret, Environment environment) {
        this.secret = secret;
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

        boolean production = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (production) {
            throw new IllegalStateException("""

                    ┌─────────────────────────────────────────────────────────────┐
                    │  ÜRETİMDE HERKESE AÇIK JWT ANAHTARI                         │
                    ├─────────────────────────────────────────────────────────────┤
                    │  Kullanılan anahtar application.yml'deki varsayılan; bu      │
                    │  değer depoda açıkça duruyor. Onunla canlıya çıkmak,         │
                    │  isteyen herkesin kendine geçerli token üretebilmesi         │
                    │  demek — şifreye gerek kalmadan tüm hesaplara erişim.        │
                    │                                                             │
                    │  Kendi anahtarını üret ve JWT_SECRET olarak ver:            │
                    │      openssl rand -base64 64                                │
                    │                                                             │
                    │  Uygulama bilerek başlatılmadı.                             │
                    └─────────────────────────────────────────────────────────────┘""");
        }

        log.warn("""

                ┌─────────────────────────────────────────────────────────────┐
                │  GELİŞTİRME JWT ANAHTARI KULLANILIYOR                       │
                ├─────────────────────────────────────────────────────────────┤
                │  Bu anahtar depoda açıkça duruyor; yerelde sorun değil ama   │
                │  canlıya çıkmadan önce JWT_SECRET verilmelidir. prod         │
                │  profilinde uygulama bu anahtarla açılmaz.                   │
                └─────────────────────────────────────────────────────────────┘""");
    }
}
