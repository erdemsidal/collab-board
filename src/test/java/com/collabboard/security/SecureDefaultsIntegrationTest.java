package com.collabboard.security;

import com.collabboard.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ortak ayarların güvenli varsayılanları (ADR 0025, güvenlik incelemesi O1).
 *
 * Testler "test" profiliyle çalışıyor; bu profilin kendine ait ayarı yok. Yani
 * burada görülen, profil unutulduğunda uygulamanın dışarıya ne açtığıdır:
 * yalnızca sağlık kontrolü. Metrikleri ve Swagger'ı yalnızca dev profili açar.
 */
class SecureDefaultsIntegrationTest extends IntegrationTestBase {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/actuator/metrics", "/actuator/info", "/actuator/caches",
            "/swagger-ui.html", "/swagger-ui/index.html", "/v3/api-docs"})
    @DisplayName("Profil verilmeden metrikler, önbellek ucu ve Swagger kapalı (oturum açmış kişiye de)")
    void icUclarKapali(String path) {
        // Oturumlu istek: 401 "kapalı" demek değildir, yalnızca "kimlik yok" demektir.
        // Uç yayında değilse kimlikle de 404 döner.
        String token = registerAndLogin("Varsayilan", "Ayar", uniqueEmail("varsayilan"));
        assertThat(rest.exchange(path, HttpMethod.GET, new HttpEntity<>(authHeaders(token)), String.class)
                .getStatusCode())
                .as(path)
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Güvenlik başlıkları her yanıtta: Referrer-Policy, Permissions-Policy, nosniff, X-Frame-Options")
    void guvenlikBasliklari() {
        HttpHeaders headers = rest.getForEntity("/", String.class).getHeaders();

        assertThat(headers.getFirst("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
        assertThat(headers.getFirst("Permissions-Policy"))
                .contains("camera=()", "microphone=()", "geolocation=()");
        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(headers.getFirst("X-Frame-Options")).isEqualTo("DENY");
        // CSP bilerek bu değişiklikte yok (ayrı iş: CDN dosyalarının yerele alınması).
        assertThat(headers.getFirst("Content-Security-Policy")).isNull();
    }

    @Test
    @DisplayName("HSTS yalnızca HTTPS'te: vekil X-Forwarded-Proto: https dediğinde yazılır")
    void hstsYalnizcaHttps() {
        // Düz HTTP: tarayıcı HSTS'yi zaten yok sayar, sunucu da yazmaz.
        assertThat(rest.getForEntity("/", String.class).getHeaders().getFirst("Strict-Transport-Security"))
                .isNull();

        // Railway'in vekili HTTPS'i sonlandırıp bu başlıkla iletir. Test istemcisi
        // 127.0.0.1'den bağlanıyor, yani güvenilen bir vekil gibi görünüyor.
        HttpHeaders proxied = new HttpHeaders();
        proxied.set("X-Forwarded-Proto", "https");
        String hsts = rest.exchange("/", HttpMethod.GET, new HttpEntity<>(proxied), String.class)
                .getHeaders().getFirst("Strict-Transport-Security");

        assertThat(hsts).contains("max-age=31536000").contains("includeSubDomains").doesNotContain("preload");
    }

    @Test
    @DisplayName("Sağlık kontrolü açık")
    void saglikAcik() {
        JsonNode health = rest.getForEntity("/actuator/health", JsonNode.class).getBody();
        assertThat(health.get("status").asText()).isEqualTo("UP");
    }
}
