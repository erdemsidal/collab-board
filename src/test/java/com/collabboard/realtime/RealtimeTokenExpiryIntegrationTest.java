package com.collabboard.realtime;

import com.collabboard.support.IntegrationTestBase;
import com.collabboard.support.StompTestClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Jetonun süresi dolan canlı bağlantı kapanır; jetonunu bildiren kopmaz (ADR 0025).
 *
 * Bu bağlamda erişim jetonu 4 saniye yaşıyor, pay yok ve bağlantılar yarım
 * saniyede bir yoklanıyor; gerçekte 15 dakika, 60 saniye pay ve 30 saniye.
 */
@TestPropertySource(properties = {
        "app.jwt.access-token-expiration=4s",
        "app.realtime.token-expiry-grace=0s",
        "app.realtime.session-check-ms=500"
})
class RealtimeTokenExpiryIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("Jetonu yenilenmeyen bağlantı süresi dolunca 'token-expired' ile kapanır")
    void suresiDolanBaglantiKapanir() throws Exception {
        String token = registerAndLogin("Sure", "Dolan", uniqueEmail("sure"));
        StompTestClient client = StompTestClient.connect(wsUrl(), token).subscribe("/user/queue/notifications");

        assertThat(client.awaitError().getFirst("x-reason")).isEqualTo("token-expired");
        assertThat(client.awaitClosed(5)).isTrue();
    }

    @Test
    @DisplayName("Yeni jetonunu bildiren bağlantı süresi geçse de açık kalır; bildirmeyi bırakınca kapanır")
    void jetonunuYenileyenBaglantiSurer() throws Exception {
        String email = uniqueEmail("yenileyen");
        StompTestClient client = StompTestClient.connect(wsUrl(), registerAndLogin("Jeton", "Yenileyen", email))
                .subscribe("/user/queue/notifications");

        // Arayüzün yaptığı: jeton yenilendikçe açık bağlantıya bildir. 8 saniye, ilk jetonun iki katı.
        for (int i = 0; i < 4; i++) {
            Thread.sleep(2_000);
            client.send("/app/session/renew", Map.of("token", loginToken(email)));
        }
        assertThat(client.pollError(0)).as("yenilenen bağlantı kapanmamalı").isNull();
        assertThat(client.isConnected()).isTrue();

        // Bildirmeyi bırakınca son jetonun süresiyle kapanır.
        assertThat(client.awaitError().getFirst("x-reason")).isEqualTo("token-expired");
    }

    @Test
    @DisplayName("Başkasının jetonuyla yenileme kabul edilmez")
    void baskasininJetonuKabulEdilmez() throws Exception {
        String token = registerAndLogin("Kendi", "Jetonu", uniqueEmail("kendi"));
        String otherEmail = uniqueEmail("baskasi");
        registerAndLogin("Baska", "Biri", otherEmail);
        StompTestClient client = StompTestClient.connect(wsUrl(), token).subscribe("/user/queue/notifications");

        Thread.sleep(2_000);
        client.send("/app/session/renew", Map.of("token", loginToken(otherEmail)));

        assertThat(client.awaitError().getFirst("x-reason")).isEqualTo("token-expired");
    }
}
