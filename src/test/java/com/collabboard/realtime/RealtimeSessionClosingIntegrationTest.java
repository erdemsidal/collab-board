package com.collabboard.realtime;

import com.collabboard.support.IntegrationTestBase;
import com.collabboard.support.StompTestClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.stomp.StompHeaders;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Açık canlı bağlantılar, erişim kalkınca kapanır (ADR 0025, güvenlik incelemesi O4).
 */
class RealtimeSessionClosingIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("Panodan çıkarılanın o panoya abone bağlantısı kapanır; kişisel kanalı açık kalır")
    void panodanCikarilaninBaglantisiKapanir() throws Exception {
        String ownerToken = registerAndLogin("Erdem", "Sidal", uniqueEmail("cikar-sahip"));
        JsonNode board = createBoard(ownerToken, "Çıkarma panosu");
        long boardId = board.get("id").asLong();
        long todo = board.get("columns").get(0).get("id").asLong();
        String topic = "/topic/board." + boardId;
        String memberEmail = uniqueEmail("cikar-uye");
        registerAndLogin("Ayrilan", "Calisan", memberEmail);
        addMember(boardId, ownerToken, memberEmail, "EDITOR");
        String memberToken = loginToken(memberEmail);

        StompTestClient boardSession = StompTestClient.connect(wsUrl(), memberToken)
                .subscribe(topic).subscribe(topic + "/presence");
        StompTestClient inbox = StompTestClient.connect(wsUrl(), memberToken).subscribe("/user/queue/notifications");
        StompTestClient owner = StompTestClient.connect(wsUrl(), ownerToken).subscribe(topic);
        StompTestClient.awaitSubscriptions();

        // Önce olayları alıyor.
        owner.send("/app/board/" + boardId + "/ops", Map.of("type", "ADD_CARD", "columnId", todo, "title", "Önce"));
        assertThat(boardSession.awaitMessage(topic).get("card").get("title").asText()).isEqualTo("Önce");

        assertThat(removeMember(boardId, ownerToken, memberEmail).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        StompHeaders error = boardSession.awaitError();
        assertThat(error.getFirst("x-reason")).isEqualTo("board-access");
        assertThat(boardSession.awaitClosed(5)).isTrue();

        // Çıkarıldıktan sonraki olay ona ulaşmaz. (Çıkarılmanın kendisini duyuran
        // MEMBERS_CHANGED kapanmadan önce gelebilir; arayüz panodan onunla da çıkar.)
        owner.send("/app/board/" + boardId + "/ops", Map.of("type", "ADD_CARD", "columnId", todo, "title", "Sonra"));
        for (JsonNode seen = owner.awaitMessage(topic); !"ADD_CARD".equals(seen.get("type").asText());
             seen = owner.awaitMessage(topic)) {
            // sahibin kuyruğundaki MEMBERS_CHANGED'i geç
        }
        for (JsonNode late = boardSession.poll(topic, 1); late != null; late = boardSession.poll(topic, 1)) {
            assertThat(late.get("type").asText()).as("çıkarıldıktan sonra gelen olay").isNotEqualTo("ADD_CARD");
        }

        // Panoya abone olmayan kişisel kanalı etkilenmez.
        assertThat(inbox.pollError(1)).isNull();
        assertThat(inbox.isConnected()).isTrue();

        // Yeniden bağlanıp abone olmaya çalışırsa reddedilir.
        StompTestClient retry = StompTestClient.connect(wsUrl(), memberToken).subscribe(topic);
        assertThat(retry.awaitError().getFirst("x-reason")).isEqualTo("board-access");

        inbox.disconnect();
        owner.disconnect();
    }

    @Test
    @DisplayName("Şifre değişince kişinin bütün bağlantıları kapanır; eski jetonla geri gelinemez, yenisiyle gelinir")
    void sifreDegisinceBaglantilarKapanir() throws Exception {
        String email = uniqueEmail("sifre-ws");
        String oldToken = registerAndLogin("Sifre", "Degisen", email);
        String otherToken = registerAndLogin("Baska", "Kisi", uniqueEmail("sifre-ws-baska"));

        StompTestClient laptop = StompTestClient.connect(wsUrl(), oldToken).subscribe("/user/queue/notifications");
        StompTestClient phone = StompTestClient.connect(wsUrl(), oldToken).subscribe("/user/queue/errors");
        StompTestClient someoneElse = StompTestClient.connect(wsUrl(), otherToken).subscribe("/user/queue/notifications");
        StompTestClient.awaitSubscriptions();

        ResponseEntity<JsonNode> changed = rest.exchange("/api/users/me/password", HttpMethod.POST,
                new HttpEntity<>(Map.of("currentPassword", PASSWORD, "newPassword", "yeni-ve-uzun-bir-sifre"),
                        authHeaders(oldToken)), JsonNode.class);
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);

        for (StompTestClient device : new StompTestClient[]{laptop, phone}) {
            assertThat(device.awaitError().getFirst("x-reason")).isEqualTo("session-revoked");
            assertThat(device.awaitClosed(5)).isTrue();
        }
        // Başka bir kişinin bağlantısı etkilenmez.
        assertThat(someoneElse.pollError(1)).isNull();

        // Elde kalan eski erişim jetonu (henüz süresi dolmamış) canlı bağlantı açamaz...
        assertThatThrownBy(() -> StompTestClient.connect(wsUrl(), oldToken)).isInstanceOf(Exception.class);
        // ...şifreyi değiştiren cihaza verilen yeni jeton açar.
        String newToken = changed.getBody().get("accessToken").asText();
        StompTestClient reconnected = StompTestClient.connect(wsUrl(), newToken).subscribe("/user/queue/notifications");
        assertThat(reconnected.pollError(1)).isNull();
        assertThat(reconnected.isConnected()).isTrue();

        reconnected.disconnect();
        someoneElse.disconnect();
    }

    private ResponseEntity<Void> removeMember(long boardId, String ownerToken, String memberEmail) {
        JsonNode members = rest.exchange("/api/boards/" + boardId + "/members", HttpMethod.GET,
                new HttpEntity<>(authHeaders(ownerToken)), JsonNode.class).getBody();
        long userId = 0;
        for (JsonNode m : members) {
            if (memberEmail.equals(m.get("email").asText())) {
                userId = m.get("id").asLong();
            }
        }
        return rest.exchange("/api/boards/" + boardId + "/members/" + userId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(ownerToken)), Void.class);
    }
}
