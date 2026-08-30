package com.collabboard.board;

import com.collabboard.support.IntegrationTestBase;
import com.collabboard.support.StompTestClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kart detayları: açıklama, atanan kişi, son tarih.
 *
 * EDIT_CARD alanların TAMAMINI taşır (bkz. EditCardOp): gönderilmeyen alan
 * temizlenir. Bunu güvenli kılan baseVersion'dır — istemci gördüğü hâlin üzerine
 * yazar, kör bir ezme yapmaz. Testler hem yazmayı hem temizlemeyi doğrular.
 */
class CardDetailsIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("Kart detayları kaydedilir ve panodaki herkese yayınlanır")
    void detaylarKaydedilirVeYayinlanir() throws Exception {
        String email = uniqueEmail("detay");
        String token = registerAndLogin("Detay", "Sahibi", email);
        JsonNode board = createBoard(token, "Detay Testi");
        long boardId = board.get("id").asLong();
        long todo = board.get("columns").get(0).get("id").asLong();
        String topic = "/topic/board." + boardId;
        String ops = "/app/board/" + boardId + "/ops";

        long myId = rest.exchange("/api/users/me", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), JsonNode.class).getBody().get("id").asLong();

        StompTestClient client = StompTestClient.connect(wsUrl(), token)
                .subscribe(topic).subscribe("/user/queue/errors");
        StompTestClient.awaitSubscriptions();

        client.send(ops, Map.of("type", "ADD_CARD", "columnId", todo, "title", "Detaylanacak"));
        JsonNode added = client.awaitMessage(topic).get("card");
        long cardId = added.get("id").asLong();
        assertThat(added.get("description").isNull()).isTrue();

        client.send(ops, Map.of(
                "type", "EDIT_CARD",
                "cardId", cardId,
                "title", "Detaylanacak",
                "description", "Önce şemayı çiz, sonra uçları yaz.",
                "assigneeId", myId,
                "dueDate", "2026-12-31",
                "baseVersion", 0));

        JsonNode edited = client.awaitMessage(topic);
        assertThat(edited.get("type").asText()).isEqualTo("EDIT_CARD");
        assertThat(edited.get("description").asText()).isEqualTo("Önce şemayı çiz, sonra uçları yaz.");
        assertThat(edited.get("assigneeId").asLong()).isEqualTo(myId);
        assertThat(edited.get("dueDate").asText()).isEqualTo("2026-12-31");
        client.disconnect();

        JsonNode fresh = rest.exchange("/api/boards/" + boardId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), JsonNode.class).getBody();
        JsonNode card = fresh.get("columns").get(0).get("cards").get(0);
        assertThat(card.get("description").asText()).isEqualTo("Önce şemayı çiz, sonra uçları yaz.");
        assertThat(card.get("assigneeId").asLong()).isEqualTo(myId);
        assertThat(card.get("dueDate").asText()).isEqualTo("2026-12-31");
    }

    @Test
    @DisplayName("Gönderilmeyen alanlar temizlenir — detay boşaltılabilir")
    void gonderilmeyenAlanlarTemizlenir() throws Exception {
        String token = registerAndLogin("Temiz", "Leyen", uniqueEmail("detay"));
        JsonNode board = createBoard(token, "Temizleme Testi");
        long boardId = board.get("id").asLong();
        long todo = board.get("columns").get(0).get("id").asLong();
        String topic = "/topic/board." + boardId;
        String ops = "/app/board/" + boardId + "/ops";

        StompTestClient client = StompTestClient.connect(wsUrl(), token).subscribe(topic);
        StompTestClient.awaitSubscriptions();

        client.send(ops, Map.of("type", "ADD_CARD", "columnId", todo, "title", "Kart"));
        long cardId = client.awaitMessage(topic).get("card").get("id").asLong();

        client.send(ops, Map.of("type", "EDIT_CARD", "cardId", cardId, "title", "Kart",
                "description", "Silinecek not", "dueDate", "2026-01-15", "baseVersion", 0));
        assertThat(client.awaitMessage(topic).get("description").asText()).isEqualTo("Silinecek not");

        // Detaysız bir düzenleme: alanlar gönderilmiyor → temizlenmeli.
        Map<String, Object> bosalt = new HashMap<>();
        bosalt.put("type", "EDIT_CARD");
        bosalt.put("cardId", cardId);
        bosalt.put("title", "Kart");
        bosalt.put("description", null);
        bosalt.put("dueDate", null);
        bosalt.put("baseVersion", 1);
        client.send(ops, bosalt);

        JsonNode cleared = client.awaitMessage(topic);
        assertThat(cleared.get("description").isNull()).isTrue();
        assertThat(cleared.get("dueDate").isNull()).isTrue();
        client.disconnect();
    }

    @Test
    @DisplayName("Geçmişe bakıldığında kartın o andaki detayları da geri gelir")
    void gecmisteDetaylarYenidenKurulur() throws Exception {
        String token = registerAndLogin("Gecmis", "Detay", uniqueEmail("detay"));
        JsonNode board = createBoard(token, "Geçmiş Detay");
        long boardId = board.get("id").asLong();
        long todo = board.get("columns").get(0).get("id").asLong();
        String topic = "/topic/board." + boardId;
        String ops = "/app/board/" + boardId + "/ops";

        StompTestClient client = StompTestClient.connect(wsUrl(), token).subscribe(topic);
        StompTestClient.awaitSubscriptions();

        client.send(ops, Map.of("type", "ADD_CARD", "columnId", todo, "title", "Zamanda kart"));
        long cardId = client.awaitMessage(topic).get("card").get("id").asLong();
        client.send(ops, Map.of("type", "EDIT_CARD", "cardId", cardId, "title", "Zamanda kart",
                "description", "İlk hâli", "dueDate", "2026-03-01", "baseVersion", 0));
        client.awaitMessage(topic);
        client.disconnect();

        JsonNode timeline = rest.exchange("/api/boards/" + boardId + "/timeline", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), JsonNode.class).getBody();
        long sonAn = timeline.get(timeline.size() - 1).get("id").asLong();

        JsonNode past = rest.exchange("/api/boards/" + boardId + "/history?upTo=" + sonAn,
                HttpMethod.GET, new HttpEntity<>(authHeaders(token)), JsonNode.class).getBody();

        JsonNode card = past.get("columns").get(0).get("cards").get(0);
        assertThat(card.get("description").asText()).isEqualTo("İlk hâli");
        assertThat(card.get("dueDate").asText()).isEqualTo("2026-03-01");
    }
}
