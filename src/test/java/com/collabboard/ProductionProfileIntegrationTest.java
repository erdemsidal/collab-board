package com.collabboard;

import com.collabboard.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uygulama canlıdaki ayarlarla (prod profili) açıldığında dışarıdan ne görünüyor?
 *
 * Bu sınıf ayrı bir uygulama bağlamı kurar; diğer testler varsayılan profille
 * çalışır ve bunları göremezdi.
 */
@ActiveProfiles("prod")
// prod profili depodaki herkese açık JWT anahtarıyla açılmayı reddeder (JwtSecretGuard).
@TestPropertySource(properties =
        "app.jwt.secret=dGVzdC1vbmx5LXNlY3JldC1mb3ItdGhlLXByb2R1Y3Rpb24tcHJvZmlsZS1pbnRlZ3JhdGlvbi10ZXN0LXh5eiE=")
class ProductionProfileIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("Canlıda iç metrikler dışarıya kapalı, sağlık kontrolü açık")
    void metriklerKapaliSaglikAcik() {
        assertThat(rest.getForEntity("/actuator/metrics", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<JsonNode> health = rest.getForEntity("/actuator/health", JsonNode.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody().get("status").asText()).isEqualTo("UP");
    }

    @Test
    @DisplayName("Olmayan adres 500 değil 404 döner: tarayıcının istediği favicon.ico, canlıda kapalı Swagger")
    void olmayanAdres404() {
        assertThat(rest.getForEntity("/favicon.ico", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.getForEntity("/swagger-ui/index.html", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
