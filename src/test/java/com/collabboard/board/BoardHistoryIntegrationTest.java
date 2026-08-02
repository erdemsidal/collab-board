package com.collabboard.board;

import com.collabboard.support.IntegrationTestBase;
import com.collabboard.support.StompTestClient;
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
 * Zaman yolculuğu: panonun geçmişteki hâlinin olay kaydından yeniden kurulması.
 */
class BoardHistoryIntegrationTest extends IntegrationTestBase {

    private JsonNode get(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(authHeaders(token)), JsonNode.class).getBody();
    }

    /** Bir kolondaki kart başlıklarını okur. */
    private java.util.List<String> titles(JsonNode board, int columnIndex) {
        java.util.List<String> out = new java.util.ArrayList<>();
        board.get("columns").get(columnIndex).get("cards").forEach(c -> out.add(c.get("title").asText()));
        return out;
    }

    @Test
    @DisplayName("Pano herhangi bir geçmiş ana geri sarılabilir; silinen kart o anda geri gelir")
    void panoGecmisAnaGeriSarilir() throws Exception {
        String token = registerAndLogin("Zaman", "Yolcusu", uniqueEmail("hist"));
        JsonNode board = createBoard(token, "Geçmiş Testi");
        long boardId = board.get("id").asLong();
        long todo = board.get("columns").get(0).get("id").asLong();
        long done = board.get("columns").get(2).get("id").asLong();
        String topic = "/topic/board." + boardId;
        String ops = "/app/board/" + boardId + "/ops";

        StompTestClient client = StompTestClient.connect(wsUrl(), token).subscribe(topic);
        StompTestClient.awaitSubscriptions();

        // Olaylar sırayla: iki kart ekle → birini taşı → birini sil
        client.send(ops, Map.of("type", "ADD_CARD", "columnId", todo, "title", "İlk kart"));
        long ilk = client.awaitMessage(topic).get("card").get("id").asLong();

        client.send(ops, Map.of("type", "ADD_CARD", "columnId", todo, "title", "İkinci kart"));
        client.awaitMessage(topic);

        client.send(ops, Map.of("type", "MOVE_CARD", "cardId", ilk,
                "toColumnId", done, "position", 0, "baseVersion", 0));
        client.awaitMessage(topic);

        client.send(ops, Map.of("type", "DELETE_CARD", "cardId", ilk));
        client.awaitMessage(topic);
        client.disconnect();

        // Zaman çizelgesi: dört olay, oluş sırasında
        JsonNode timeline = get("/api/boards/" + boardId + "/timeline", token);
        assertThat(timeline).hasSize(4);
        assertThat(timeline.get(0).get("type").asText()).isEqualTo("ADD_CARD");
        assertThat(timeline.get(3).get("type").asText()).isEqualTo("DELETE_CARD");

        long ilkEklemeAni = timeline.get(0).get("id").asLong();
        long tasimaAni = timeline.get(2).get("id").asLong();
        long silmeAni = timeline.get(3).get("id").asLong();

        // Başlangıç: pano bomboş
        JsonNode basta = get("/api/boards/" + boardId + "/history?upTo=0", token);
        assertThat(titles(basta, 0)).isEmpty();

        // İlk eklemeden sonra: tek kart, To Do'da
        JsonNode ilkAndan = get("/api/boards/" + boardId + "/history?upTo=" + ilkEklemeAni, token);
        assertThat(titles(ilkAndan, 0)).containsExactly("İlk kart");

        // Taşımadan sonra: kart Done kolonunda, To Do'da yalnızca ikinci kart
        JsonNode tasimaSonrasi = get("/api/boards/" + boardId + "/history?upTo=" + tasimaAni, token);
        assertThat(titles(tasimaSonrasi, 0)).containsExactly("İkinci kart");
        assertThat(titles(tasimaSonrasi, 2)).containsExactly("İlk kart");

        // Silmeden sonra: kart artık yok
        JsonNode silmeSonrasi = get("/api/boards/" + boardId + "/history?upTo=" + silmeAni, token);
        assertThat(titles(silmeSonrasi, 2)).isEmpty();

        // Ama GEÇMİŞTE hâlâ duruyor — özelliğin asıl değeri bu.
        assertThat(titles(tasimaSonrasi, 2)).containsExactly("İlk kart");

        // Son an, panonun bugünkü hâliyle aynı olmalı
        JsonNode guncel = get("/api/boards/" + boardId, token);
        assertThat(titles(silmeSonrasi, 0)).isEqualTo(titles(guncel, 0));
        assertThat(titles(silmeSonrasi, 2)).isEqualTo(titles(guncel, 2));
    }

    @Test
    @DisplayName("Başlık değişikliği geçmişte eski hâliyle görünür")
    void baslikDegisikligiGecmisteEskiHaliyleGorunur() throws Exception {
        String token = registerAndLogin("Zaman", "Yolcusu", uniqueEmail("hist"));
        JsonNode board = createBoard(token, "Düzenleme Geçmişi");
        long boardId = board.get("id").asLong();
        long todo = board.get("columns").get(0).get("id").asLong();
        String topic = "/topic/board." + boardId;
        String ops = "/app/board/" + boardId + "/ops";

        StompTestClient client = StompTestClient.connect(wsUrl(), token).subscribe(topic);
        StompTestClient.awaitSubscriptions();

        client.send(ops, Map.of("type", "ADD_CARD", "columnId", todo, "title", "Süt al"));
        long cardId = client.awaitMessage(topic).get("card").get("id").asLong();
        client.send(ops, Map.of("type", "EDIT_CARD", "cardId", cardId, "title", "2L süt al", "baseVersion", 0));
        client.awaitMessage(topic);
        client.disconnect();

        JsonNode timeline = get("/api/boards/" + boardId + "/timeline", token);
        long eklemeAni = timeline.get(0).get("id").asLong();

        assertThat(titles(get("/api/boards/" + boardId + "/history?upTo=" + eklemeAni, token), 0))
                .containsExactly("Süt al");
        assertThat(titles(get("/api/boards/" + boardId, token), 0))
                .containsExactly("2L süt al");
    }

    @Test
    @DisplayName("Üye olmayan geçmişi göremez")
    void uyeOlmayanGecmisiGoremez() {
        String owner = registerAndLogin("Sahip", "Kisi", uniqueEmail("hist"));
        String yabanci = registerAndLogin("Yabanci", "Kisi", uniqueEmail("hist"));
        long boardId = createBoard(owner, "Gizli Geçmiş").get("id").asLong();

        ResponseEntity<JsonNode> response = rest.exchange("/api/boards/" + boardId + "/timeline",
                HttpMethod.GET, new HttpEntity<>(authHeaders(yabanci)), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
