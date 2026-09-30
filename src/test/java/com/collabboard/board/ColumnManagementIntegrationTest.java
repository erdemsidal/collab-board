package com.collabboard.board;

import com.collabboard.support.IntegrationTestBase;
import com.collabboard.support.StompTestClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kolon yaşam döngüsü: ekleme, yeniden adlandırma, silme.
 *
 * Kolonlar artık sabit değil; bu, geçmişin yeniden kurulmasını da etkiliyor.
 * "Bugünkü kolonlar" başlangıç hâli sayılamaz: sonradan eklenen kolon geçmişte
 * yoktu, silinen kolon ise geçmişte vardı. Son test bunu doğrular.
 */
class ColumnManagementIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("Kolon eklenir, sona yerleşir ve panoda görünür")
    void kolonEklenir() throws Exception {
        String token = registerAndLogin("Kolon", "Ekleyen", uniqueEmail("col"));
        JsonNode board = createBoard(token, "Kolon Testi");
        long boardId = board.get("id").asLong();
        String topic = "/topic/board." + boardId;
        String ops = "/app/board/" + boardId + "/ops";

        StompTestClient client = StompTestClient.connect(wsUrl(), token)
                .subscribe(topic).subscribe("/user/queue/errors");
        StompTestClient.awaitSubscriptions();

        client.send(ops, Map.of("type", "ADD_COLUMN", "boardId", boardId, "name", "İncelemede"));

        JsonNode event = client.awaitMessage(topic);
        assertThat(event.get("type").asText()).isEqualTo("ADD_COLUMN");
        assertThat(event.get("column").get("name").asText()).isEqualTo("İncelemede");
        assertThat(event.get("column").get("position").asInt()).isEqualTo(3);   // sona eklendi
        assertThat(event.get("column").get("cards")).isEmpty();
        client.disconnect();

        assertThat(columnNames(token, boardId))
                .containsExactly("To Do", "In Progress", "Done", "İncelemede");
    }

    @Test
    @DisplayName("Kolon yeniden adlandırılır")
    void kolonAdiDegisir() throws Exception {
        String token = registerAndLogin("Kolon", "Adlandiran", uniqueEmail("col"));
        JsonNode board = createBoard(token, "Adlandırma Testi");
        long boardId = board.get("id").asLong();
        long todo = board.get("columns").get(0).get("id").asLong();
        String topic = "/topic/board." + boardId;

        StompTestClient client = StompTestClient.connect(wsUrl(), token).subscribe(topic);
        StompTestClient.awaitSubscriptions();

        client.send("/app/board/" + boardId + "/ops",
                Map.of("type", "RENAME_COLUMN", "columnId", todo, "name", "Yapılacaklar"));

        JsonNode event = client.awaitMessage(topic);
        assertThat(event.get("type").asText()).isEqualTo("RENAME_COLUMN");
        assertThat(event.get("name").asText()).isEqualTo("Yapılacaklar");
        client.disconnect();

        assertThat(columnNames(token, boardId)).first().isEqualTo("Yapılacaklar");
    }

    @Test
    @DisplayName("Kolon silinince kartları da gider ve kalanlar yeniden sıralanır")
    void kolonSilinir() throws Exception {
        String token = registerAndLogin("Kolon", "Silen", uniqueEmail("col"));
        JsonNode board = createBoard(token, "Silme Testi");
        long boardId = board.get("id").asLong();
        long todo = board.get("columns").get(0).get("id").asLong();
        long progress = board.get("columns").get(1).get("id").asLong();
        String topic = "/topic/board." + boardId;
        String ops = "/app/board/" + boardId + "/ops";

        StompTestClient client = StompTestClient.connect(wsUrl(), token).subscribe(topic);
        StompTestClient.awaitSubscriptions();

        client.send(ops, Map.of("type", "ADD_CARD", "columnId", progress, "title", "Silinecek kart"));
        client.awaitMessage(topic);
        client.send(ops, Map.of("type", "ADD_CARD", "columnId", todo, "title", "Kalacak kart"));
        client.awaitMessage(topic);

        client.send(ops, Map.of("type", "DELETE_COLUMN", "columnId", progress));

        JsonNode event = client.awaitMessage(topic);
        assertThat(event.get("type").asText()).isEqualTo("DELETE_COLUMN");
        // Ad ve konum, geçmişin yeniden kurulabilmesi için olayla birlikte taşınır.
        assertThat(event.get("name").asText()).isEqualTo("In Progress");
        assertThat(event.get("position").asInt()).isEqualTo(1);
        client.disconnect();

        JsonNode fresh = board(token, boardId);
        assertThat(columnNames(fresh)).containsExactly("To Do", "Done");
        // Boşluksuz yeniden numaralandırma: 0,1 — yoksa sonraki taşımalar yanlış konuma düşer.
        assertThat(fresh.get("columns").get(0).get("position").asInt()).isZero();
        assertThat(fresh.get("columns").get(1).get("position").asInt()).isEqualTo(1);
        assertThat(fresh.get("columns").get(0).get("cards")).hasSize(1);
    }

    @Test
    @DisplayName("Boş kolon adı reddedilir, gönderene gerekçe döner")
    void bosAdReddedilir() throws Exception {
        String token = registerAndLogin("Kolon", "Bos", uniqueEmail("col"));
        JsonNode board = createBoard(token, "Boş Ad Testi");
        long boardId = board.get("id").asLong();
        String topic = "/topic/board." + boardId;

        StompTestClient client = StompTestClient.connect(wsUrl(), token)
                .subscribe(topic).subscribe("/user/queue/errors");
        StompTestClient.awaitSubscriptions();

        client.send("/app/board/" + boardId + "/ops",
                Map.of("type", "ADD_COLUMN", "boardId", boardId, "name", "   "));

        JsonNode rejection = client.awaitMessage("/user/queue/errors");
        assertThat(rejection.get("reason").asText()).isEqualTo("INVALID");
        assertThat(client.poll(topic, 1)).isNull();
        client.disconnect();

        assertThat(columnNames(token, boardId)).hasSize(3);
    }

    @Test
    @DisplayName("Geçmişte kolonlar o anki hâliyle görünür: eklenen yok, silinen var")
    void gecmisteKolonlarOAnkiHaliyleGorunur() throws Exception {
        String token = registerAndLogin("Kolon", "Gecmis", uniqueEmail("col"));
        JsonNode board = createBoard(token, "Kolon Geçmişi");
        long boardId = board.get("id").asLong();
        long todo = board.get("columns").get(0).get("id").asLong();
        long done = board.get("columns").get(2).get("id").asLong();
        String topic = "/topic/board." + boardId;
        String ops = "/app/board/" + boardId + "/ops";

        StompTestClient client = StompTestClient.connect(wsUrl(), token).subscribe(topic);
        StompTestClient.awaitSubscriptions();

        client.send(ops, Map.of("type", "ADD_CARD", "columnId", todo, "title", "İlk kart"));
        client.awaitMessage(topic);
        client.send(ops, Map.of("type", "ADD_COLUMN", "boardId", boardId, "name", "İncelemede"));
        client.awaitMessage(topic);
        client.send(ops, Map.of("type", "DELETE_COLUMN", "columnId", done));
        client.awaitMessage(topic);
        client.disconnect();

        JsonNode timeline = rest.exchange("/api/boards/" + boardId + "/timeline", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), JsonNode.class).getBody();
        assertThat(timeline).hasSize(3);
        long ilkKartAni = timeline.get(0).get("id").asLong();
        long sonAn = timeline.get(2).get("id").asLong();

        // Bugün: İncelemede var, Done yok.
        assertThat(columnNames(token, boardId)).containsExactly("To Do", "In Progress", "İncelemede");

        // İlk kartın eklendiği an: İncelemede henüz YOK, Done hâlâ VAR.
        assertThat(columnNames(historyAt(token, boardId, ilkKartAni)))
                .containsExactly("To Do", "In Progress", "Done");

        // Son an: İncelemede eklenmiş, Done silinmiş.
        assertThat(columnNames(historyAt(token, boardId, sonAn)))
                .containsExactly("To Do", "In Progress", "İncelemede");
    }

    // ── yardımcılar ──────────────────────────────────────────────────

    private JsonNode board(String token, long boardId) {
        return rest.exchange("/api/boards/" + boardId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), JsonNode.class).getBody();
    }

    private JsonNode historyAt(String token, long boardId, long upTo) {
        return rest.exchange("/api/boards/" + boardId + "/history?upTo=" + upTo, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), JsonNode.class).getBody();
    }

    private List<String> columnNames(String token, long boardId) {
        return columnNames(board(token, boardId));
    }

    private List<String> columnNames(JsonNode board) {
        return board.get("columns").findValuesAsText("name");
    }
}
