package com.collabboard.board;

import com.collabboard.support.IntegrationTestBase;
import com.collabboard.support.StompTestClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kart anahtarı (STR-42): çözme ucu ve genel aramada anahtarla bulma (ADR 0021).
 *
 * Asıl sınanan yine sızıntı: anahtarlar sıralı, yani tahmin edilebilir. Erişemediğim
 * panodaki STR-42 bana "yok" görünmeli; başlığı, panosu, varlığı sızmamalı.
 */
class CardKeyIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("Anahtar kartı ve panosunu verir; kart kolon değiştirse de anahtar aynı kalır")
    void anahtarCozulur() throws Exception {
        String owner = registerAndLogin("Anahtar", "Sahibi", uniqueEmail("anahtar"));
        JsonNode board = createBoard(owner, "Anahtar Panosu");
        long boardId = board.get("id").asLong();
        long done = board.get("columns").get(2).get("id").asLong();

        long cardId;
        try (Cards c = new Cards(owner, board)) {
            cardId = c.add("Ödeme sayfası çöküyor");

            JsonNode found = resolve(owner, "STR-" + cardId).getBody();
            assertThat(found.get("key").asText()).isEqualTo("STR-" + cardId);
            assertThat(found.get("cardId").asLong()).isEqualTo(cardId);
            assertThat(found.get("boardId").asLong()).isEqualTo(boardId);
            assertThat(found.get("boardName").asText()).isEqualTo("Anahtar Panosu");
            assertThat(found.get("columnName").asText()).isEqualTo("Yapılacak");
            assertThat(found.get("title").asText()).isEqualTo("Ödeme sayfası çöküyor");
            assertThat(found.get("branch").asText()).isEqualTo("str-" + cardId + "-odeme-sayfasi-cokuyor");

            // Küçük harf ve yalın sayı da aynı kartı bulur
            assertThat(resolve(owner, "str-" + cardId).getBody().get("cardId").asLong()).isEqualTo(cardId);
            assertThat(resolve(owner, String.valueOf(cardId)).getBody().get("cardId").asLong()).isEqualTo(cardId);

            c.move(cardId, done);
        }

        JsonNode moved = resolve(owner, "STR-" + cardId).getBody();
        assertThat(moved.get("key").asText()).isEqualTo("STR-" + cardId);
        assertThat(moved.get("columnName").asText()).isEqualTo("Bitti");
    }

    @Test
    @DisplayName("Başkasının kartı bulunmaz: hiç olmayan kartla aynı 404; üye izleyici bulur")
    void erisimDenetlenir() throws Exception {
        String owner = registerAndLogin("Gizli", "Sahip", uniqueEmail("anahtar"));
        JsonNode board = createBoard(owner, "Gizli Anahtarlar");
        long cardId;
        try (Cards c = new Cards(owner, board)) {
            cardId = c.add("Gizli kart başlığı");
        }

        String stranger = registerAndLogin("Yabancı", "Biri", uniqueEmail("anahtar"));
        ResponseEntity<JsonNode> foreign = resolve(stranger, "STR-" + cardId);
        ResponseEntity<JsonNode> missing = resolve(stranger, "STR-999999999");
        assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(foreign.getBody().get("message").asText())
                .isEqualTo(missing.getBody().get("message").asText())
                .isEqualTo("Kart bulunamadı.");
        assertThat(foreign.getBody().toString()).doesNotContain("Gizli kart başlığı", "Gizli Anahtarlar");

        String viewerEmail = uniqueEmail("anahtar");
        String viewer = registerAndLogin("İzleyen", "Üye", viewerEmail);
        addMember(board.get("id").asLong(), owner, viewerEmail, "VIEWER");
        assertThat(resolve(viewer, "STR-" + cardId).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> malformed = resolve(owner, "PLAN-12");
        assertThat(malformed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(malformed.getBody().get("message").asText()).isEqualTo("Geçersiz kart anahtarı. Örnek: STR-42");

        assertThat(rest.getForEntity("/api/cards/by-key/STR-" + cardId, JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Aramada STR-42, str-42 ve 42 kartı ilk sırada bulur; başkası bulamaz")
    void aramadaAnahtar() throws Exception {
        String owner = registerAndLogin("Arama", "Anahtar", uniqueEmail("anahtar"));
        JsonNode board = createBoard(owner, "Arama Anahtarı");
        long cardId;
        try (Cards c = new Cards(owner, board)) {
            cardId = c.add("Fatura kesimi");
            // Tek basamaklı sayı metin aramasına girmez (en az 2 karakter); boş veritabanında
            // ilk kartlar 1-9 olur. Metin eşleşmesini de sınamak için iki basamağa çık.
            while (cardId < 10) {
                cardId = c.add("Fatura kesimi");
            }
            // Başlığında aynı sayı geçen ikinci kart: metinle bulunur, anahtar eşleşmesinin arkasında
            c.add("Bağlı iş " + cardId);
            // Anahtarla bulunan kartın başlığında da sayı geçiyor: listede iki kez görünmemeli
            c.rename(cardId, "Fatura kesimi " + cardId);
        }

        for (String q : new String[]{"STR-" + cardId, "str-" + cardId, " Str-" + cardId + " "}) {
            JsonNode cards = search(owner, q).getBody().get("cards");
            assertThat(cards).as(q).isNotEmpty();
            assertThat(cards.get(0).get("cardId").asLong()).as(q).isEqualTo(cardId);
            assertThat(cards.get(0).get("key").asText()).isEqualTo("STR-" + cardId);
            assertThat(cards.get(0).get("columnName").asText()).isEqualTo("Yapılacak");
        }

        List<String> byNumber = titles(search(owner, String.valueOf(cardId)).getBody().get("cards"));
        assertThat(byNumber).containsExactly("Fatura kesimi " + cardId, "Bağlı iş " + cardId);

        String stranger = registerAndLogin("Yabancı", "Arayan", uniqueEmail("anahtar"));
        createBoard(stranger, "Kendi Panom");
        JsonNode foreign = search(stranger, "STR-" + cardId).getBody();
        assertThat(foreign.get("cards")).isEmpty();
        assertThat(foreign.toString()).doesNotContain("Fatura", "Arama Anahtarı");
    }

    // ── Yardımcılar ─────────────────────────────────────────

    private ResponseEntity<JsonNode> resolve(String token, String key) {
        HttpHeaders headers = authHeaders(token);
        return rest.exchange("/api/cards/by-key/{key}", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class, key);
    }

    private ResponseEntity<JsonNode> search(String token, String q) {
        return rest.exchange("/api/search?q={q}", HttpMethod.GET, new HttpEntity<>(authHeaders(token)),
                JsonNode.class, q);
    }

    private static List<String> titles(JsonNode cards) {
        List<String> out = new ArrayList<>();
        cards.forEach(c -> out.add(c.get("title").asText()));
        return out;
    }

    /** Panonun ilk kolonuna canlı kanaldan kart ekler, taşır, adını değiştirir; her işlemde yayını bekler. */
    private final class Cards implements AutoCloseable {
        private final StompTestClient client;
        private final String topic;
        private final String ops;
        private final long firstColumn;
        private final Map<Long, Long> versions = new HashMap<>();

        Cards(String token, JsonNode board) throws Exception {
            long id = board.get("id").asLong();
            firstColumn = board.get("columns").get(0).get("id").asLong();
            topic = "/topic/board." + id;
            ops = "/app/board/" + id + "/ops";
            client = StompTestClient.connect(wsUrl(), token).subscribe(topic);
            StompTestClient.awaitSubscriptions();
        }

        long add(String title) throws InterruptedException {
            client.send(ops, Map.of("type", "ADD_CARD", "columnId", firstColumn, "title", title));
            JsonNode card = client.awaitMessage(topic).get("card");
            versions.put(card.get("id").asLong(), card.get("version").asLong());
            return card.get("id").asLong();
        }

        void move(long cardId, long toColumn) throws InterruptedException {
            client.send(ops, Map.of("type", "MOVE_CARD", "cardId", cardId, "toColumnId", toColumn,
                    "position", 0, "baseVersion", versions.get(cardId)));
            JsonNode moved = client.awaitMessage(topic);
            assertThat(moved.get("type").asText()).isEqualTo("MOVE_CARD");
            versions.put(cardId, moved.get("version").asLong());
        }

        void rename(long cardId, String title) throws InterruptedException {
            Map<String, Object> op = new HashMap<>();   // Map.of null'sız; düzenleme tüm alanları taşır
            op.put("type", "EDIT_CARD");
            op.put("cardId", cardId);
            op.put("title", title);
            op.put("baseVersion", versions.get(cardId));
            client.send(ops, op);
            JsonNode edited = client.awaitMessage(topic);
            assertThat(edited.get("type").asText()).isEqualTo("EDIT_CARD");
            versions.put(cardId, edited.get("version").asLong());
        }

        @Override
        public void close() {
            client.disconnect();
        }
    }
}
