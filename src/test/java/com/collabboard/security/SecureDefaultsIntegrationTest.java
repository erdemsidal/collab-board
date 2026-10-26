package com.collabboard.security;

import com.collabboard.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
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
    @DisplayName("Sağlık kontrolü açık")
    void saglikAcik() {
        JsonNode health = rest.getForEntity("/actuator/health", JsonNode.class).getBody();
        assertThat(health.get("status").asText()).isEqualTo("UP");
    }
}
