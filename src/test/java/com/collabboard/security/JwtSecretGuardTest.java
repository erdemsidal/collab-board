package com.collabboard.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Açılış koruması: depodaki herkese açık JWT anahtarıyla yalnızca açıkça yerel
 * bir çalışmada açılınır (ADR 0025, güvenlik incelemesi O1).
 *
 * Bağlamın tamamı değil, yalnızca koruma kurulur (ApplicationContextRunner):
 * veritabanı gerekmez, her durum milisaniyeler sürer. Koruma @PostConstruct'ta
 * hata fırlatınca bağlam kurulamaz; canlıda da uygulama tam olarak böyle durur.
 */
class JwtSecretGuardTest {

    private static final String DEV_SECRET = JwtSecretGuard.PUBLIC_DEV_SECRET;
    private static final String OWN_SECRET =
            "dGVzdC1vbmx5LXNlY3JldC1mb3ItdGhlLWd1YXJkLXVuaXQtdGVzdC14eXotMDEyMzQ1Njc4OS1hYmNkZWZnaGlqa2xtbm9w";

    @ParameterizedTest(name = "profil=[{0}] adres={1} anahtar={2} → açılır mı: {3}")
    @CsvSource(delimiter = '|', value = {
            // Varsayılan anahtar: yalnızca dev/test + prod yok + yerel adres.
            "dev         | http://localhost:8080                           | varsayilan | true",
            "test        | http://localhost:8080                           | varsayilan | true",
            "dev,mailpit | http://127.0.0.1:8094                           | varsayilan | true",
            "dev         | http://[::1]:8080                               | varsayilan | true",
            "''          | http://localhost:8080                           | varsayilan | false",
            "mailpit     | http://localhost:8080                           | varsayilan | false",
            "prod        | http://localhost:8080                           | varsayilan | false",
            "prod        | https://collab-board-production.up.railway.app  | varsayilan | false",
            "dev,prod    | http://localhost:8080                           | varsayilan | false",
            "dev         | https://collab-board-production.up.railway.app  | varsayilan | false",
            "dev         | http://192.168.1.20:8080                        | varsayilan | false",
            "dev         | http://localhost.evil.example                   | varsayilan | false",
            // Kendi anahtarıyla her durumda açılır.
            "''          | https://collab-board-production.up.railway.app  | kendi      | true",
            "prod        | https://collab-board-production.up.railway.app  | kendi      | true",
            "dev         | http://localhost:8080                           | kendi      | true",
    })
    @DisplayName("Varsayılan anahtar yalnızca açıkça yerel çalışmada kabul edilir")
    void profilAdresVeAnahtarBirlesimleri(String profiles, String baseUrl, String secret, boolean starts) {
        String[] active = profiles.isBlank() ? new String[0] : profiles.split(",");

        new ApplicationContextRunner()
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles(active))
                .withPropertyValues(
                        "app.jwt.secret=" + ("kendi".equals(secret) ? OWN_SECRET : DEV_SECRET),
                        "app.base-url=" + baseUrl)
                .withBean(JwtSecretGuard.class)
                .run(context -> {
                    if (starts) {
                        assertThat(context).hasNotFailed();
                    } else {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .rootCause()
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("HERKESE AÇIK JWT ANAHTARIYLA AÇILMA REDDEDİLDİ");
                    }
                });
    }
}
