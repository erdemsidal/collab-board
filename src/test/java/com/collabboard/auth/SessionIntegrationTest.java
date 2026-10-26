package com.collabboard.auth;

import com.collabboard.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Oturumun ömrü: süresi dolan erişim jetonu istemciye "yenile" der, oturum da
 * kullanıldıkça uzar.
 */
class SessionIntegrationTest extends IntegrationTestBase {

    @Value("${app.jwt.secret}")
    String jwtSecret;

    @Autowired
    StringRedisTemplate redis;

    @Test
    @DisplayName("Süresi dolmuş erişim jetonu 401 alır; refresh ile yenisi alınıp istek tekrarlanır")
    void suresiDolanJeton401VeYenilenir() {
        String email = uniqueEmail("oturum");
        registerAndLogin("Oturum", "Uzun", email);
        String refreshToken = login(email).get("refreshToken").asText();

        ResponseEntity<JsonNode> expired = rest.exchange("/api/boards", HttpMethod.GET,
                new HttpEntity<>(authHeaders(expiredAccessToken(email))), JsonNode.class);

        // 403 olsaydı istemci bunu "izin yok" diye okur, yenilemeden oturumu kapatırdı.
        assertThat(expired.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(expired.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");

        JsonNode renewed = rest.postForEntity("/api/auth/refresh",
                Map.of("refreshToken", refreshToken), JsonNode.class).getBody();
        ResponseEntity<JsonNode> retried = rest.exchange("/api/boards", HttpMethod.GET,
                new HttpEntity<>(authHeaders(renewed.get("accessToken").asText())), JsonNode.class);
        assertThat(retried.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Kimliği belli ama yetkisi olmayan kişi 403 almaya devam eder")
    void yetkisizlik403Kalir() {
        String owner = registerAndLogin("Pano", "Sahibi", uniqueEmail("oturum"));
        long boardId = createBoard(owner, "Özel").get("id").asLong();
        String stranger = registerAndLogin("Yabanci", "Kisi", uniqueEmail("oturum"));

        assertThat(getBoard(boardId, stranger).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Oturum her yenilemede baştan başlar: yeni refresh token tam süreyle yazılır")
    void oturumKullanildikcaUzar() {
        String email = uniqueEmail("oturum");
        registerAndLogin("Kayan", "Oturum", email);
        String first = login(email).get("refreshToken").asText();

        String second = rest.postForEntity("/api/auth/refresh",
                Map.of("refreshToken", first), JsonNode.class).getBody().get("refreshToken").asText();

        // Jeton "<aile>.<sır>"; oturum ailesi döndürmede aynı kalır, süresi baştan başlar (ADR 0025).
        String family = second.substring(0, second.indexOf('.'));
        assertThat(first).startsWith(family + ".");
        assertThat(second).isNotEqualTo(first);
        long secondsLeft = redis.getExpire("refresh_family:" + family, TimeUnit.SECONDS);
        assertThat(Duration.ofSeconds(secondsLeft)).isBetween(Duration.ofDays(30).minusMinutes(1), Duration.ofDays(30));
    }

    private JsonNode login(String email) {
        return rest.postForEntity("/api/auth/login",
                Map.of("email", email, "password", PASSWORD), JsonNode.class).getBody();
    }

    /** Sunucunun kendi anahtarıyla imzalanmış, 5 dakika önce süresi dolmuş gerçek bir jeton. */
    private String expiredAccessToken(String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .issuedAt(Date.from(now.minus(Duration.ofMinutes(20))))
                .expiration(Date.from(now.minus(Duration.ofMinutes(5))))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret)))
                .compact();
    }
}
