package com.collabboard.board;

import com.collabboard.support.IntegrationTestBase;
import com.collabboard.support.StompTestClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Projenin kalbi: gerçek zamanlı senkron, çakışma çözümü ve presence.
 *
 * Bunlar "gerçek" testler: tarayıcı yerine Java STOMP istemcisi kullanıyoruz ama
 * akış birebir aynı — kimlikli CONNECT, abonelik, operasyon gönderimi, yayın.
 */
class RealtimeSyncIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("Bir kullanıcının eklediği kart, aynı panodaki HERKESE ulaşır")
    void kartEklemeAyniPanodakiHerkeseUlasir() throws Exception {
        String tokenA = registerAndLogin("Ayse", "Yilmaz", uniqueEmail("rt"));
        String tokenB = registerAndLogin("Bora", "Kaya", uniqueEmail("rt"));

        JsonNode board = createBoard(tokenA, "Senkron Testi");
        long boardId = board.get("id").asLong();
        long todoId = board.get("columns").get(0).get("id").asLong();
        String topic = "/topic/board." + boardId;

        StompTestClient ayse = StompTestClient.connect(wsUrl(), tokenA).subscribe(topic);
        StompTestClient bora = StompTestClient.connect(wsUrl(), tokenB).subscribe(topic);
        StompTestClient.awaitSubscriptions();

        ayse.send("/app/board/" + boardId + "/ops",
                Map.of("type", "ADD_CARD", "columnId", todoId, "title", "Süt al"));

        // Gönderen de dahil, o panoyu dinleyen herkes olayı alır.
        JsonNode ayseEvent = ayse.awaitMessage(topic);
        JsonNode boraEvent = bora.awaitMessage(topic);

        assertThat(ayseEvent.get("type").asText()).isEqualTo("ADD_CARD");
        assertThat(boraEvent.get("type").asText()).isEqualTo("ADD_CARD");
        assertThat(boraEvent.get("card").get("title").asText()).isEqualTo("Süt al");
        assertThat(boraEvent.get("card").get("version").asLong()).isZero();

        ayse.disconnect();
        bora.disconnect();
    }

    @Test
    @DisplayName("Kart taşınınca sürüm artar ve yeni konum yayınlanır")
    void kartTasinincaSurumArtar() throws Exception {
        String token = registerAndLogin("Cem", "Demir", uniqueEmail("rt"));
        JsonNode board = createBoard(token, "Taşıma Testi");
        long boardId = board.get("id").asLong();
        long todoId = board.get("columns").get(0).get("id").asLong();
        long doneId = board.get("columns").get(2).get("id").asLong();
        String topic = "/topic/board." + boardId;

        StompTestClient client = StompTestClient.connect(wsUrl(), token).subscribe(topic);
        StompTestClient.awaitSubscriptions();

        client.send("/app/board/" + boardId + "/ops",
                Map.of("type", "ADD_CARD", "columnId", todoId, "title", "Taşınacak"));
        long cardId = client.awaitMessage(topic).get("card").get("id").asLong();

        client.send("/app/board/" + boardId + "/ops", Map.of(
                "type", "MOVE_CARD", "cardId", cardId,
                "toColumnId", doneId, "position", 0, "baseVersion", 0));

        JsonNode moved = client.awaitMessage(topic);
        assertThat(moved.get("type").asText()).isEqualTo("MOVE_CARD");
        assertThat(moved.get("toColumnId").asLong()).isEqualTo(doneId);
        // saveAndFlush sayesinde yayınlanan olay GÜNCEL sürümü taşır (0 → 1).
        assertThat(moved.get("version").asLong()).isEqualTo(1);

        client.disconnect();
    }

    @Test
    @DisplayName("Bayat sürümle gelen operasyon reddedilir ve ilk değişiklik korunur")
    void bayatSurumluOperasyonReddedilir() throws Exception {
        String tokenA = registerAndLogin("Ayse", "Yilmaz", uniqueEmail("rt"));
        String tokenB = registerAndLogin("Bora", "Kaya", uniqueEmail("rt"));

        JsonNode board = createBoard(tokenA, "Çakışma Testi");
        long boardId = board.get("id").asLong();
        long todoId = board.get("columns").get(0).get("id").asLong();
        String topic = "/topic/board." + boardId;
        String ops = "/app/board/" + boardId + "/ops";

        StompTestClient ayse = StompTestClient.connect(wsUrl(), tokenA)
                .subscribe(topic).subscribe("/user/queue/errors");
        StompTestClient bora = StompTestClient.connect(wsUrl(), tokenB)
                .subscribe(topic).subscribe("/user/queue/errors");
        StompTestClient.awaitSubscriptions();

        // Kart oluşur; ikisi de sürüm 0 görüyor.
        ayse.send(ops, Map.of("type", "ADD_CARD", "columnId", todoId, "title", "Süt al"));
        long cardId = ayse.awaitMessage(topic).get("card").get("id").asLong();
        bora.awaitMessage(topic);

        // Ayşe önce düzenler → kabul, sürüm 0 → 1.
        ayse.send(ops, Map.of("type", "EDIT_CARD", "cardId", cardId,
                "title", "2L Süt", "baseVersion", 0));
        assertThat(ayse.awaitMessage(topic).get("title").asText()).isEqualTo("2L Süt");
        bora.awaitMessage(topic);

        // Bora HÂLÂ sürüm 0 zannederek düzenlemeye çalışır → REDDEDİLMELİ.
        bora.send(ops, Map.of("type", "EDIT_CARD", "cardId", cardId,
                "title", "Badem sütü", "baseVersion", 0));

        JsonNode rejection = bora.awaitMessage("/user/queue/errors");
        assertThat(rejection.get("type").asText()).isEqualTo("OP_REJECTED");
        assertThat(rejection.get("reason").asText()).isEqualTo("STALE_VERSION");

        // Reddetme SADECE gönderene gider: Ayşe bu gürültüyü görmemeli.
        assertThat(ayse.poll("/user/queue/errors", 1)).isNull();
        // Ve panoya yeni bir olay yayınlanmamalı (operasyon uygulanmadı).
        assertThat(bora.poll(topic, 1)).isNull();

        // Ayşe'nin değişikliği korundu mu? Sunucudaki gerçeğe bakalım.
        JsonNode current = rest.exchange("/api/boards/" + boardId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(tokenA)), JsonNode.class).getBody();
        String title = current.get("columns").get(0).get("cards").get(0).get("title").asText();
        assertThat(title).isEqualTo("2L Süt");

        ayse.disconnect();
        bora.disconnect();
    }

    @Test
    @DisplayName("Panoya katılan kullanıcılar presence listesinde gerçek isimleriyle görünür")
    void presenceGercekKullanicilariGosterir() throws Exception {
        String tokenA = registerAndLogin("Erdem", "Sidal", uniqueEmail("pr"));
        String tokenB = registerAndLogin("Ayse", "Yilmaz", uniqueEmail("pr"));

        long boardId = createBoard(tokenA, "Presence Testi").get("id").asLong();
        String presenceTopic = "/topic/board." + boardId + "/presence";

        StompTestClient erdem = StompTestClient.connect(wsUrl(), tokenA).subscribe(presenceTopic);
        StompTestClient ayse = StompTestClient.connect(wsUrl(), tokenB).subscribe(presenceTopic);
        StompTestClient.awaitSubscriptions();

        erdem.send("/app/board/" + boardId + "/presence/join", Map.of());
        assertThat(erdem.awaitMessage(presenceTopic).get("users")).hasSize(1);
        ayse.awaitMessage(presenceTopic);

        ayse.send("/app/board/" + boardId + "/presence/join", Map.of());
        JsonNode state = ayse.awaitMessage(presenceTopic);

        assertThat(state.get("users")).hasSize(2);
        assertThat(state.get("users").toString()).contains("Erdem Sidal").contains("Ayse Yilmaz");

        erdem.disconnect();
        ayse.disconnect();
    }

    @Test
    @DisplayName("Geçersiz token ile WebSocket bağlantısı kurulamaz")
    void tokensizBaglantiReddedilir() {
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> StompTestClient.connect(wsUrl(), "gecersiz-token"));
    }
}
