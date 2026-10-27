package com.collabboard.security;

import com.collabboard.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Yaygın şifre reddi: kayıt, şifre değişimi (ADR 0025, güvenlik incelemesi Y3).
 * Uzunluk kuralı (en az 8) aynen duruyor; karmaşıklık kuralı yok.
 */
class CommonPasswordIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("Kayıtta yaygın şifre Türkçe bir mesajla reddedilir")
    void kayittaYayginSifreReddedilir() {
        ResponseEntity<JsonNode> response = rest.postForEntity("/api/auth/register", Map.of(
                "firstName", "Yaygin", "lastName", "Sifre",
                "email", uniqueEmail("yaygin"), "password", "Password1"), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("fieldErrors").get(0).get("message").asText())
                .contains("Bu şifre çok yaygın");
    }

    @Test
    @DisplayName("Yaygın olmayan, 8 karakterlik şifre kabul edilir; karmaşıklık kuralı yok")
    void yayginOlmayanKabul() {
        ResponseEntity<JsonNode> response = rest.postForEntity("/api/auth/register", Map.of(
                "firstName", "Uzun", "lastName", "Cumle",
                "email", uniqueEmail("cumle"), "password", "mavikedi"), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("Şifre değişiminde yaygın şifre reddedilir, eski şifre geçerli kalır")
    void degisimdeYayginSifreReddedilir() {
        String email = uniqueEmail("degisim-yaygin");
        String token = registerAndLogin("Degisim", "Yaygin", email);

        ResponseEntity<JsonNode> response = rest.exchange("/api/users/me/password", HttpMethod.POST,
                new HttpEntity<>(Map.of("currentPassword", PASSWORD, "newPassword", "galatasaray"),
                        authHeaders(token)), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("fieldErrors").get(0).get("message").asText())
                .contains("Bu şifre çok yaygın");
        assertThat(loginToken(email)).isNotBlank();
    }
}
