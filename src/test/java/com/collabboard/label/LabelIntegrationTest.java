package com.collabboard.label;

import com.collabboard.support.IntegrationTestBase;
import com.collabboard.support.StompTestClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pano etiketleri ve kartlara takılmaları (ADR 0022).
 *
 * Etiket kartın sürümüne dokunmaz, karta takma bir küme işlemidir (iki kez
 * takmak zararsız), hedef her zaman adresteki panoda aranır ve etiketler
 * zaman yolculuğunda o anki hâliyle görünür. Testler bunları sabitler.
 */
class LabelIntegrationTest extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    // ── temel akış ──────────────────────────────────────────────────

    @Test
    @DisplayName("Etiket oluşturulur, adı ve rengi değişir; pano fotoğrafında etiketler ve kartın etiketleri var")
    void etiketYasamDongusu() throws Exception {
        String owner = registerAndLogin("Etiket", "Sahibi", uniqueEmail("etiket"));
        Fixture f = boardWithCard(owner, "Etiket Panosu", "Ödeme sayfası");

        ResponseEntity<JsonNode> created = createLabel(f.boardId, owner, "  Acil  ", "red");
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("name").asText()).isEqualTo("Acil");   // boşluk atılır
        assertThat(created.getBody().get("color").asText()).isEqualTo("RED");   // küçük harf de kabul
        long acil = created.getBody().get("id").asLong();

        // Adsız etiket: boş ad null olur.
        JsonNode unnamed = createLabel(f.boardId, owner, "   ", "SKY").getBody();
        assertThat(unnamed.get("name").isNull()).isTrue();
        long sky = unnamed.get("id").asLong();

        ResponseEntity<JsonNode> renamed = patchLabel(f.boardId, owner, acil, "Çok acil", "ORANGE");
        assertThat(renamed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(renamed.getBody().get("name").asText()).isEqualTo("Çok acil");
        assertThat(renamed.getBody().get("color").asText()).isEqualTo("ORANGE");

        assertThat(attach(f, owner, sky).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(attach(f, owner, acil).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        JsonNode board = getBoard(f.boardId, owner).getBody();
        assertThat(board.get("labels")).hasSize(2);
        assertThat(board.get("labels").get(0).get("name").asText()).isEqualTo("Çok acil");
        JsonNode card = board.get("columns").get(0).get("cards").get(0);
        assertThat(ids(card.get("labelIds"))).containsExactly(acil, sky);   // panodaki sıra (id)
        assertThat(card.get("version").asLong()).isZero();                   // kartın sürümü değişmedi

        assertThat(detach(f, owner, acil).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        JsonNode after = getBoard(f.boardId, owner).getBody().get("columns").get(0).get("cards").get(0);
        assertThat(ids(after.get("labelIds"))).containsExactly(sky);
    }

    @Test
    @DisplayName("Aynı etiketi iki kez takmak ya da takılı olmayanı çıkarmak zararsız; geçmişe bir kez yazılır")
    void takmaVeCikarmaTekrarlanabilir() throws Exception {
        String owner = registerAndLogin("Tekrar", "Eden", uniqueEmail("etiket"));
        Fixture f = boardWithCard(owner, "Tekrar", "Kart");
        long label = createLabel(f.boardId, owner, "Tasarım", "PURPLE").getBody().get("id").asLong();

        assertThat(attach(f, owner, label).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(attach(f, owner, label).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(detach(f, owner, label).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(detach(f, owner, label).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        JsonNode activity = get("/api/boards/" + f.boardId + "/activity", owner);
        assertThat(types(activity)).containsExactly("REMOVE_LABEL", "ADD_LABEL", "ADD_CARD");
        JsonNode added = activity.get(1);
        assertThat(added.get("params").get("card").asText()).isEqualTo("Kart");
        assertThat(added.get("params").get("label").asText()).isEqualTo("Tasarım");
        assertThat(added.get("params").get("color").asText()).isEqualTo("PURPLE");
        assertThat(added.get("description").asText()).isEqualTo("'Kart' kartına 'Tasarım' etiketini ekledi");
    }

    // ── doğrulama ve sınırlar ───────────────────────────────────────

    @Test
    @DisplayName("Geçersiz renk, uzun ad, aynı ad ve 30'dan fazla etiket reddedilir")
    void gecersizEtiket() throws Exception {
        String owner = registerAndLogin("Gecersiz", "Etiket", uniqueEmail("etiket"));
        long boardId = createBoard(owner, "Sınırlar").get("id").asLong();

        ResponseEntity<JsonNode> badColor = createLabel(boardId, owner, "X", "teal");
        assertThat(badColor.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badColor.getBody().get("message").asText()).contains("GREEN");
        assertThat(createLabel(boardId, owner, "X", null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<JsonNode> tooLong = createLabel(boardId, owner, "a".repeat(41), "GREEN");
        assertThat(tooLong.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(tooLong.getBody().get("message").asText()).contains("40");
        long first = createLabel(boardId, owner, "a".repeat(40), "GREEN").getBody().get("id").asLong();

        // Aynı ad: büyük/küçük harf farkı sayılmaz, Türkçe İ dahil.
        createLabel(boardId, owner, "İnceleme", "BLUE");
        ResponseEntity<JsonNode> dup = createLabel(boardId, owner, "inceleme", "RED");
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(dup.getBody().get("message").asText()).isEqualTo("Bu panoda aynı adda bir etiket zaten var.");
        assertThat(patchLabel(boardId, owner, first, "İNCELEME", "GREEN").getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        // Adsız etiketler serbest; kendi adını değiştirmeden rengini değiştirmek serbest.
        assertThat(createLabel(boardId, owner, "", "RED").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createLabel(boardId, owner, null, "RED").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(patchLabel(boardId, owner, first, "a".repeat(40), "PINK").getStatusCode()).isEqualTo(HttpStatus.OK);

        // 30 sınırı: şu an 4 etiket var.
        for (int i = 4; i < LabelService.MAX_LABELS; i++) {
            assertThat(createLabel(boardId, owner, "Etiket " + i, "YELLOW").getStatusCode())
                    .isEqualTo(HttpStatus.CREATED);
        }
        ResponseEntity<JsonNode> over = createLabel(boardId, owner, "Bir fazla", "YELLOW");
        assertThat(over.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(over.getBody().get("message").asText()).contains("30");
        assertThat(getBoard(boardId, owner).getBody().get("labels")).hasSize(LabelService.MAX_LABELS);
    }

    // ── yetki ve kapsam ─────────────────────────────────────────────

    @Test
    @DisplayName("İzleyici etiketleri görür ama oluşturamaz, değiştiremez, takamaz; üye olmayan 403 alır")
    void izleyiciDegistiremez() throws Exception {
        String ownerEmail = uniqueEmail("etiket"), viewerEmail = uniqueEmail("izleyici");
        String owner = registerAndLogin("Pano", "Sahibi", ownerEmail);
        String viewer = registerAndLogin("Sadece", "Izleyen", viewerEmail);
        String stranger = registerAndLogin("Yabanci", "Kisi", uniqueEmail("yabanci"));
        Fixture f = boardWithCard(owner, "Yetki", "Kart");
        addMember(f.boardId, owner, viewerEmail, "VIEWER");
        long label = createLabel(f.boardId, owner, "Hata", "RED").getBody().get("id").asLong();
        attach(f, owner, label);

        assertThat(getBoard(f.boardId, viewer).getBody().get("labels")).hasSize(1);

        for (String token : List.of(viewer, stranger)) {
            assertThat(createLabel(f.boardId, token, "Benim", "GREEN").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(patchLabel(f.boardId, token, label, "Değişti", "GREEN").getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(deleteLabel(f.boardId, token, label).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(detach(f, token, label).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(attach(f, token, label).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        ResponseEntity<JsonNode> denied = createLabel(f.boardId, viewer, "Benim", "GREEN");
        assertThat(denied.getBody().get("message").asText()).contains("VIEWER");

        JsonNode board = getBoard(f.boardId, owner).getBody();
        assertThat(board.get("labels").get(0).get("name").asText()).isEqualTo("Hata");
        assertThat(ids(board.get("columns").get(0).get("cards").get(0).get("labelIds"))).containsExactly(label);
    }

    @Test
    @DisplayName("Başka panonun etiketi ya da kartı bu panonun adresinden kullanılamaz: 404, hiçbir şey değişmez")
    void baskaPanonunHedefi() throws Exception {
        String attacker = registerAndLogin("Saldirgan", "Kisi", uniqueEmail("saldirgan"));
        String victim = registerAndLogin("Kurban", "Kisi", uniqueEmail("kurban"));
        Fixture mine = boardWithCard(attacker, "Benim", "Benim kartım");
        Fixture theirs = boardWithCard(victim, "Onların", "Gizli kart");
        long myLabel = createLabel(mine.boardId, attacker, "Benim", "GREEN").getBody().get("id").asLong();
        long theirLabel = createLabel(theirs.boardId, victim, "Onların", "RED").getBody().get("id").asLong();

        // Kendi panomun adresinden: onların etiketi, onların kartı.
        Fixture theirCardOnMyPath = new Fixture(mine.boardId, theirs.cardId);
        assertThat(attach(mine, attacker, theirLabel).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(attach(theirCardOnMyPath, attacker, myLabel).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(detach(theirCardOnMyPath, attacker, myLabel).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ResponseEntity<JsonNode> rename = patchLabel(mine.boardId, attacker, theirLabel, "Ele geçirildi", "GREEN");
        assertThat(rename.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rename.getBody().get("message").asText()).isEqualTo("Etiket bulunamadı.");
        assertThat(deleteLabel(mine.boardId, attacker, theirLabel).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Onların panosu: etiket aynen duruyor, kartlarına hiçbir şey takılmadı.
        JsonNode board = getBoard(theirs.boardId, victim).getBody();
        assertThat(board.get("labels").get(0).get("name").asText()).isEqualTo("Onların");
        assertThat(board.get("columns").get(0).get("cards").get(0).get("labelIds")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM card_labels WHERE card_id = ?", Long.class,
                theirs.cardId)).isZero();
    }

    // ── silme ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Etiket silinince takılı olduğu bütün kartlardan kalkar; diğer etiketler yerinde kalır")
    void silmeKartlardanKaldirir() throws Exception {
        String owner = registerAndLogin("Silen", "Kisi", uniqueEmail("etiket"));
        Fixture f = boardWithCard(owner, "Silme", "Birinci");
        Fixture second = new Fixture(f.boardId, addCard(owner, f.boardId, f.columnId, "İkinci"));
        long doomed = createLabel(f.boardId, owner, "Eski", "YELLOW").getBody().get("id").asLong();
        long kept = createLabel(f.boardId, owner, "Kalıcı", "GREEN").getBody().get("id").asLong();
        attach(f, owner, doomed);
        attach(second, owner, doomed);
        attach(f, owner, kept);

        assertThat(deleteLabel(f.boardId, owner, doomed).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        JsonNode board = getBoard(f.boardId, owner).getBody();
        assertThat(board.get("labels")).hasSize(1);
        Map<Long, List<Long>> labelsByCard = new HashMap<>();
        board.get("columns").get(0).get("cards").forEach(c -> labelsByCard.put(c.get("id").asLong(), ids(c.get("labelIds"))));
        assertThat(labelsByCard).containsEntry(f.cardId, List.of(kept)).containsEntry(second.cardId, List.of());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM card_labels WHERE label_id = ?", Long.class, doomed))
                .isZero();

        JsonNode deleted = get("/api/boards/" + f.boardId + "/activity", owner).get(0);
        assertThat(deleted.get("type").asText()).isEqualTo("DELETE_LABEL");
        assertThat(deleted.get("params").get("label").asText()).isEqualTo("Eski");
        assertThat(deleted.get("params").get("count").asInt()).isEqualTo(2);
        assertThat(deleteLabel(f.boardId, owner, doomed).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Kart silinince etiket bağları da gider; etiketin kendisi kalır")
    void kartSilinince() throws Exception {
        String owner = registerAndLogin("Kart", "Silen", uniqueEmail("etiket"));
        Fixture f = boardWithCard(owner, "Kart silme", "Gidecek kart");
        long label = createLabel(f.boardId, owner, "Kalır", "BLUE").getBody().get("id").asLong();
        attach(f, owner, label);

        op(owner, f.boardId, Map.of("type", "DELETE_CARD", "cardId", f.cardId), "DELETE_CARD");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM card_labels WHERE card_id = ?", Long.class, f.cardId))
                .isZero();
        assertThat(getBoard(f.boardId, owner).getBody().get("labels")).hasSize(1);
    }

    // ── canlı yayın ─────────────────────────────────────────────────

    @Test
    @DisplayName("Her değişiklik panodaki herkese canlı gider: oluşturma, düzenleme, takma, çıkarma, silme")
    void canliOlaylar() throws Exception {
        String ownerEmail = uniqueEmail("etiket"), viewerEmail = uniqueEmail("izleyici");
        String owner = registerAndLogin("Canli", "Yazan", ownerEmail);
        String viewer = registerAndLogin("Canli", "Izleyen", viewerEmail);
        Fixture f = boardWithCard(owner, "Canlı", "Kart");
        addMember(f.boardId, owner, viewerEmail, "VIEWER");
        String topic = "/topic/board." + f.boardId;

        StompTestClient watcher = StompTestClient.connect(wsUrl(), viewer).subscribe(topic);
        StompTestClient.awaitSubscriptions();

        long label = createLabel(f.boardId, owner, "Canlı", "GREEN").getBody().get("id").asLong();
        JsonNode created = awaitType(watcher, topic, "LABEL_CREATED");
        assertThat(created.get("label").get("name").asText()).isEqualTo("Canlı");
        assertThat(created.get("labelId").asLong()).isEqualTo(label);

        patchLabel(f.boardId, owner, label, "Yayında", "SKY");
        JsonNode updated = awaitType(watcher, topic, "LABEL_UPDATED");
        assertThat(updated.get("label").get("color").asText()).isEqualTo("SKY");

        attach(f, owner, label);
        JsonNode added = awaitType(watcher, topic, "CARD_LABEL_ADDED");
        assertThat(added.get("cardId").asLong()).isEqualTo(f.cardId);
        assertThat(added.get("labelId").asLong()).isEqualTo(label);

        detach(f, owner, label);
        assertThat(awaitType(watcher, topic, "CARD_LABEL_REMOVED").get("cardId").asLong()).isEqualTo(f.cardId);

        deleteLabel(f.boardId, owner, label);
        JsonNode deleted = awaitType(watcher, topic, "LABEL_DELETED");
        assertThat(deleted.get("labelId").asLong()).isEqualTo(label);
        assertThat(deleted.has("label")).isFalse();

        // Zaten takılı olanı yeniden takmak olay üretmez.
        long second = createLabel(f.boardId, owner, "İkinci", "RED").getBody().get("id").asLong();
        awaitType(watcher, topic, "LABEL_CREATED");
        attach(f, owner, second);
        awaitType(watcher, topic, "CARD_LABEL_ADDED");
        attach(f, owner, second);
        assertThat(watcher.poll(topic, 1)).isNull();
        watcher.disconnect();
    }

    // ── eşzamanlılık ────────────────────────────────────────────────

    @Test
    @DisplayName("Aynı anda aynı etiketi takan on istek: tek bağ, tek geçmiş satırı, tek olay")
    void ayniAndaTakma() throws Exception {
        String owner = registerAndLogin("Ayni", "Anda", uniqueEmail("etiket"));
        Fixture f = boardWithCard(owner, "Eşzamanlı", "Kart");
        long label = createLabel(f.boardId, owner, "Yarış", "ORANGE").getBody().get("id").asLong();

        List<HttpStatus> statuses = concurrently(10, () -> (HttpStatus) attach(f, owner, label).getStatusCode());
        assertThat(statuses).containsOnly(HttpStatus.NO_CONTENT);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM card_labels WHERE card_id = ?", Long.class, f.cardId))
                .isEqualTo(1);
        assertThat(types(get("/api/boards/" + f.boardId + "/activity", owner)).stream()
                .filter("ADD_LABEL"::equals).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Sınıra yakın panoda aynı anda gelen etiketler 30'u aşamaz; aynı adla gelen ikisinden biri 409 alır")
    void ayniAndaOlusturma() throws Exception {
        String owner = registerAndLogin("Sinir", "Zorlayan", uniqueEmail("etiket"));
        long boardId = createBoard(owner, "Sınırda").get("id").asLong();
        for (int i = 0; i < LabelService.MAX_LABELS - 3; i++) {
            createLabel(boardId, owner, "Etiket " + i, "GREEN");
        }

        List<HttpStatus> statuses = concurrently(8, () ->
                (HttpStatus) createLabel(boardId, owner, null, "BLUE").getStatusCode());
        assertThat(statuses.stream().filter(HttpStatus.CREATED::equals).count()).isEqualTo(3);
        assertThat(getBoard(boardId, owner).getBody().get("labels")).hasSize(LabelService.MAX_LABELS);

        String other = registerAndLogin("Ad", "Yarisi", uniqueEmail("etiket"));
        long second = createBoard(other, "Ad yarışı").get("id").asLong();
        List<HttpStatus> names = concurrently(6, () ->
                (HttpStatus) createLabel(second, other, "Aynı ad", "RED").getStatusCode());
        assertThat(names.stream().filter(HttpStatus.CREATED::equals).count()).isEqualTo(1);
        assertThat(names.stream().filter(HttpStatus.CONFLICT::equals).count()).isEqualTo(5);
    }

    // ── zaman yolculuğu ─────────────────────────────────────────────

    @Test
    @DisplayName("Geçmişte etiketler o anki hâliyle görünür; silinen etiket silinmeden önceki anlarda yine görünür")
    void gecmisteEtiketler() throws Exception {
        String owner = registerAndLogin("Gecmis", "Bakan", uniqueEmail("etiket"));
        Fixture f = boardWithCard(owner, "Geçmiş", "Zaman kartı");
        long acil = createLabel(f.boardId, owner, "Acil", "RED").getBody().get("id").asLong();
        long kalici = createLabel(f.boardId, owner, null, "GREEN").getBody().get("id").asLong();
        attach(f, owner, acil);
        attach(f, owner, kalici);
        deleteLabel(f.boardId, owner, acil);

        JsonNode timeline = get("/api/boards/" + f.boardId + "/timeline", owner);
        assertThat(types(timeline)).containsExactly("ADD_CARD", "ADD_LABEL", "ADD_LABEL", "DELETE_LABEL");
        long addCard = timeline.get(0).get("id").asLong();
        long bothOn = timeline.get(2).get("id").asLong();
        long afterDelete = timeline.get(3).get("id").asLong();

        assertThat(cardLabelsAt(f, owner, addCard)).isEmpty();
        JsonNode past = history(f.boardId, owner, bothOn);
        assertThat(ids(past.get("columns").get(0).get("cards").get(0).get("labelIds"))).containsExactly(acil, kalici);
        // Silinen "Acil" bugün panoda yok ama geçmiş görünümü onu adı ve rengiyle tanır.
        Map<Long, String> names = new HashMap<>();
        past.get("labels").forEach(l -> names.put(l.get("id").asLong(), l.path("name").asText("")));
        assertThat(names).containsEntry(acil, "Acil").containsKey(kalici);
        assertThat(past.get("columns").get(0).get("cards").get(0).get("checklists")).isEmpty();

        assertThat(cardLabelsAt(f, owner, afterDelete)).containsExactly(kalici);
        // Etiketler canlı panoyla aynı: zaman çizelgesinin sonu bugünle aynı.
        assertThat(ids(getBoard(f.boardId, owner).getBody().get("columns").get(0).get("cards").get(0)
                .get("labelIds"))).containsExactly(kalici);
    }

    // ── yardımcılar ─────────────────────────────────────────────────

    private record Fixture(long boardId, long columnId, long cardId) {
        Fixture(long boardId, long cardId) {
            this(boardId, 0, cardId);
        }
    }

    private Fixture boardWithCard(String token, String boardName, String cardTitle) throws Exception {
        JsonNode board = createBoard(token, boardName);
        long boardId = board.get("id").asLong();
        long columnId = board.get("columns").get(0).get("id").asLong();
        return new Fixture(boardId, columnId, addCard(token, boardId, columnId, cardTitle));
    }

    private long addCard(String token, long boardId, long columnId, String title) throws Exception {
        return op(token, boardId, Map.of("type", "ADD_CARD", "columnId", columnId, "title", title), "ADD_CARD")
                .get("card").get("id").asLong();
    }

    /** Kart operasyonunu gerçek WebSocket kanalından gönderir ve olayını bekler. */
    private JsonNode op(String token, long boardId, Map<String, Object> op, String type) throws Exception {
        String topic = "/topic/board." + boardId;
        StompTestClient client = StompTestClient.connect(wsUrl(), token).subscribe(topic);
        StompTestClient.awaitSubscriptions();
        client.send("/app/board/" + boardId + "/ops", op);
        JsonNode event = awaitType(client, topic, type);
        client.disconnect();
        return event;
    }

    private ResponseEntity<JsonNode> createLabel(long boardId, String token, String name, String color) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("color", color);
        return rest.exchange("/api/boards/" + boardId + "/labels", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> patchLabel(long boardId, String token, long labelId, String name, String color) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("color", color);
        return rest.exchange("/api/boards/" + boardId + "/labels/" + labelId, HttpMethod.PATCH,
                new HttpEntity<>(body, authHeaders(token)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> deleteLabel(long boardId, String token, long labelId) {
        return rest.exchange("/api/boards/" + boardId + "/labels/" + labelId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> attach(Fixture f, String token, long labelId) {
        return rest.exchange("/api/boards/" + f.boardId + "/cards/" + f.cardId + "/labels/" + labelId, HttpMethod.PUT,
                new HttpEntity<>(authHeaders(token)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> detach(Fixture f, String token, long labelId) {
        return rest.exchange("/api/boards/" + f.boardId + "/cards/" + f.cardId + "/labels/" + labelId,
                HttpMethod.DELETE, new HttpEntity<>(authHeaders(token)), JsonNode.class);
    }

    private JsonNode history(long boardId, String token, long upTo) {
        return get("/api/boards/" + boardId + "/history?upTo=" + upTo, token);
    }

    private List<Long> cardLabelsAt(Fixture f, String token, long upTo) {
        return ids(history(f.boardId, token, upTo).get("columns").get(0).get("cards").get(0).get("labelIds"));
    }

    private JsonNode get(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(authHeaders(token)), JsonNode.class).getBody();
    }

    private static List<Long> ids(JsonNode array) {
        List<Long> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asLong()));
        return out;
    }

    private static List<String> types(JsonNode rows) {
        List<String> out = new ArrayList<>();
        rows.forEach(r -> out.add(r.get("type").asText()));
        return out;
    }

    /** Aynı işi n iş parçacığında aynı anda başlatır, sonuçları toplar. */
    private static <T> List<T> concurrently(int n, Callable<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> out = new ArrayList<>();
            for (Future<T> fut : futures) {
                out.add(fut.get(30, TimeUnit.SECONDS));
            }
            return out;
        } finally {
            pool.shutdownNow();
        }
    }

    /** Kanalda belirli türdeki olayı bekler; araya giren başka olayları atlar. */
    private JsonNode awaitType(StompTestClient client, String topic, String type) throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            JsonNode msg = client.awaitMessage(topic);
            if (type.equals(msg.path("type").asText())) {
                return msg;
            }
        }
        throw new AssertionError("Olay gelmedi: " + type);
    }
}
