package com.collabboard.checklist;

import com.collabboard.support.IntegrationTestBase;
import com.collabboard.support.StompTestClient;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kartların kontrol listeleri (ADR 0022).
 *
 * Listeye dokunan her istek listenin satırını kilitler: aynı anda sıralayan ya
 * da işaretleyen kişiler sıra numaralarını bozamaz, "liste tamamlandı" bir kez
 * yazılır. Her işaretleme geçmişe yazılmaz; listeler zaman yolculuğunda yoktur.
 */
class ChecklistIntegrationTest extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    // ── temel akış ──────────────────────────────────────────────────

    @Test
    @DisplayName("Liste oluşturulur; madde eklenir, işaretlenir, düzenlenir, sıralanır, silinir; her adımda revision artar")
    void listeYasamDongusu() throws Exception {
        String owner = registerAndLogin("Liste", "Sahibi", uniqueEmail("liste"));
        Fixture f = boardWithCard(owner, "Liste Panosu", "Yayına hazırlık");

        ResponseEntity<JsonNode> created = createList(f, owner, "  Yayın öncesi  ");
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode list = created.getBody();
        assertThat(list.get("title").asText()).isEqualTo("Yayın öncesi");
        assertThat(list.get("cardId").asLong()).isEqualTo(f.cardId);
        assertThat(list.get("items")).isEmpty();
        long listId = list.get("id").asLong();
        long rev = list.get("revision").asLong();

        for (String text : List.of("Testleri çalıştır", "Notları yaz", "Duyuruyu hazırla")) {
            JsonNode after = addItem(f, owner, listId, text).getBody();
            assertThat(after.get("revision").asLong()).isGreaterThan(rev);
            rev = after.get("revision").asLong();
        }
        JsonNode three = lists(f, owner).getBody().get(0);
        assertThat(texts(three)).containsExactly("Testleri çalıştır", "Notları yaz", "Duyuruyu hazırla");
        assertThat(positions(three)).containsExactly(0, 1, 2);
        long first = itemId(three, 0), second = itemId(three, 1), third = itemId(three, 2);

        JsonNode ticked = patchItem(f, owner, listId, first, Map.of("done", true)).getBody();
        assertThat(ticked.get("items").get(0).get("done").asBoolean()).isTrue();

        JsonNode edited = patchItem(f, owner, listId, second, Map.of("text", "  Sürüm notlarını yaz ")).getBody();
        assertThat(edited.get("items").get(1).get("text").asText()).isEqualTo("Sürüm notlarını yaz");

        // Sona taşı; listenin dışını gösteren yer sona yuvarlanır.
        JsonNode moved = patchItem(f, owner, listId, first, Map.of("position", 99)).getBody();
        assertThat(ids(moved)).containsExactly(second, third, first);
        assertThat(positions(moved)).containsExactly(0, 1, 2);
        moved = patchItem(f, owner, listId, third, Map.of("position", 0)).getBody();
        assertThat(ids(moved)).containsExactly(third, second, first);

        JsonNode afterDelete = deleteItem(f, owner, listId, second).getBody();
        assertThat(ids(afterDelete)).containsExactly(third, first);
        assertThat(positions(afterDelete)).containsExactly(0, 1);

        JsonNode renamed = renameList(f, owner, listId, "Son kontrol").getBody();
        assertThat(renamed.get("title").asText()).isEqualTo("Son kontrol");

        // Hiçbir şey değişmeyen istek revision'ı artırmaz.
        long before = renamed.get("revision").asLong();
        assertThat(renameList(f, owner, listId, "Son kontrol").getBody().get("revision").asLong()).isEqualTo(before);
        assertThat(patchItem(f, owner, listId, first, Map.of("done", true)).getBody().get("revision").asLong())
                .isEqualTo(before);

        // Kartın sürümü hiç değişmedi: aynı anda kartı taşıyan kimse bayat sayılmaz.
        assertThat(getBoard(f.boardId, owner).getBody().get("columns").get(0).get("cards").get(0)
                .get("version").asLong()).isZero();
    }

    @Test
    @DisplayName("Panonun fotoğrafında her kartın liste özetleri var: yapılan, toplam, revision")
    void panoFotografindaOzet() throws Exception {
        String owner = registerAndLogin("Ozet", "Bakan", uniqueEmail("liste"));
        Fixture f = boardWithCard(owner, "Özet", "Listeli kart");
        long quiet = addCard(owner, f.boardId, f.columnId, "Listesiz kart");
        long a = createList(f, owner, "Birinci").getBody().get("id").asLong();
        long b = createList(f, owner, "İkinci").getBody().get("id").asLong();
        long item = addItem(f, owner, a, "Bir").getBody().get("items").get(0).get("id").asLong();
        addItem(f, owner, a, "İki");
        addItem(f, owner, b, "Üç");
        JsonNode lastA = patchItem(f, owner, a, item, Map.of("done", true)).getBody();

        Map<Long, JsonNode> cards = new HashMap<>();
        getBoard(f.boardId, owner).getBody().get("columns").get(0).get("cards")
                .forEach(c -> cards.put(c.get("id").asLong(), c));
        JsonNode summaries = cards.get(f.cardId).get("checklists");
        assertThat(summaries).hasSize(2);
        assertThat(summaries.get(0).get("id").asLong()).isEqualTo(a);
        assertThat(summaries.get(0).get("done").asLong()).isEqualTo(1);
        assertThat(summaries.get(0).get("total").asLong()).isEqualTo(2);
        assertThat(summaries.get(0).get("revision").asLong()).isEqualTo(lastA.get("revision").asLong());
        assertThat(summaries.get(1).get("done").asLong()).isZero();
        assertThat(summaries.get(1).get("total").asLong()).isEqualTo(1);
        assertThat(cards.get(quiet).get("checklists")).isEmpty();
        assertThat(cards.get(quiet).get("labelIds")).isEmpty();
    }

    @Test
    @DisplayName("Panonun fotoğrafı kart sayısından bağımsız sayıda sorguyla gelir (etiket ve listeler N+1 değil)")
    void fotografN1Degil() throws Exception {
        String owner = registerAndLogin("Sorgu", "Sayan", uniqueEmail("liste"));
        Fixture f = boardWithCard(owner, "Sorgu", "İlk kart");
        decorate(f, owner, f.cardId, 0);

        long small = statementsFor(f.boardId, owner);

        for (int i = 1; i <= 8; i++) {
            long card = addCard(owner, f.boardId, f.columnId, "Kart " + i);
            decorate(f, owner, card, i);
        }
        JsonNode board = getBoard(f.boardId, owner).getBody();
        board.get("columns").get(0).get("cards").forEach(c -> {
            assertThat(c.get("labelIds")).hasSize(1);
            assertThat(c.get("checklists")).hasSize(1);
        });

        long big = statementsFor(f.boardId, owner);
        assertThat(small).as("istatistik toplanıyor").isPositive();
        assertThat(big).as("9 kartlı pano, 1 kartlı panoyla aynı sayıda sorgu").isEqualTo(small);
    }

    // ── doğrulama ve sınırlar ───────────────────────────────────────

    @Test
    @DisplayName("Boş ya da uzun ad ve madde, 10'dan fazla liste, 100'den fazla madde, boş değişiklik 400 alır")
    void sinirlar() throws Exception {
        String owner = registerAndLogin("Sinir", "Koyan", uniqueEmail("liste"));
        Fixture f = boardWithCard(owner, "Sınırlar", "Kart");

        ResponseEntity<JsonNode> blank = createList(f, owner, "   ");
        assertThat(blank.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(blank.getBody().get("message").asText()).isEqualTo("Liste adı boş olamaz.");
        assertThat(createList(f, owner, "a".repeat(101)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        long listId = createList(f, owner, "a".repeat(100)).getBody().get("id").asLong();

        assertThat(addItem(f, owner, listId, " ").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<JsonNode> tooLong = addItem(f, owner, listId, "x".repeat(301));
        assertThat(tooLong.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(tooLong.getBody().get("message").asText()).contains("300");
        JsonNode ok = addItem(f, owner, listId, "x".repeat(300)).getBody();
        long item = ok.get("items").get(0).get("id").asLong();
        assertThat(patchItem(f, owner, listId, item, Map.of("text", "y".repeat(301))).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<JsonNode> empty = patchItem(f, owner, listId, item, Map.of());
        assertThat(empty.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(empty.getBody().get("message").asText()).isEqualTo("Değiştirilecek bir şey gönderilmedi.");

        for (int i = 1; i < ChecklistService.MAX_ITEMS; i++) {
            assertThat(addItem(f, owner, listId, "Madde " + i).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
        ResponseEntity<JsonNode> overItems = addItem(f, owner, listId, "Bir fazla");
        assertThat(overItems.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(overItems.getBody().get("message").asText()).contains("100");

        for (int i = 1; i < ChecklistService.MAX_CHECKLISTS; i++) {
            assertThat(createList(f, owner, "Liste " + i).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
        ResponseEntity<JsonNode> overLists = createList(f, owner, "Bir fazla");
        assertThat(overLists.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(overLists.getBody().get("message").asText()).contains("10");
        assertThat(lists(f, owner).getBody()).hasSize(ChecklistService.MAX_CHECKLISTS);
    }

    // ── yetki ve kapsam ─────────────────────────────────────────────

    @Test
    @DisplayName("İzleyici listeleri okur ama değiştiremez; üye olmayan okuyamaz da")
    void izleyiciDegistiremez() throws Exception {
        String ownerEmail = uniqueEmail("liste"), viewerEmail = uniqueEmail("izleyici");
        String owner = registerAndLogin("Pano", "Sahibi", ownerEmail);
        String viewer = registerAndLogin("Sadece", "Izleyen", viewerEmail);
        String stranger = registerAndLogin("Yabanci", "Kisi", uniqueEmail("yabanci"));
        Fixture f = boardWithCard(owner, "Yetki", "Kart");
        addMember(f.boardId, owner, viewerEmail, "VIEWER");
        long listId = createList(f, owner, "Liste").getBody().get("id").asLong();
        long item = addItem(f, owner, listId, "Madde").getBody().get("items").get(0).get("id").asLong();

        assertThat(lists(f, viewer).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(lists(f, viewer).getBody()).hasSize(1);
        assertThat(lists(f, stranger).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        for (String token : List.of(viewer, stranger)) {
            assertThat(createList(f, token, "Benim").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(renameList(f, token, listId, "Değişti").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(addItem(f, token, listId, "Yeni").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(patchItem(f, token, listId, item, Map.of("done", true)).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(deleteItem(f, token, listId, item).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(deleteList(f, token, listId).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        JsonNode unchanged = lists(f, owner).getBody().get(0);
        assertThat(unchanged.get("title").asText()).isEqualTo("Liste");
        assertThat(unchanged.get("items").get(0).get("done").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("Liste ve madde adresteki kartta, kart adresteki panoda olmalı; değilse 404 ve hiçbir şey değişmez")
    void baskaKartinListesi() throws Exception {
        String attacker = registerAndLogin("Saldirgan", "Kisi", uniqueEmail("saldirgan"));
        String victim = registerAndLogin("Kurban", "Kisi", uniqueEmail("kurban"));
        Fixture mine = boardWithCard(attacker, "Benim", "Benim kartım");
        long myOtherCard = addCard(attacker, mine.boardId, mine.columnId, "Diğer kartım");
        Fixture theirs = boardWithCard(victim, "Onların", "Gizli kart");
        long myList = createList(mine, attacker, "Benim listem").getBody().get("id").asLong();
        long myItem = addItem(mine, attacker, myList, "Benim maddem").getBody().get("items").get(0).get("id").asLong();
        long theirList = createList(theirs, victim, "Onların listesi").getBody().get("id").asLong();
        long theirItem = addItem(theirs, victim, theirList, "Gizli madde").getBody().get("items").get(0).get("id").asLong();

        // Onların kartı, benim panomun adresinden.
        Fixture theirCardOnMyBoard = new Fixture(mine.boardId, mine.columnId, theirs.cardId);
        assertThat(lists(theirCardOnMyBoard, attacker).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(createList(theirCardOnMyBoard, attacker, "Sızma").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        // Onların listesi, benim kartımın adresinden.
        assertThat(addItem(mine, attacker, theirList, "Sızma").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(renameList(mine, attacker, theirList, "Ele geçirildi").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(deleteList(mine, attacker, theirList).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        // Onların maddesi, benim listemin adresinden.
        ResponseEntity<JsonNode> foreignItem = patchItem(mine, attacker, myList, theirItem, Map.of("done", true));
        assertThat(foreignItem.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(foreignItem.getBody().get("message").asText()).isEqualTo("Madde bulunamadı.");
        assertThat(deleteItem(mine, attacker, myList, theirItem).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        // Aynı panoda bile: listem başka kartımın adresinden açılamaz.
        Fixture otherCard = new Fixture(mine.boardId, mine.columnId, myOtherCard);
        ResponseEntity<JsonNode> wrongCard = patchItem(otherCard, attacker, myList, myItem, Map.of("done", true));
        assertThat(wrongCard.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(wrongCard.getBody().get("message").asText()).isEqualTo("Kontrol listesi bulunamadı.");

        JsonNode theirsNow = lists(theirs, victim).getBody().get(0);
        assertThat(theirsNow.get("title").asText()).isEqualTo("Onların listesi");
        assertThat(theirsNow.get("items")).hasSize(1);
        assertThat(theirsNow.get("items").get(0).get("done").asBoolean()).isFalse();
        assertThat(lists(mine, attacker).getBody().get(0).get("items").get(0).get("done").asBoolean()).isFalse();
    }

    // ── eşzamanlılık ────────────────────────────────────────────────

    @Test
    @DisplayName("İki kişi aynı listeyi aynı anda karıştırır: sıra her zaman 0..n-1, madde kaybolmaz, çoğalmaz")
    void ayniAndaSiralama() throws Exception {
        String ownerEmail = uniqueEmail("liste"), editorEmail = uniqueEmail("editor");
        String owner = registerAndLogin("Sira", "Bir", ownerEmail);
        String editor = registerAndLogin("Sira", "Iki", editorEmail);
        Fixture f = boardWithCard(owner, "Sıra yarışı", "Kart");
        addMember(f.boardId, owner, editorEmail, "EDITOR");
        long listId = createList(f, owner, "Karışık").getBody().get("id").asLong();
        for (int i = 0; i < 8; i++) {
            addItem(f, owner, listId, "Madde " + i);
        }
        List<Long> items = ids(lists(f, owner).getBody().get(0));

        Random random = new Random(42);
        List<Callable<HttpStatus>> moves = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            long item = items.get(random.nextInt(items.size()));
            int to = random.nextInt(items.size());
            String token = i % 2 == 0 ? owner : editor;
            moves.add(() -> (HttpStatus) patchItem(f, token, listId, item, Map.of("position", to)).getStatusCode());
        }
        assertThat(concurrently(moves)).containsOnly(HttpStatus.OK);

        JsonNode after = lists(f, owner).getBody().get(0);
        assertThat(positions(after)).containsExactly(0, 1, 2, 3, 4, 5, 6, 7);
        assertThat(ids(after)).containsExactlyInAnyOrderElementsOf(items);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(DISTINCT position) FROM checklist_items WHERE checklist_id = ?", Long.class, listId))
                .isEqualTo(8);
        // Oluşturma 0, sekiz ekleme 8; yerini gerçekten değiştiren her taşıma bir artırır.
        assertThat(after.get("revision").asLong()).isBetween(9L, 8L + 24);
    }

    @Test
    @DisplayName("Bütün maddeler aynı anda işaretlenir: liste tamamlanır, 'tamamlandı' geçmişe tam bir kez yazılır")
    void ayniAndaIsaretleme() throws Exception {
        String owner = registerAndLogin("Isaret", "Yarisi", uniqueEmail("liste"));
        Fixture f = boardWithCard(owner, "İşaret yarışı", "Kart");
        long listId = createList(f, owner, "Hepsi").getBody().get("id").asLong();
        for (int i = 0; i < 10; i++) {
            addItem(f, owner, listId, "Madde " + i);
        }
        List<Long> items = ids(lists(f, owner).getBody().get(0));

        // Her madde iki kez, aynı anda: işaretleme atamadır, ikinci tıklama bozmaz.
        List<Callable<HttpStatus>> ticks = new ArrayList<>();
        for (long item : items) {
            ticks.add(() -> (HttpStatus) patchItem(f, owner, listId, item, Map.of("done", true)).getStatusCode());
            ticks.add(() -> (HttpStatus) patchItem(f, owner, listId, item, Map.of("done", true)).getStatusCode());
        }
        assertThat(concurrently(ticks)).containsOnly(HttpStatus.OK);

        JsonNode after = lists(f, owner).getBody().get(0);
        after.get("items").forEach(i -> assertThat(i.get("done").asBoolean()).isTrue());
        // Oluşturma 0, on ekleme 10, her madde yalnızca bir kez değişti: 20. Kayıp güncelleme yok.
        assertThat(after.get("revision").asLong()).as("her madde bir kez değişti").isEqualTo(10 + 10);

        List<String> types = types(get("/api/boards/" + f.boardId + "/activity?limit=100", owner));
        assertThat(types.stream().filter(ChecklistService.COMPLETE::equals).count()).isEqualTo(1);
        // İşaretlemelerin kendisi geçmişe yazılmaz.
        assertThat(types).containsExactly(ChecklistService.COMPLETE, ChecklistService.ADD, "ADD_CARD");
    }

    @Test
    @DisplayName("Aynı anda eklenen maddeler sıra numarasında çakışmaz")
    void ayniAndaEkleme() throws Exception {
        String owner = registerAndLogin("Ekleme", "Yarisi", uniqueEmail("liste"));
        Fixture f = boardWithCard(owner, "Ekleme yarışı", "Kart");
        long listId = createList(f, owner, "Kalabalık").getBody().get("id").asLong();

        List<Callable<HttpStatus>> adds = new ArrayList<>();
        IntStream.range(0, 15).forEach(i ->
                adds.add(() -> (HttpStatus) addItem(f, owner, listId, "Madde " + i).getStatusCode()));
        assertThat(concurrently(adds)).containsOnly(HttpStatus.CREATED);

        JsonNode after = lists(f, owner).getBody().get(0);
        assertThat(positions(after)).containsExactlyElementsOf(IntStream.range(0, 15).boxed().toList());
    }

    // ── silme ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Liste silinince maddeleri de gider; kart silinince listeleri ve maddeleri gider")
    void silme() throws Exception {
        String owner = registerAndLogin("Silen", "Kisi", uniqueEmail("liste"));
        Fixture f = boardWithCard(owner, "Silme", "Kart");
        long gone = createList(f, owner, "Gidecek").getBody().get("id").asLong();
        long kept = createList(f, owner, "Kalacak").getBody().get("id").asLong();
        addItem(f, owner, gone, "Bir");
        addItem(f, owner, gone, "İki");
        addItem(f, owner, kept, "Üç");

        assertThat(deleteList(f, owner, gone).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM checklist_items WHERE checklist_id = ?", Long.class, gone))
                .isZero();
        assertThat(lists(f, owner).getBody()).hasSize(1);
        assertThat(addItem(f, owner, gone, "Hayalet").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        op(owner, f.boardId, Map.of("type", "DELETE_CARD", "cardId", f.cardId), "DELETE_CARD");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM checklists WHERE card_id = ?", Long.class, f.cardId))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM checklist_items WHERE checklist_id = ?", Long.class, kept))
                .isZero();
    }

    // ── canlı yayın, geçmiş ─────────────────────────────────────────

    @Test
    @DisplayName("Her değişiklik listenin yeni hâliyle panodaki herkese canlı gider")
    void canliOlaylar() throws Exception {
        String ownerEmail = uniqueEmail("liste"), viewerEmail = uniqueEmail("izleyici");
        String owner = registerAndLogin("Canli", "Yazan", ownerEmail);
        String viewer = registerAndLogin("Canli", "Izleyen", viewerEmail);
        Fixture f = boardWithCard(owner, "Canlı", "Kart");
        addMember(f.boardId, owner, viewerEmail, "VIEWER");
        String topic = "/topic/board." + f.boardId;

        StompTestClient watcher = StompTestClient.connect(wsUrl(), viewer).subscribe(topic);
        StompTestClient.awaitSubscriptions();

        long listId = createList(f, owner, "Canlı liste").getBody().get("id").asLong();
        JsonNode created = awaitType(watcher, topic, "CHECKLIST_UPDATED");
        assertThat(created.get("cardId").asLong()).isEqualTo(f.cardId);
        assertThat(created.get("checklist").get("title").asText()).isEqualTo("Canlı liste");
        assertThat(created.get("logged").asBoolean()).isTrue();

        addItem(f, owner, listId, "Bir");
        JsonNode added = awaitType(watcher, topic, "CHECKLIST_UPDATED");
        assertThat(texts(added.get("checklist"))).containsExactly("Bir");
        assertThat(added.has("logged")).isFalse();   // madde eklemek geçmişe yazılmaz
        long one = itemId(added.get("checklist"), 0);
        addItem(f, owner, listId, "İki");
        long two = itemId(awaitType(watcher, topic, "CHECKLIST_UPDATED").get("checklist"), 1);

        patchItem(f, owner, listId, one, Map.of("done", true));
        assertThat(awaitType(watcher, topic, "CHECKLIST_UPDATED").get("checklist").get("items").get(0)
                .get("done").asBoolean()).isTrue();
        patchItem(f, owner, listId, two, Map.of("position", 0));
        assertThat(ids(awaitType(watcher, topic, "CHECKLIST_UPDATED").get("checklist"))).containsExactly(two, one);
        patchItem(f, owner, listId, two, Map.of("text", "İki (düzeltildi)"));
        assertThat(texts(awaitType(watcher, topic, "CHECKLIST_UPDATED").get("checklist")))
                .containsExactly("İki (düzeltildi)", "Bir");
        renameList(f, owner, listId, "Yeni ad");
        assertThat(awaitType(watcher, topic, "CHECKLIST_UPDATED").get("checklist").get("title").asText())
                .isEqualTo("Yeni ad");

        // Son açık madde işaretlenince liste tamamlanır: bu geçmişe yazılır.
        patchItem(f, owner, listId, two, Map.of("done", true));
        JsonNode completed = awaitType(watcher, topic, "CHECKLIST_UPDATED");
        assertThat(completed.get("logged").asBoolean()).isTrue();

        deleteItem(f, owner, listId, one);
        JsonNode lastRev = awaitType(watcher, topic, "CHECKLIST_UPDATED");
        assertThat(texts(lastRev.get("checklist"))).containsExactly("İki (düzeltildi)");

        deleteList(f, owner, listId);
        JsonNode deleted = awaitType(watcher, topic, "CHECKLIST_DELETED");
        assertThat(deleted.get("checklistId").asLong()).isEqualTo(listId);
        assertThat(deleted.get("cardId").asLong()).isEqualTo(f.cardId);
        assertThat(deleted.has("checklist")).isFalse();
        watcher.disconnect();

        // Geçmişe yalnızca anlamlı anlar yazıldı (izleyicinin katılması testin kurulumundan).
        JsonNode activity = get("/api/boards/" + f.boardId + "/activity", owner);
        assertThat(types(activity)).containsExactly(ChecklistService.DELETE, ChecklistService.COMPLETE,
                ChecklistService.ADD, "MEMBER_JOINED", "ADD_CARD");
        assertThat(activity.get(1).get("params").get("checklist").asText()).isEqualTo("Yeni ad");
        assertThat(activity.get(1).get("description").asText())
                .isEqualTo("'Kart' kartındaki 'Yeni ad' kontrol listesini tamamladı");
    }

    @Test
    @DisplayName("Listeler zaman yolculuğunu bozmaz: çizelgede an değiller, geçmiş görünümünde kartlar listesiz")
    void gecmisBozulmaz() throws Exception {
        String owner = registerAndLogin("Gecmis", "Bakan", uniqueEmail("liste"));
        Fixture f = boardWithCard(owner, "Geçmiş", "Zaman kartı");
        long progress = getBoard(f.boardId, owner).getBody().get("columns").get(1).get("id").asLong();
        op(owner, f.boardId, Map.of("type", "MOVE_CARD", "cardId", f.cardId, "toColumnId", progress,
                "position", 0, "baseVersion", 0), "MOVE_CARD");
        JsonNode before = get("/api/boards/" + f.boardId + "/timeline", owner);
        int transitions = get("/api/boards/" + f.boardId + "/flow", owner).get("measuredTransitions").asInt();

        long listId = createList(f, owner, "Liste").getBody().get("id").asLong();
        long item = addItem(f, owner, listId, "Tek madde").getBody().get("items").get(0).get("id").asLong();
        patchItem(f, owner, listId, item, Map.of("done", true));
        deleteList(f, owner, listId);

        JsonNode after = get("/api/boards/" + f.boardId + "/timeline", owner);
        assertThat(after).hasSize(before.size());
        assertThat(types(after)).doesNotContain(ChecklistService.ADD, ChecklistService.COMPLETE, ChecklistService.DELETE);

        long lastActivity = get("/api/boards/" + f.boardId + "/activity", owner).get(0).get("id").asLong();
        JsonNode past = get("/api/boards/" + f.boardId + "/history?upTo=" + lastActivity, owner);
        JsonNode card = past.get("columns").get(1).get("cards").get(0);
        assertThat(card.get("title").asText()).isEqualTo("Zaman kartı");
        assertThat(card.get("checklists")).isEmpty();
        assertThat(get("/api/boards/" + f.boardId + "/flow", owner).get("measuredTransitions").asInt())
                .isEqualTo(transitions);
    }

    // ── yardımcılar ─────────────────────────────────────────────────

    private record Fixture(long boardId, long columnId, long cardId) {
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

    /** Karta bir etiket ve bir maddeli liste ekler (N+1 sınaması için). */
    private void decorate(Fixture f, String token, long cardId, int i) {
        Fixture card = new Fixture(f.boardId, f.columnId, cardId);
        Map<String, Object> label = Map.of("name", "Etiket " + i, "color", "GREEN");
        long labelId = rest.exchange("/api/boards/" + f.boardId + "/labels", HttpMethod.POST,
                new HttpEntity<>(label, authHeaders(token)), JsonNode.class).getBody().get("id").asLong();
        rest.exchange("/api/boards/" + f.boardId + "/cards/" + cardId + "/labels/" + labelId, HttpMethod.PUT,
                new HttpEntity<>(authHeaders(token)), JsonNode.class);
        long listId = createList(card, token, "Liste " + i).getBody().get("id").asLong();
        addItem(card, token, listId, "Madde " + i);
    }

    /** Panonun fotoğrafı için sunucunun hazırladığı SQL ifadesi sayısı (Hibernate istatistiği). */
    private long statementsFor(long boardId, String token) {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean was = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        try {
            long best = Long.MAX_VALUE;
            for (int i = 0; i < 3; i++) {   // arka planda başka bir sorgu araya girerse en küçüğü al
                stats.clear();
                assertThat(getBoard(boardId, token).getStatusCode()).isEqualTo(HttpStatus.OK);
                best = Math.min(best, stats.getPrepareStatementCount());
            }
            return best;
        } finally {
            stats.setStatisticsEnabled(was);
        }
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

    private String path(Fixture f) {
        return "/api/boards/" + f.boardId + "/cards/" + f.cardId + "/checklists";
    }

    private ResponseEntity<JsonNode> lists(Fixture f, String token) {
        return rest.exchange(path(f), HttpMethod.GET, new HttpEntity<>(authHeaders(token)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> createList(Fixture f, String token, String title) {
        return rest.exchange(path(f), HttpMethod.POST,
                new HttpEntity<>(Map.of("title", title), authHeaders(token)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> renameList(Fixture f, String token, long listId, String title) {
        return rest.exchange(path(f) + "/" + listId, HttpMethod.PATCH,
                new HttpEntity<>(Map.of("title", title), authHeaders(token)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> deleteList(Fixture f, String token, long listId) {
        return rest.exchange(path(f) + "/" + listId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> addItem(Fixture f, String token, long listId, String text) {
        return rest.exchange(path(f) + "/" + listId + "/items", HttpMethod.POST,
                new HttpEntity<>(Map.of("text", text), authHeaders(token)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> patchItem(Fixture f, String token, long listId, long itemId, Map<String, ?> body) {
        return rest.exchange(path(f) + "/" + listId + "/items/" + itemId, HttpMethod.PATCH,
                new HttpEntity<>(body, authHeaders(token)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> deleteItem(Fixture f, String token, long listId, long itemId) {
        return rest.exchange(path(f) + "/" + listId + "/items/" + itemId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)), JsonNode.class);
    }

    private JsonNode get(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(authHeaders(token)), JsonNode.class).getBody();
    }

    private static long itemId(JsonNode checklist, int index) {
        return checklist.get("items").get(index).get("id").asLong();
    }

    private static List<Long> ids(JsonNode checklist) {
        List<Long> out = new ArrayList<>();
        checklist.get("items").forEach(i -> out.add(i.get("id").asLong()));
        return out;
    }

    private static List<String> texts(JsonNode checklist) {
        List<String> out = new ArrayList<>();
        checklist.get("items").forEach(i -> out.add(i.get("text").asText()));
        return out;
    }

    private static List<Integer> positions(JsonNode checklist) {
        List<Integer> out = new ArrayList<>();
        checklist.get("items").forEach(i -> out.add(i.get("position").asInt()));
        return out;
    }

    private static List<String> types(JsonNode rows) {
        List<String> out = new ArrayList<>();
        rows.forEach(r -> out.add(r.get("type").asText()));
        return out;
    }

    /** Verilen işleri ayrı iş parçacıklarında aynı anda başlatır, sonuçları sırayla döner. */
    private static <T> List<T> concurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> out = new ArrayList<>();
            for (Future<T> fut : futures) {
                out.add(fut.get(60, TimeUnit.SECONDS));
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
