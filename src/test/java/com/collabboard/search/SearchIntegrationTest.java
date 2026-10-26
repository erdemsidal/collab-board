package com.collabboard.search;

import com.collabboard.support.IntegrationTestBase;
import com.collabboard.support.StompTestClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Genel arama (GET /api/search).
 *
 * Asıl sınanan şey yine sızıntı: arama tüm panoları tek sorguda tarar; yetki
 * hatası, erişemediğim bir panodaki kartın başlığını bana gösterirdi. Bu yüzden
 * her senaryoda aynı kelimeyi taşıyan bir yabancı pano ve kart var.
 */
class SearchIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("Arama yalnızca erişilebilen panolarda bulur: başlığı aramayla başlayan kart önce")
    void aramaYalnizcaErisilebilenPanolardanBulur() throws Exception {
        String meEmail = uniqueEmail("arama");
        String me = registerAndLogin("Arama", "Sahibi", meEmail);
        JsonNode marketing = createBoard(me, "Pazarlama Planı");
        createBoard(me, "Plan 2027");
        createBoard(me, "Kahve Listesi");

        // Arkadaşın panosu: davetle İZLEYİCİ'yim → bulunur, rolüm VIEWER görünür
        String mate = registerAndLogin("Ekip", "Arkadaşı", uniqueEmail("arama"));
        JsonNode shared = createBoard(mate, "Ortak Plan");
        addMember(shared.get("id").asLong(), mate, meEmail, "VIEWER");

        // Yabancının panosu: aynı kelime, erişimim yok → hiçbiri bulunmamalı
        String stranger = registerAndLogin("Yabancı", "Kişi", uniqueEmail("arama"));
        JsonNode secret = createBoard(stranger, "Gizli Plan");

        try (Cards c = new Cards(me, marketing)) {
            c.add("Yıllık plan");
            c.add("Plan toplantısı");
            c.describe(c.add("Bütçe"), "Bütçe", "Gelecek yılın planı burada");
            c.add("Kahve");
        }
        try (Cards c = new Cards(mate, shared)) {
            c.add("Plan taslağı");
        }
        try (Cards c = new Cards(stranger, secret)) {
            c.add("Plan gizli kart");
        }

        JsonNode result = search(me, "PLAN").getBody();   // büyük harf: küçük-büyük farkı yok

        JsonNode boards = result.get("boards");
        assertThat(names(boards, "name")).containsExactly("Plan 2027", "Ortak Plan", "Pazarlama Planı");
        assertThat(boards.get(0).get("role").asText()).isEqualTo("OWNER");
        assertThat(boards.get(0).get("workspaceName").asText()).isNotBlank();
        assertThat(boards.get(1).get("role").asText()).isEqualTo("VIEWER");

        JsonNode cards = result.get("cards");
        List<String> titles = names(cards, "title");
        // Başlığı "plan" ile başlayanlar önce, sonra başlığında geçen, sonra yalnızca açıklamasında geçen
        assertThat(titles).hasSize(4);
        assertThat(titles.subList(0, 2)).containsExactlyInAnyOrder("Plan toplantısı", "Plan taslağı");
        assertThat(titles.subList(2, 4)).containsExactly("Yıllık plan", "Bütçe");

        JsonNode meeting = cards.get(titles.indexOf("Plan toplantısı"));
        assertThat(meeting.get("boardId").asLong()).isEqualTo(marketing.get("id").asLong());
        assertThat(meeting.get("boardName").asText()).isEqualTo("Pazarlama Planı");
        assertThat(meeting.get("columnName").asText()).isEqualTo("Yapılacak");
        assertThat(meeting.get("cardId").asLong()).isPositive();

        assertThat(result.toString()).doesNotContain("Gizli Plan", "Plan gizli kart", "Kahve");
    }

    @Test
    @DisplayName("Kısa arama boş döner, uzun arama 400; % ve _ joker değil düz karakter; Türkçe harfler küçültülür")
    void kisaUzunVeOzelKarakterler() throws Exception {
        String me = registerAndLogin("Özel", "Karakter", uniqueEmail("arama"));
        JsonNode board = createBoard(me, "Karakter Panosu");
        try (Cards c = new Cards(me, board)) {
            c.add("%50 indirim");
            c.add("Sürüm 50 notları");     // kaçışlanmasaydı "%50" bunu da bulurdu
            c.add("kod_adı");
            c.add("kodXadı");              // kaçışlanmasaydı "d_a" bunu da bulurdu
            c.add("Şubat bütçesi");
            c.add("İndirim kodu");
        }
        // Yabancının panosunda aynı başlık: bulunmamalı
        String stranger = registerAndLogin("Yabancı", "Karakter", uniqueEmail("arama"));
        try (Cards c = new Cards(stranger, createBoard(stranger, "Yabancı Karakter"))) {
            c.add("%50 indirim");
        }

        // Çok kısa ya da boş: sorgu atılmaz, iki liste de boş
        for (String q : new String[]{"p", "   ", " a "}) {
            ResponseEntity<JsonNode> response = search(me, q);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().get("boards")).isEmpty();
            assertThat(response.getBody().get("cards")).isEmpty();
        }
        ResponseEntity<JsonNode> missing = rest.exchange("/api/search", HttpMethod.GET,
                new HttpEntity<>(authHeaders(me)), JsonNode.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(missing.getBody().get("cards")).isEmpty();

        assertThat(search(me, "x".repeat(100)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(search(me, "x".repeat(101)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(names(search(me, "%50").getBody().get("cards"), "title")).containsExactly("%50 indirim");
        assertThat(names(search(me, "d_a").getBody().get("cards"), "title")).containsExactly("kod_adı");
        assertThat(search(me, "%%").getBody().get("cards")).isEmpty();   // joker olsaydı hepsini bulurdu
        assertThat(names(search(me, "ŞUBAT").getBody().get("cards"), "title")).containsExactly("Şubat bütçesi");
        // Noktalı büyük İ küçülünce "i": baştan eşleşen önce, içinde geçen sonra
        assertThat(names(search(me, "indirim").getBody().get("cards"), "title"))
                .containsExactly("İndirim kodu", "%50 indirim");

        assertThat(rest.getForEntity("/api/search?q=plan", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Her listede en fazla 8 sonuç döner")
    void enFazlaSekizSonuc() throws Exception {
        String me = registerAndLogin("Uzun", "Liste", uniqueEmail("arama"));
        JsonNode board = createBoard(me, "Liste Panosu");
        try (Cards c = new Cards(me, board)) {
            for (int i = 1; i <= 9; i++) {
                c.add("Liste kartı " + i);
            }
        }

        JsonNode cards = search(me, "liste").getBody().get("cards");

        assertThat(cards).hasSize(8);
        assertThat(cards.get(0).get("title").asText()).isEqualTo("Liste kartı 9");   // en yeni önce
    }

    // ── Yardımcılar ─────────────────────────────────────────

    /** Arama metni şablon değişkeniyle gider: %, _ ve Türkçe harfler doğru kodlansın. */
    private ResponseEntity<JsonNode> search(String token, String q) {
        return rest.exchange("/api/search?q={q}", HttpMethod.GET, new HttpEntity<>(authHeaders(token)),
                JsonNode.class, q);
    }

    private static List<String> names(JsonNode items, String field) {
        List<String> out = new ArrayList<>();
        items.forEach(i -> out.add(i.get(field).asText()));
        return out;
    }

    /** Panonun ilk kolonuna canlı kanaldan kart ekler; her işlemden sonra yayını bekler. */
    private final class Cards implements AutoCloseable {
        private final StompTestClient client;
        private final String topic;
        private final String ops;
        private final long firstColumn;

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
            return client.awaitMessage(topic).get("card").get("id").asLong();
        }

        /** Yeni eklenmiş karta (sürüm 0) açıklama yazar; başlık aynı kalır. */
        void describe(long cardId, String title, String description) throws InterruptedException {
            Map<String, Object> op = new HashMap<>();   // Map.of null'sız; düzenleme tüm alanları taşır
            op.put("type", "EDIT_CARD");
            op.put("cardId", cardId);
            op.put("title", title);
            op.put("description", description);
            op.put("baseVersion", 0);
            client.send(ops, op);
            assertThat(client.awaitMessage(topic).get("type").asText()).isEqualTo("EDIT_CARD");
        }

        @Override
        public void close() {
            client.disconnect();
        }
    }
}
