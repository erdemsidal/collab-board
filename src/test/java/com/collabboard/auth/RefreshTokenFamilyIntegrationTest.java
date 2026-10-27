package com.collabboard.auth;

import com.collabboard.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Çıkış, atomik döndürme ve yeniden kullanımın yakalanması (ADR 0025,
 * güvenlik incelemesi O3).
 */
class RefreshTokenFamilyIntegrationTest extends IntegrationTestBase {

    @Autowired
    StringRedisTemplate redis;

    @Test
    @DisplayName("Çıkış oturumu sunucuda kapatır: aynı refresh token ile yenileme reddedilir")
    void cikisRefreshTokeniIptalEder() {
        String refresh = loginRefreshToken(account());

        // Erişim jetonu gerekmez; kimlik kanıtı refresh token'ın kendisi.
        ResponseEntity<JsonNode> logout = rest.postForEntity("/api/auth/logout",
                Map.of("refreshToken", refresh), JsonNode.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(refresh(refresh).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Kullanılmış refresh token geri gelince aile kapanır; kişinin diğer oturumu sürer")
    void yenidenKullanimAileyiKapatir() {
        String email = account();
        String first = loginRefreshToken(email);
        String otherDevice = loginRefreshToken(email);

        String second = refresh(first).getBody().get("refreshToken").asText();

        // Çalınmış kopya (first) geri geliyor: reddedilir...
        assertThat(refresh(first).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // ...ve aile kapandığı için meşru görünen son jeton da artık geçmez.
        assertThat(refresh(second).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(redis.hasKey("refresh_family:" + familyOf(first))).isFalse();

        // Başka cihazdaki oturum ayrı bir aile: etkilenmez.
        assertThat(refresh(otherDevice).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Döndürme atomik: aynı jetonla aynı anda gelen isteklerden yalnızca biri yeni jeton alır")
    void dondurmeAtomik() throws Exception {
        String refresh = loginRefreshToken(account());
        int parallel = 8;
        ExecutorService pool = Executors.newFixedThreadPool(parallel);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<HttpStatus>> results = new ArrayList<>();
            for (int i = 0; i < parallel; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return HttpStatus.valueOf(refresh(refresh).getStatusCode().value());
                }));
            }
            start.countDown();

            long succeeded = 0;
            for (Future<HttpStatus> result : results) {
                if (result.get(30, TimeUnit.SECONDS) == HttpStatus.OK) {
                    succeeded++;
                }
            }
            assertThat(succeeded).as("aynı jetonla başarılı yenileme sayısı").isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Redis'te jetonun kendisi değil özeti durur; eski biçimli jeton tanınmaz")
    void redisteYalnizcaOzet() {
        String refresh = loginRefreshToken(account());
        Object stored = redis.opsForHash().get("refresh_family:" + familyOf(refresh), "token");

        assertThat(stored).isNotNull();
        assertThat(stored.toString()).hasSize(64).isNotEqualTo(refresh)
                .doesNotContain(refresh.substring(refresh.indexOf('.') + 1));
        // Bu sürümden önceki jetonlar düz UUID'ydi; artık geçmez, bir kez yeniden giriş gerekir.
        assertThat(refresh(UUID.randomUUID().toString()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── yardımcılar ──────────────────────────────────────────────────

    private String account() {
        String email = uniqueEmail("aile");
        registerAndLogin("Oturum", "Ailesi", email);
        return email;
    }

    private String loginRefreshToken(String email) {
        return rest.postForEntity("/api/auth/login", Map.of("email", email, "password", PASSWORD), JsonNode.class)
                .getBody().get("refreshToken").asText();
    }

    private ResponseEntity<JsonNode> refresh(String refreshToken) {
        return rest.postForEntity("/api/auth/refresh", Map.of("refreshToken", refreshToken), JsonNode.class);
    }

    private static String familyOf(String refreshToken) {
        return refreshToken.substring(0, refreshToken.indexOf('.'));
    }
}
