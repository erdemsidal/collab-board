package com.collabboard.comment;

import com.collabboard.support.IntegrationTestBase;
import com.collabboard.support.StompTestClient;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kart yorumları ve @bahsetme (ADR 0019).
 *
 * Yorum kartın parçası değil, altındaki konuşma: kartın sürümünü artırmaz,
 * zaman yolculuğunda ve akış ölçümünde oynatılmaz. Testler bunu da sabitler.
 */
class CommentIntegrationTest extends IntegrationTestBase {

    private static final String INBOX = "/user/queue/notifications";

    // ── temel akış ──────────────────────────────────────────────────

    @Test
    @DisplayName("Yorum yazılır, eskiden yeniye listelenir, düzenlenir ve silinir")
    void yorumYasamDongusu() throws Exception {
        String owner = registerAndLogin("Yorum", "Sahibi", uniqueEmail("yorum"));
        Fixture f = boardWithCard(owner, "Yorum Panosu", "Tasarımı bitir");

        ResponseEntity<JsonNode> first = post(f, owner, "  İlk yorum  ", List.of());
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode c1 = first.getBody();
        assertThat(c1.get("body").asText()).isEqualTo("İlk yorum");   // baştaki/sondaki boşluk atılır
        assertThat(c1.get("cardId").asLong()).isEqualTo(f.cardId);
        assertThat(c1.get("author").get("name").asText()).isEqualTo("Yorum Sahibi");
        assertThat(c1.get("author").get("id").asLong()).isEqualTo(userId(owner));
        assertThat(c1.get("mentions")).isEmpty();
        assertThat(c1.get("createdAt").asText()).endsWith("Z");
        assertThat(c1.get("editedAt").isNull()).isTrue();

        long secondId = post(f, owner, "İkinci yorum", List.of()).getBody().get("id").asLong();

        JsonNode list = list(f, owner).getBody();
        assertThat(list).hasSize(2);
        assertThat(list.get(0).get("body").asText()).isEqualTo("İlk yorum");
        assertThat(list.get(1).get("body").asText()).isEqualTo("İkinci yorum");

        ResponseEntity<JsonNode> edited = patch(f, owner, c1.get("id").asLong(), "İlk yorum, düzeltildi", List.of());
        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(edited.getBody().get("body").asText()).isEqualTo("İlk yorum, düzeltildi");
        assertThat(edited.getBody().get("editedAt").isNull()).isFalse();

        assertThat(delete(f, owner, secondId).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        JsonNode after = list(f, owner).getBody();
        assertThat(after).hasSize(1);
        assertThat(after.get(0).get("body").asText()).isEqualTo("İlk yorum, düzeltildi");
    }

    @Test
    @DisplayName("Boş, çok uzun ya da 20'den fazla kişiyi anan yorum 400 alır; mesaj Türkçe")
    void gecersizYorumReddedilir() throws Exception {
        String owner = registerAndLogin("Gecersiz", "Yorum", uniqueEmail("yorum"));
        Fixture f = boardWithCard(owner, "Doğrulama", "Kart");

        ResponseEntity<JsonNode> blank = post(f, owner, "   \n  ", List.of());
        assertThat(blank.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(blank.getBody().get("message").asText()).isEqualTo("Yorum boş olamaz.");

        ResponseEntity<JsonNode> tooLong = post(f, owner, "a".repeat(5001), List.of());
        assertThat(tooLong.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(tooLong.getBody().get("message").asText()).contains("5000");
        assertThat(post(f, owner, "a".repeat(5000), List.of()).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        List<Long> many = LongStream.rangeClosed(1, 21).boxed().toList();
        ResponseEntity<JsonNode> crowd = post(f, owner, "Herkese", many);
        assertThat(crowd.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(crowd.getBody().get("message").asText()).contains("20");

        ResponseEntity<JsonNode> missing = patch(f, owner, 987654321L, "Yok", List.of());
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── yetki ───────────────────────────────────────────────────────

    @Test
    @DisplayName("İzleyici de yorum yazar; üye olmayan panonun yorumlarına 403 alır")
    void izleyiciYazarYabanciGiremez() throws Exception {
        String ownerEmail = uniqueEmail("yorum"), viewerEmail = uniqueEmail("yorum");
        String owner = registerAndLogin("Pano", "Sahibi", ownerEmail);
        String viewer = registerAndLogin("Sadece", "Izleyen", viewerEmail);
        String stranger = registerAndLogin("Yabanci", "Kisi", uniqueEmail("yorum"));
        Fixture f = boardWithCard(owner, "Ortak", "Soru sorulacak kart");
        addMember(f.boardId, owner, viewerEmail, "VIEWER");

        assertThat(post(f, viewer, "Bunu kim yapıyor?", List.of()).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(list(f, viewer).getBody()).hasSize(1);

        assertThat(list(f, stranger).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post(f, stranger, "Merhaba", List.of()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Başka panonun kartı bu panonun adresinden açılamaz: 404")
    void baskaPanonunKarti() throws Exception {
        String owner = registerAndLogin("Iki", "Panolu", uniqueEmail("yorum"));
        Fixture a = boardWithCard(owner, "A Panosu", "A kartı");
        Fixture b = boardWithCard(owner, "B Panosu", "B kartı");

        Fixture crossed = new Fixture(a.boardId, a.columnId, b.cardId);
        assertThat(list(crossed, owner).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post(crossed, owner, "Yanlış yer", List.of()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Yorumu yalnızca yazanı düzenler; silmeyi yazanı ya da pano yöneticisi yapar")
    void duzenlemeVeSilmeYetkisi() throws Exception {
        String ownerEmail = uniqueEmail("yorum"), editorEmail = uniqueEmail("yorum"), viewerEmail = uniqueEmail("yorum");
        String owner = registerAndLogin("Pano", "Yoneticisi", ownerEmail);
        String editor = registerAndLogin("Kart", "Editoru", editorEmail);
        String viewer = registerAndLogin("Kenar", "Izleyici", viewerEmail);
        Fixture f = boardWithCard(owner, "Yetki", "Kart");
        addMember(f.boardId, owner, editorEmail, "EDITOR");
        addMember(f.boardId, owner, viewerEmail, "VIEWER");

        long editorsComment = post(f, editor, "Editörün yorumu", List.of()).getBody().get("id").asLong();
        long secondComment = post(f, editor, "Bir tane daha", List.of()).getBody().get("id").asLong();

        ResponseEntity<JsonNode> foreignEdit = patch(f, owner, editorsComment, "Yönetici değiştirdi", List.of());
        assertThat(foreignEdit.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(foreignEdit.getBody().get("message").asText()).contains("kendi yorumunu");

        assertThat(delete(f, viewer, editorsComment).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(delete(f, owner, editorsComment).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(delete(f, editor, secondComment).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(list(f, owner).getBody()).isEmpty();
    }

    // ── bahsetme ve bildirim ────────────────────────────────────────

    @Test
    @DisplayName("Yalnızca panoya erişen kişi anılır; anılana canlı bildirim ve tek posta gider")
    void bahsetmeBildirimVePosta() throws Exception {
        String ownerEmail = uniqueEmail("yorum"), memberEmail = uniqueEmail("anilan"), strangerEmail = uniqueEmail("yabanci");
        String owner = registerAndLogin("Ayse", "Yilmaz", ownerEmail);
        String member = registerAndLogin("Mehmet", "Kaya", memberEmail);
        String stranger = registerAndLogin("Disaridan", "Biri", strangerEmail);
        Fixture f = boardWithCard(owner, "Pazarlama", "Giriş sayfası");
        addMember(f.boardId, owner, memberEmail, "EDITOR");
        long ownerId = userId(owner), memberId = userId(member), strangerId = userId(stranger);

        StompTestClient inbox = StompTestClient.connect(wsUrl(), member).subscribe(INBOX);
        StompTestClient.awaitSubscriptions();
        mailSender.clear();

        String body = "@Mehmet Kaya metni yarına kadar gözden geçirebilir misin? " + "x".repeat(300);
        ResponseEntity<JsonNode> created = post(f, owner, body, List.of(memberId, strangerId, ownerId, 99_999_999L));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode mentions = created.getBody().get("mentions");
        assertThat(mentions).hasSize(1);
        assertThat(mentions.get(0).get("id").asLong()).isEqualTo(memberId);
        assertThat(mentions.get(0).get("name").asText()).isEqualTo("Mehmet Kaya");

        // Canlı: anılan kişi hangi ekranda olursa olsun haberi alır.
        JsonNode live = inbox.awaitMessage(INBOX).get("notification");
        assertThat(live.get("type").asText()).isEqualTo("COMMENT_MENTION");
        assertThat(live.get("actorName").asText()).isEqualTo("Ayse Yilmaz");
        assertThat(live.get("boardId").asLong()).isEqualTo(f.boardId);
        assertThat(live.get("boardName").asText()).isEqualTo("Pazarlama");
        assertThat(live.get("cardId").asLong()).isEqualTo(f.cardId);
        assertThat(live.get("cardTitle").asText()).isEqualTo("Giriş sayfası");
        inbox.disconnect();

        // Zil menüsünde de duruyor.
        JsonNode bell = notifications(member).get("items").get(0);
        assertThat(bell.get("type").asText()).isEqualTo("COMMENT_MENTION");
        assertThat(bell.get("read").asBoolean()).isFalse();

        // Posta: kısa, kartı açan bağlantıyla, yorumun en fazla 200 karakteri.
        MimeMessage mail = awaitMailTo(memberEmail);
        assertThat(mail.getSubject()).isEqualTo("Ayse Yilmaz seni bir yorumda andı: Giriş sayfası");
        String text = textOf(mail);
        assertThat(text).contains("/?board=" + f.boardId + "&card=" + f.cardId);
        assertThat(text).contains("@Mehmet Kaya metni yarına kadar");
        assertThat(text).doesNotContain("x".repeat(200));
        assertThat(text).contains("…");

        // Anılamayanlara (yabancı, yazanın kendisi) hiçbir şey gitmez.
        Thread.sleep(300);
        assertThat(mailsTo(strangerEmail)).isEmpty();
        assertThat(mailsTo(ownerEmail)).isEmpty();
        assertThat(notifications(stranger).get("items")).isEmpty();
        assertThat(mailsTo(memberEmail)).hasSize(1);
    }

    @Test
    @DisplayName("Düzenleme yalnızca yeni anılana haber verir; aynı kişi aynı yorumda ikinci postayı almaz")
    void duzenlemeYenidenGondermez() throws Exception {
        String ownerEmail = uniqueEmail("yorum"), firstEmail = uniqueEmail("ilk"), laterEmail = uniqueEmail("sonra");
        String owner = registerAndLogin("Yazan", "Kisi", ownerEmail);
        String first = registerAndLogin("Ilk", "Anilan", firstEmail);
        String later = registerAndLogin("Sonra", "Anilan", laterEmail);
        Fixture f = boardWithCard(owner, "Düzenleme", "Kart");
        addMember(f.boardId, owner, firstEmail, "EDITOR");
        addMember(f.boardId, owner, laterEmail, "VIEWER");
        long firstId = userId(first), laterId = userId(later);
        mailSender.clear();

        long commentId = post(f, owner, "@Ilk Anilan bakar mısın?", List.of(firstId)).getBody().get("id").asLong();
        awaitMailTo(firstEmail);

        // Metin düzeltildi, ilk kişi hâlâ anılıyor, yeni biri eklendi.
        ResponseEntity<JsonNode> edited = patch(f, owner, commentId,
                "@Ilk Anilan ve @Sonra Anilan bakar mısınız?", List.of(firstId, laterId));
        assertThat(edited.getBody().get("mentions")).hasSize(2);
        awaitMailTo(laterEmail);

        // İlk kişiyi çıkar, sonra yeniden ekle: yine de ikinci posta yok.
        patch(f, owner, commentId, "Sonra Anilan bakar mısın?", List.of(laterId));
        patch(f, owner, commentId, "@Ilk Anilan tekrar sen bak", List.of(firstId));

        Thread.sleep(400);
        assertThat(mailsTo(firstEmail)).hasSize(1);
        assertThat(mailsTo(laterEmail)).hasSize(1);
        assertThat(countOfType(notifications(first), "COMMENT_MENTION")).isEqualTo(1);
        assertThat(countOfType(notifications(later), "COMMENT_MENTION")).isEqualTo(1);
    }

    @Test
    @DisplayName("Karta atanan kişiye yorumdan uygulama içinde haber gider, posta gitmez")
    void atananKisiyeHaber() throws Exception {
        String ownerEmail = uniqueEmail("yorum"), assigneeEmail = uniqueEmail("atanan"), otherEmail = uniqueEmail("diger");
        String owner = registerAndLogin("Pano", "Sahibi", ownerEmail);
        String assignee = registerAndLogin("Atanan", "Kisi", assigneeEmail);
        String other = registerAndLogin("Diger", "Uye", otherEmail);
        Fixture f = boardWithCard(owner, "Atama", "Raporu yaz");
        addMember(f.boardId, owner, assigneeEmail, "EDITOR");
        addMember(f.boardId, owner, otherEmail, "EDITOR");
        long assigneeId = userId(assignee), otherId = userId(other);
        assign(owner, f, assigneeId);
        mailSender.clear();

        // Anılmadan yazılan yorum: atanan kişiye "kartına yorum geldi".
        post(f, owner, "Taslağı paylaştım.", List.of());
        JsonNode n = notifications(assignee).get("items").get(0);
        assertThat(n.get("type").asText()).isEqualTo("CARD_COMMENT");
        assertThat(n.get("cardId").asLong()).isEqualTo(f.cardId);
        assertThat(n.get("cardTitle").asText()).isEqualTo("Raporu yaz");

        // Atanan kişi anılırsa yalnızca bahsetme bildirimi alır (ikisi birden değil).
        post(f, owner, "@Atanan Kisi son hâline bak", List.of(assigneeId));
        // Kendi yazdığı yorumdan kendisine haber gitmez.
        post(f, assignee, "Baktım, tamam.", List.of());

        JsonNode items = notifications(assignee).get("items");
        assertThat(countOfType(items, "CARD_COMMENT")).isEqualTo(1);
        assertThat(countOfType(items, "COMMENT_MENTION")).isEqualTo(1);

        // Posta yalnızca bahsetmeden: başka birini anıp onun postasını bekliyoruz,
        // ardından atanana giden posta sayısına bakıyoruz.
        post(f, owner, "@Diger Uye sen de bak", List.of(otherId));
        awaitMailTo(otherEmail);
        Thread.sleep(300);
        assertThat(mailsTo(assigneeEmail)).hasSize(1);   // bahsetme postası; atama haberi posta değil
        // Yazan kişiye kendi yorumundan bildirim gitmez.
        assertThat(countOfType(notifications(owner).get("items"), "CARD_COMMENT")).isZero();
    }

    // ── canlı yayın, pano fotoğrafı, geçmiş ─────────────────────────

    @Test
    @DisplayName("Yorum olayları panodaki herkese canlı ulaşır; kartın sürümü değişmez")
    void canliOlaylar() throws Exception {
        String ownerEmail = uniqueEmail("yorum"), viewerEmail = uniqueEmail("yorum");
        String owner = registerAndLogin("Canli", "Yazan", ownerEmail);
        String viewer = registerAndLogin("Canli", "Izleyen", viewerEmail);
        Fixture f = boardWithCard(owner, "Canlı", "Konuşulan kart");
        addMember(f.boardId, owner, viewerEmail, "VIEWER");
        String topic = "/topic/board." + f.boardId;

        StompTestClient watcher = StompTestClient.connect(wsUrl(), viewer).subscribe(topic);
        StompTestClient.awaitSubscriptions();

        long id = post(f, owner, "Canlı yorum", List.of()).getBody().get("id").asLong();
        JsonNode added = awaitType(watcher, topic, "COMMENT_ADDED");
        assertThat(added.get("cardId").asLong()).isEqualTo(f.cardId);
        assertThat(added.get("commentCount").asLong()).isEqualTo(1);
        assertThat(added.get("comment").get("body").asText()).isEqualTo("Canlı yorum");
        assertThat(added.get("comment").get("author").get("name").asText()).isEqualTo("Canli Yazan");

        patch(f, owner, id, "Canlı yorum (düzeltme)", List.of());
        JsonNode edited = awaitType(watcher, topic, "COMMENT_EDITED");
        assertThat(edited.get("comment").get("body").asText()).isEqualTo("Canlı yorum (düzeltme)");
        assertThat(edited.get("commentCount").asLong()).isEqualTo(1);

        delete(f, owner, id);
        JsonNode deleted = awaitType(watcher, topic, "COMMENT_DELETED");
        assertThat(deleted.get("commentId").asLong()).isEqualTo(id);
        assertThat(deleted.get("commentCount").asLong()).isZero();
        assertThat(deleted.has("comment")).isFalse();
        watcher.disconnect();

        // Yorum kartın sürümünü artırmaz: aynı anda kartı taşıyan kimse bayat sayılmaz.
        JsonNode card = getBoard(f.boardId, owner).getBody().get("columns").get(0).get("cards").get(0);
        assertThat(card.get("version").asLong()).isZero();
    }

    @Test
    @DisplayName("Pano fotoğrafında her kartın yorum sayısı var")
    void panoFotografindaYorumSayisi() throws Exception {
        String owner = registerAndLogin("Sayan", "Kisi", uniqueEmail("yorum"));
        Fixture f = boardWithCard(owner, "Sayım", "Konuşulan");
        long quietCard = addCard(owner, f.boardId, f.columnId, "Sessiz");

        post(f, owner, "Bir", List.of());
        post(f, owner, "İki", List.of());

        JsonNode cards = getBoard(f.boardId, owner).getBody().get("columns").get(0).get("cards");
        Map<Long, Long> counts = new HashMap<>();
        cards.forEach(c -> counts.put(c.get("id").asLong(), c.get("commentCount").asLong()));
        assertThat(counts).containsEntry(f.cardId, 2L).containsEntry(quietCard, 0L);
    }

    @Test
    @DisplayName("Yorum son hareketlerde görünür; zaman yolculuğu ve akış ölçümü onu oynatmaz")
    void gecmisVeAkisEtkilenmez() throws Exception {
        String owner = registerAndLogin("Gecmis", "Bakan", uniqueEmail("yorum"));
        Fixture f = boardWithCard(owner, "Geçmiş", "Akan kart");
        long progress = getBoard(f.boardId, owner).getBody().get("columns").get(1).get("id").asLong();
        String topic = "/topic/board." + f.boardId;

        StompTestClient client = StompTestClient.connect(wsUrl(), owner).subscribe(topic);
        StompTestClient.awaitSubscriptions();
        client.send("/app/board/" + f.boardId + "/ops", Map.of("type", "MOVE_CARD", "cardId", f.cardId,
                "toColumnId", progress, "position", 0, "baseVersion", 0));
        client.awaitMessage(topic);
        client.disconnect();

        JsonNode timelineBefore = get("/api/boards/" + f.boardId + "/timeline", owner);
        int transitionsBefore = get("/api/boards/" + f.boardId + "/flow", owner).get("measuredTransitions").asInt();

        post(f, owner, "Taşıdım, bakabilirsiniz.", List.of());
        post(f, owner, "Bir not daha.", List.of());

        JsonNode timelineAfter = get("/api/boards/" + f.boardId + "/timeline", owner);
        assertThat(timelineAfter).hasSize(timelineBefore.size());
        assertThat(timelineAfter.findValuesAsText("type")).doesNotContain(CommentService.ACTIVITY_TYPE);
        assertThat(get("/api/boards/" + f.boardId + "/flow", owner).get("measuredTransitions").asInt())
                .isEqualTo(transitionsBefore);

        // Son hareketlerde görünür.
        JsonNode activity = get("/api/boards/" + f.boardId + "/activity", owner);
        assertThat(activity.get(0).get("type").asText()).isEqualTo(CommentService.ACTIVITY_TYPE);
        assertThat(activity.get(0).get("description").asText()).isEqualTo("'Akan kart' kartına yorum yazdı");

        // Yorumdan sonraki bir an istense bile pano o anki hâliyle kurulur.
        long lastActivity = activity.get(0).get("id").asLong();
        JsonNode past = get("/api/boards/" + f.boardId + "/history?upTo=" + lastActivity, owner);
        assertThat(past.get("columns").get(1).get("cards").get(0).get("title").asText()).isEqualTo("Akan kart");
    }

    @Test
    @DisplayName("Çok hızlı yorum yazan kişi 429 alır; sınır kişi başına")
    void hizSiniri() throws Exception {
        String calmEmail = uniqueEmail("sakin");
        String fast = registerAndLogin("Hizli", "Yazan", uniqueEmail("hizli"));
        String calm = registerAndLogin("Sakin", "Yazan", calmEmail);
        Fixture f = boardWithCard(fast, "Hız", "Kart");
        addMember(f.boardId, fast, calmEmail, "EDITOR");

        for (int i = 1; i <= CommentRateLimiter.PER_MINUTE; i++) {
            assertThat(post(f, fast, "Yorum " + i, List.of()).getStatusCode())
                    .as("%d. yorum sınırın altında", i).isEqualTo(HttpStatus.CREATED);
        }
        ResponseEntity<JsonNode> blocked = post(f, fast, "Bir tane daha", List.of());
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(blocked.getHeaders().getFirst("Retry-After")).isEqualTo("60");
        assertThat(blocked.getBody().get("message").asText()).contains("Çok hızlı yorum");

        // Başka bir kişinin kotası ayrı.
        assertThat(post(f, calm, "Ben yazabiliyorum", List.of()).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    // ── yardımcılar ─────────────────────────────────────────────────

    private record Fixture(long boardId, long columnId, long cardId) {
    }

    /** Pano açar ve ilk kolonuna bir kart ekler (gerçek WebSocket operasyonuyla). */
    private Fixture boardWithCard(String token, String boardName, String cardTitle) throws Exception {
        JsonNode board = createBoard(token, boardName);
        long boardId = board.get("id").asLong();
        long columnId = board.get("columns").get(0).get("id").asLong();
        return new Fixture(boardId, columnId, addCard(token, boardId, columnId, cardTitle));
    }

    private long addCard(String token, long boardId, long columnId, String title) throws Exception {
        String topic = "/topic/board." + boardId;
        StompTestClient client = StompTestClient.connect(wsUrl(), token).subscribe(topic);
        StompTestClient.awaitSubscriptions();
        client.send("/app/board/" + boardId + "/ops", Map.of("type", "ADD_CARD", "columnId", columnId, "title", title));
        long cardId = awaitType(client, topic, "ADD_CARD").get("card").get("id").asLong();
        client.disconnect();
        return cardId;
    }

    private void assign(String token, Fixture f, long assigneeId) throws Exception {
        String topic = "/topic/board." + f.boardId;
        StompTestClient client = StompTestClient.connect(wsUrl(), token).subscribe(topic);
        StompTestClient.awaitSubscriptions();
        Map<String, Object> op = new HashMap<>();
        op.put("type", "EDIT_CARD");
        op.put("cardId", f.cardId);
        op.put("title", getBoard(f.boardId, token).getBody().get("columns").get(0).get("cards").get(0).get("title").asText());
        op.put("assigneeId", assigneeId);
        op.put("baseVersion", 0);
        client.send("/app/board/" + f.boardId + "/ops", op);
        awaitType(client, topic, "EDIT_CARD");
        client.disconnect();
    }

    private String commentsPath(Fixture f) {
        return "/api/boards/" + f.boardId + "/cards/" + f.cardId + "/comments";
    }

    private ResponseEntity<JsonNode> post(Fixture f, String token, String body, List<Long> mentions) {
        return rest.exchange(commentsPath(f), HttpMethod.POST,
                new HttpEntity<>(Map.of("body", body, "mentions", mentions), authHeaders(token)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> list(Fixture f, String token) {
        return rest.exchange(commentsPath(f), HttpMethod.GET, new HttpEntity<>(authHeaders(token)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> patch(Fixture f, String token, long commentId, String body, List<Long> mentions) {
        return rest.exchange(commentsPath(f) + "/" + commentId, HttpMethod.PATCH,
                new HttpEntity<>(Map.of("body", body, "mentions", mentions), authHeaders(token)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> delete(Fixture f, String token, long commentId) {
        return rest.exchange(commentsPath(f) + "/" + commentId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)), JsonNode.class);
    }

    private JsonNode get(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(authHeaders(token)), JsonNode.class).getBody();
    }

    private JsonNode notifications(String token) {
        return get("/api/notifications", token);
    }

    private long userId(String token) {
        return get("/api/users/me", token).get("id").asLong();
    }

    private static long countOfType(JsonNode notificationsOrItems, String type) {
        JsonNode items = notificationsOrItems.has("items") ? notificationsOrItems.get("items") : notificationsOrItems;
        List<String> types = new ArrayList<>();
        items.forEach(n -> types.add(n.get("type").asText()));
        return types.stream().filter(type::equals).count();
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

    private List<MimeMessage> mailsTo(String email) {
        return mailSender.sent().stream().filter(m -> {
            try {
                return Arrays.stream(m.getAllRecipients())
                        .anyMatch(a -> ((InternetAddress) a).getAddress().equalsIgnoreCase(email));
            } catch (Exception e) {
                return false;
            }
        }).toList();
    }

    /** Posta asenkron gider; gelene kadar kısa aralıklarla bakar. */
    private MimeMessage awaitMailTo(String email) throws InterruptedException {
        for (int i = 0; i < 250 && mailsTo(email).isEmpty(); i++) {
            Thread.sleep(20);
        }
        List<MimeMessage> mails = mailsTo(email);
        assertThat(mails).as("%s adresine posta gitmeli", email).isNotEmpty();
        return mails.get(mails.size() - 1);
    }

    /** Postanın düz metin gövdesi (çok parçalı mesajın parçaları çözülerek). */
    private String textOf(Part part) throws Exception {
        Object content = part.getContent();
        if (content instanceof String text) {
            return part.isMimeType("text/plain") ? text : "";
        }
        if (content instanceof Multipart multipart) {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart child = multipart.getBodyPart(i);
                out.append(textOf(child));
            }
            return out.toString();
        }
        return "";
    }
}
