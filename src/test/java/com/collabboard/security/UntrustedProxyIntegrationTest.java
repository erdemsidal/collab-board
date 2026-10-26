package com.collabboard.security;

import com.collabboard.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Güvenilmeyen bir adresten gelen X-Forwarded-For hiç dikkate alınmaz (ADR 0025).
 *
 * Bu bağlamda Tomcat'e güvenilen vekil olarak yerel adres DEĞİL, hiç
 * kullanılmayan bir aralık verildi. Test istemcisi (127.0.0.1) böylece doğrudan
 * bağlanan bir yabancı gibi görünür: başlığa ne yazarsa yazsın IP'si değişmez.
 * Desen köşeli parantezle yazıldı: bu satırlar .properties gibi okunur, ters
 * bölü kaybolurdu.
 */
@TestPropertySource(properties = {
        "app.rate-limit.enabled=true",
        "server.tomcat.remoteip.internal-proxies=10[.]255[.]255[.][0-9]+"
})
class UntrustedProxyIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("Doğrudan bağlanan istemcinin uydurduğu X-Forwarded-For sınırı sıfırlamaz")
    void dogrudanIstemcininBasligiYokSayilir() {
        String email = uniqueEmail("guvensiz-vekil");
        for (int i = 1; i <= 10; i++) {
            assertThat(login("203.0.113." + i, email).getStatusCode())
                    .as("%d. deneme", i).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        ResponseEntity<JsonNode> blocked = login("203.0.113.77", email);

        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    private ResponseEntity<JsonNode> login(String xff, String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For", xff);
        return rest.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(Map.of("email", email, "password", "yanlis-sifre"), headers), JsonNode.class);
    }
}
