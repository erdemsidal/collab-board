package com.collabboard.chathook;

import com.collabboard.support.IntegrationTestBase;
import com.collabboard.support.StompTestClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.collabboard.chathook.LocalWebhookReceiver.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kanal bildirimleri (ADR 0023): Slack, Teams ve Discord'a gelen webhook ile kısa mesaj.
 *
 * Kanalların yerine yerel bir alıcı (LocalWebhookReceiver) çalışır; test ayarları
 * (IntegrationTestBase) izin listesini yalnızca ona açar. Kartlar gerçek canlı
 * kanal (STOMP) operasyonlarıyla eklenip taşınır; mesajlar işlem kaydedildikten
 * sonra arka planda gittiği için testler alıcıyı bekleyerek doğrular.
 */
class ChatHookIntegrationTest extends IntegrationTestBase {

    private static LocalWebhookReceiver receiver;
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    JdbcTemplate jdbc;

    @BeforeAll
    static void startReceiver() throws Exception {
        receiver = LocalWebhookReceiver.start();
    }

    @AfterAll
    static void stopReceiver() {
        receiver.close();
    }

    // ── mesaj biçimleri ─────────────────────────────────────────────

    @Test
    @DisplayName("Slack: text ve blocks; başlıktaki <!channel> kimseyi dürtmez, bağlantı kartı açar")
    void slackMesaji() throws Exception {
        String owner = registerAndLogin("Ayse", "Yilmaz", uniqueEmail("kanal"));
        Fixture f = board(owner, "Web Sitesi");
        String path = slackPath();
        assertThat(createHook(owner, f.boardId, hook("SLACK", receiver.url(path), "#genel",
                List.of("CARD_CREATED"), "tr")).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        long cardId = addCard(owner, f, "<!channel> & <@U123> yayına al");
        LocalWebhookReceiver.Hit hit = receiver.await(path, 1);
        assertThat(hit.contentType()).startsWith("application/json");
        assertThat(hit.userAgent()).startsWith("Sturnio");

        JsonNode body = JSON.readTree(hit.body());
        String text = body.get("text").asText();
        assertThat(text).isEqualTo("Ayse Yilmaz, “&lt;!channel&gt; &amp; &lt;@U123&gt; yayına al” kartını To Do kolonuna ekledi · Web Sitesi");
        assertThat(hit.body()).doesNotContain("<!channel>").doesNotContain("<@U123>");

        JsonNode section = body.get("blocks").get(0);
        assertThat(section.get("type").asText()).isEqualTo("section");
        assertThat(section.get("text").get("type").asText()).isEqualTo("mrkdwn");
        assertThat(section.get("text").get("text").asText())
                .isEqualTo(text + "\n<http://localhost:8080/?board=" + f.boardId + "&amp;card=" + cardId + "|Kartı aç>");
        assertThat(body.get("unfurl_links").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("Discord: content ve boş allowed_mentions; @everyone ve biçim karakterleri etkisiz, İngilizce")
    void discordMesaji() throws Exception {
        String owner = registerAndLogin("Deniz", "Arslan", uniqueEmail("kanal"));
        Fixture f = board(owner, "Launch");
        String path = discordPath();
        createHook(owner, f.boardId, hook("DISCORD", receiver.url(path), "dev", List.of("COMMENT_ADDED"), "en"));

        long cardId = addCard(owner, f, "@everyone **urgent** [click](https://evil.example)");
        assertThat(postComment(owner, f.boardId, cardId, "Bakıyorum").getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode body = JSON.readTree(receiver.await(path, 1).body());
        assertThat(body.get("allowed_mentions").get("parse")).isEmpty();
        assertThat(body.get("flags").asInt()).isEqualTo(4);
        String content = body.get("content").asText();
        assertThat(content).startsWith("Deniz Arslan commented on “@everyone \\*\\*urgent\\*\\* "
                + "\\[click\\]\\(https://evil.example\\)” · To Do · Launch\n");
        assertThat(content).endsWith("[Open card](http://localhost:8080/?board=" + f.boardId + "&card=" + cardId + ")");
        assertThat(content).doesNotContain("Bakıyorum");   // yorumun metni kanala gitmez
    }

    @Test
    @DisplayName("Teams: Adaptive Card 1.4 (sarılan metin ve \"Kartı aç\"); atama ve kendini atama")
    void teamsMesaji() throws Exception {
        String ownerEmail = uniqueEmail("kanal"), memberEmail = uniqueEmail("uye");
        String owner = registerAndLogin("Ayse", "Yilmaz", ownerEmail);
        String member = registerAndLogin("Mehmet", "Kaya", memberEmail);
        Fixture f = board(owner, "Pazarlama");
        addMember(f.boardId, owner, memberEmail, "EDITOR");
        String path = teamsPath();
        createHook(owner, f.boardId, hook("TEAMS", receiver.url(path), null, List.of("CARD_ASSIGNED"), "tr"));

        long cardId = addCard(owner, f, "Rapor **taslağı** [yaz](https://evil.example)");
        editCard(owner, f, cardId, "Rapor **taslağı** [yaz](https://evil.example)", userId(member));
        JsonNode body = JSON.readTree(receiver.await(path, 1).body());

        assertThat(body.get("type").asText()).isEqualTo("message");
        JsonNode attachment = body.get("attachments").get(0);
        assertThat(attachment.get("contentType").asText()).isEqualTo("application/vnd.microsoft.card.adaptive");
        JsonNode card = attachment.get("content");
        assertThat(card.get("type").asText()).isEqualTo("AdaptiveCard");
        assertThat(card.get("version").asText()).isEqualTo("1.4");
        JsonNode block = card.get("body").get(0);
        assertThat(block.get("type").asText()).isEqualTo("TextBlock");
        assertThat(block.get("wrap").asBoolean()).isTrue();
        assertThat(block.get("text").asText()).isEqualTo("Ayse Yilmaz, “Rapor \\*\\*taslağı\\*\\* \\[yaz\\](https://evil.example)” "
                + "kartına Mehmet Kaya kullanıcısını atadı · To Do · Pazarlama");
        JsonNode action = card.get("actions").get(0);
        assertThat(action.get("type").asText()).isEqualTo("Action.OpenUrl");
        assertThat(action.get("title").asText()).isEqualTo("Kartı aç");
        assertThat(action.get("url").asText()).isEqualTo("http://localhost:8080/?board=" + f.boardId + "&card=" + cardId);

        // Başlığı düzeltmek (atanan aynı) yeni bir "atadı" mesajı değildir.
        editCard(owner, f, cardId, "Rapor taslağı", userId(member));
        // Kişi kartı kendisi üstlenirse: "üstlendi".
        long second = addCard(owner, f, "Sunum");
        editCard(owner, f, second, "Sunum", userId(owner));
        String text = JSON.readTree(receiver.await(path, 2).body())
                .get("attachments").get(0).get("content").get("body").get(0).get("text").asText();
        assertThat(text).isEqualTo("Ayse Yilmaz, “Sunum” kartını üstlendi · To Do · Pazarlama");
        Thread.sleep(400);
        assertThat(receiver.hits(path)).hasSize(2);
    }

    // ── son kolon kuralı ────────────────────────────────────────────

    @Test
    @DisplayName("\"Bitti\" mesajı yalnızca kart son kolona GİRERKEN gider; o kolonda sıralama değiştirmek mesaj değil")
    void sonKolonKurali() throws Exception {
        String owner = registerAndLogin("Akis", "Izleyen", uniqueEmail("kanal"));
        Fixture f = board(owner, "Akış");
        String donePath = slackPath(), createdPath = slackPath();
        createHook(owner, f.boardId, hook("SLACK", receiver.url(donePath), "bitti", List.of("CARD_DONE"), "tr"));
        createHook(owner, f.boardId, hook("SLACK", receiver.url(createdPath), "yeni", List.of("CARD_CREATED"), "tr"));

        long cardId = addCard(owner, f, "Faturayı kes");
        long other = addCard(owner, f, "Diğer iş");
        long progress = f.columnIds.get(1), done = f.columnIds.get(2);

        moveCard(owner, f, cardId, progress, 0);   // ara kolon: mesaj yok
        moveCard(owner, f, cardId, done, 0);       // son kolona girdi: mesaj
        LocalWebhookReceiver.Hit first = receiver.await(donePath, 1);
        assertThat(JSON.readTree(first.body()).get("text").asText())
                .isEqualTo("Akis Izleyen, “Faturayı kes” kartını Done kolonuna taşıdı · Akış");

        moveCard(owner, f, other, done, 1);        // başka bir kart da girdi: mesaj
        moveCard(owner, f, cardId, done, 1);       // son kolonun içinde sıralama: mesaj yok
        moveCard(owner, f, cardId, progress, 0);   // geri çıktı: mesaj yok
        moveCard(owner, f, cardId, done, 0);       // yeniden girdi: mesaj
        receiver.await(donePath, 3);
        Thread.sleep(500);
        assertThat(receiver.hits(donePath)).hasSize(3);
        // Yalnızca "yeni kart" dinleyen bağlantı taşımaları duymaz.
        assertThat(receiver.hits(createdPath)).hasSize(2);
    }

    // ── gizlilik: adres saklanmaz, geri dönmez ──────────────────────

    @Test
    @DisplayName("Adres veritabanında şifreli; hiçbir yanıtta yok, yalnızca son 4 karakter")
    void adresSifreliVeGeriDonmez() throws Exception {
        String owner = registerAndLogin("Gizli", "Adres", uniqueEmail("kanal"));
        Fixture f = board(owner, "Gizlilik");
        String secret = "GizliJeton" + UUID.randomUUID().toString().replace("-", "");
        String path = "/slack/T0001/B0002/" + secret;
        String url = receiver.url(path);

        ResponseEntity<String> created = rest.exchange(hooksPath(f.boardId), HttpMethod.POST,
                new HttpEntity<>(hook("SLACK", url, "#gizli", List.of("CARD_CREATED"), "tr"), authHeaders(owner)),
                String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).doesNotContain(secret).doesNotContain("/slack/");
        JsonNode hook = JSON.readTree(created.getBody());
        assertThat(hook.get("urlHint").asText()).isEqualTo(secret.substring(secret.length() - 4));
        assertThat(hook.has("url")).isFalse();
        long hookId = hook.get("id").asLong();

        String stored = jdbc.queryForObject("SELECT url_encrypted FROM board_chat_hooks WHERE id = ?", String.class, hookId);
        assertThat(stored).doesNotContain(secret).doesNotContain("localhost").doesNotContain("/slack/");
        assertThat(jdbc.queryForObject("SELECT url_hint FROM board_chat_hooks WHERE id = ?", String.class, hookId))
                .hasSize(4);

        String list = rest.exchange(hooksPath(f.boardId), HttpMethod.GET, new HttpEntity<>(authHeaders(owner)),
                String.class).getBody();
        assertThat(list).contains("#gizli").doesNotContain(secret);
        String patched = rest.exchange(hooksPath(f.boardId) + "/" + hookId, HttpMethod.PATCH,
                new HttpEntity<>(Map.of("label", "#yeni-ad"), authHeaders(owner)), String.class).getBody();
        assertThat(patched).contains("#yeni-ad").doesNotContain(secret);
        String tested = rest.exchange(hooksPath(f.boardId) + "/" + hookId + "/test", HttpMethod.POST,
                new HttpEntity<>(authHeaders(owner)), String.class).getBody();
        assertThat(tested).contains("\"delivered\":true").doesNotContain(secret);

        // Yine de gönderim gerçek adrese gider.
        addCard(owner, f, "Şifreli kanala");
        receiver.await(path, 2);
    }

    // ── izin listesi (SSRF) ─────────────────────────────────────────

    @Test
    @DisplayName("İzin listesi dışındaki adres kaydedilmez ve denenmez; yönlendirme izlenmez")
    void izinListesi() throws Exception {
        String owner = registerAndLogin("Izin", "Listesi", uniqueEmail("kanal"));
        Fixture f = board(owner, "SSRF");

        for (String bad : List.of(
                "http://169.254.169.254/latest/meta-data/iam/security-credentials/",
                receiver.url("/admin"),                              // localhost ama izinli yol değil
                "http://hooks.slack.com/services/T0001/B0002/abc",   // https değil, üstelik yanlış sunucu
                "https://evil.example/slack/T0001/B0002/abc",        // yanlış sunucu
                "http://localhost:" + receiver.port() + "@evil.example/slack/x")) {
            ResponseEntity<JsonNode> saved = createHook(owner, f.boardId, hook("SLACK", bad, null, List.of("CARD_CREATED"), "tr"));
            assertThat(saved.getStatusCode()).as(bad).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(saved.getBody().get("message").asText()).isEqualTo(
                    "Bu bir Slack webhook adresine benzemiyor. Adresi uygulamadan yeniden kopyala.");
            ResponseEntity<JsonNode> tried = testUrl(owner, f.boardId, "SLACK", bad);
            assertThat(tried.getStatusCode()).as(bad).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        // Slack'in adresi Discord diye kaydedilemez.
        assertThat(createHook(owner, f.boardId, hook("DISCORD", receiver.url(slackPath()), null,
                List.of("CARD_CREATED"), "tr")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM board_chat_hooks WHERE board_id = ?", Long.class,
                f.boardId)).isZero();
        assertThat(receiver.hits("/admin")).isEmpty();

        // Yönlendirme: izinli adres başka bir yere yönlendirse bile izlenmez.
        String path = slackPath(), elsewhere = slackPath();
        receiver.redirect(path, receiver.url(elsewhere));
        JsonNode result = testUrl(owner, f.boardId, "SLACK", receiver.url(path)).getBody();
        assertThat(result.get("delivered").asBoolean()).isFalse();
        assertThat(result.get("outcome").asText()).isEqualTo("REDIRECT");
        assertThat(result.get("status").asInt()).isEqualTo(302);

        long hookId = createHook(owner, f.boardId, hook("SLACK", receiver.url(path), null, List.of("CARD_CREATED"), "tr"))
                .getBody().get("id").asLong();
        addCard(owner, f, "Yönlendirilen");
        waitUntil(() -> failureCount(hookId) == 1, "yönlendirme başarısız sayılmalı");
        assertThat(receiver.hits(elsewhere)).isEmpty();
    }

    // ── yetki ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Yalnızca pano yöneticisi: editör, izleyici ve yabancı 403; başka panonun bağlantısı 404")
    void yetki() throws Exception {
        String ownerEmail = uniqueEmail("kanal"), editorEmail = uniqueEmail("editor"), viewerEmail = uniqueEmail("izleyici");
        String owner = registerAndLogin("Pano", "Sahibi", ownerEmail);
        String editor = registerAndLogin("Kart", "Editoru", editorEmail);
        String viewer = registerAndLogin("Sadece", "Bakan", viewerEmail);
        String stranger = registerAndLogin("Yabanci", "Kisi", uniqueEmail("yabanci"));
        Fixture f = board(owner, "Yetki");
        addMember(f.boardId, owner, editorEmail, "EDITOR");
        addMember(f.boardId, owner, viewerEmail, "VIEWER");
        long hookId = createHook(owner, f.boardId, hook("SLACK", receiver.url(slackPath()), null,
                List.of("CARD_CREATED"), "tr")).getBody().get("id").asLong();

        for (String token : List.of(editor, viewer, stranger)) {
            assertThat(call(token, HttpMethod.GET, hooksPath(f.boardId), null)).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(call(token, HttpMethod.POST, hooksPath(f.boardId),
                    hook("SLACK", receiver.url(slackPath()), null, List.of("CARD_CREATED"), "tr"))).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(call(token, HttpMethod.PATCH, hooksPath(f.boardId) + "/" + hookId, Map.of("label", "x")))
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(call(token, HttpMethod.DELETE, hooksPath(f.boardId) + "/" + hookId, null)).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(call(token, HttpMethod.POST, hooksPath(f.boardId) + "/" + hookId + "/test", null))
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(call(token, HttpMethod.POST, hooksPath(f.boardId) + "/test",
                    Map.of("provider", "SLACK", "url", receiver.url(slackPath())))).isEqualTo(HttpStatus.FORBIDDEN);
        }

        // Başka bir panonun yöneticisi bu bağlantıya kendi panosunun adresinden ulaşamaz.
        Fixture mine = board(stranger, "Yabancının panosu");
        assertThat(call(stranger, HttpMethod.PATCH, hooksPath(mine.boardId) + "/" + hookId, Map.of("label", "x")))
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(stranger, HttpMethod.DELETE, hooksPath(mine.boardId) + "/" + hookId, null))
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(stranger, HttpMethod.POST, hooksPath(mine.boardId) + "/" + hookId + "/test", null))
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT label FROM board_chat_hooks WHERE id = ?", String.class, hookId)).isNull();

        // Yönetici her şeyi yapar.
        assertThat(call(owner, HttpMethod.DELETE, hooksPath(f.boardId) + "/" + hookId, null)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(listHooks(owner, f.boardId).get("hooks")).isEmpty();
    }

    // ── hatalar: durdurma ve yöneticiye haber ───────────────────────

    @Test
    @DisplayName("404 bağlantıyı hemen durdurur, yöneticilere haber gider; durmuş bağlantıya mesaj gitmez, yeniden açılır")
    void bulunamayanAdresDurdurur() throws Exception {
        String ownerEmail = uniqueEmail("kanal"), coOwnerEmail = uniqueEmail("ortak"), editorEmail = uniqueEmail("editor");
        String owner = registerAndLogin("Birinci", "Yonetici", ownerEmail);
        String coOwner = registerAndLogin("Ikinci", "Yonetici", coOwnerEmail);
        String editor = registerAndLogin("Sade", "Editor", editorEmail);
        Fixture f = board(owner, "Durdurma");
        addMember(f.boardId, owner, coOwnerEmail, "OWNER");
        addMember(f.boardId, owner, editorEmail, "EDITOR");

        String gone = slackPath(), forbidden = discordPath();
        receiver.respond(gone, 404);
        receiver.respond(forbidden, 403);
        long goneId = createHook(owner, f.boardId, hook("SLACK", receiver.url(gone), "#genel",
                List.of("CARD_CREATED"), "tr")).getBody().get("id").asLong();
        long forbiddenId = createHook(owner, f.boardId, hook("DISCORD", receiver.url(forbidden), null,
                List.of("CARD_CREATED"), "tr")).getBody().get("id").asLong();

        addCard(owner, f, "İlk kart");
        waitUntil(() -> "GONE".equals(reason(goneId)) && "FORBIDDEN".equals(reason(forbiddenId)),
                "404 ve 403 bağlantıyı durdurmalı");

        JsonNode listed = listHooks(owner, f.boardId).get("hooks");
        JsonNode first = listed.get(0);
        assertThat(first.get("active").asBoolean()).isFalse();
        assertThat(first.get("disabledReason").asText()).isEqualTo("GONE");

        // Panonun iki yöneticisine de haber, editöre değil.
        for (String token : List.of(owner, coOwner)) {
            waitUntil(() -> chatNotifications(token).size() == 2, "yöneticiye iki haber gitmeli");
            List<JsonNode> items = chatNotifications(token);
            assertThat(items).extracting(n -> n.get("detail").asText())
                    .containsExactlyInAnyOrder("Slack · #genel", "Discord");
            assertThat(items.get(0).get("boardId").asLong()).isEqualTo(f.boardId);
            assertThat(items.get(0).get("boardName").asText()).isEqualTo("Durdurma");
        }
        assertThat(chatNotifications(editor)).isEmpty();

        // Durmuş bağlantıya artık mesaj gitmez.
        addCard(owner, f, "İkinci kart");
        Thread.sleep(500);
        assertThat(receiver.hits(gone)).hasSize(1);

        // "Yeniden aç": adres düzeldiyse mesajlar yeniden akar.
        receiver.respond(gone, 200);
        JsonNode reopened = rest.exchange(hooksPath(f.boardId) + "/" + goneId, HttpMethod.PATCH,
                new HttpEntity<>(Map.of("active", true), authHeaders(owner)), JsonNode.class).getBody();
        assertThat(reopened.get("active").asBoolean()).isTrue();
        assertThat(reopened.has("disabledReason") && !reopened.get("disabledReason").isNull()).isFalse();
        addCard(owner, f, "Üçüncü kart");
        receiver.await(gone, 2);
        waitUntil(() -> lastSentAt(goneId) != null, "başarılı gönderim kaydedilmeli");
    }

    @Test
    @DisplayName("Geçici hatalar sayılır, başarı sayacı sıfırlar; üst üste 5 hatada bağlantı durur")
    void besHatadaDurur() throws Exception {
        String owner = registerAndLogin("Hata", "Sayan", uniqueEmail("kanal"));
        Fixture f = board(owner, "Hatalar");
        String path = slackPath();
        receiver.respond(path, 500);
        long hookId = createHook(owner, f.boardId, hook("SLACK", receiver.url(path), "#ops",
                List.of("CARD_CREATED"), "tr")).getBody().get("id").asLong();

        addCard(owner, f, "Bir");
        addCard(owner, f, "İki");
        waitUntil(() -> failureCount(hookId) == 2, "iki hata sayılmalı");

        receiver.respond(path, 200);
        addCard(owner, f, "Üç");
        waitUntil(() -> failureCount(hookId) == 0 && lastSentAt(hookId) != null, "başarı sayacı sıfırlamalı");

        receiver.respond(path, 503);
        for (int i = 1; i <= ChatDelivery.FAILURE_LIMIT; i++) {
            addCard(owner, f, "Hata " + i);
        }
        waitUntil(() -> "FAILING".equals(reason(hookId)), "5 hatada durmalı");
        assertThat(failureCount(hookId)).isEqualTo(5);
        waitUntil(() -> chatNotifications(owner).size() == 1, "yöneticiye haber gitmeli");
        assertThat(chatNotifications(owner).get(0).get("detail").asText()).isEqualTo("Slack · #ops");
    }

    @Test
    @DisplayName("Yavaş ya da düşmüş kanal kişinin işlemini yavaşlatmaz ve bozmaz (zaman aşımı 5 sn)")
    void yavasKanalBeklemez() throws Exception {
        String owner = registerAndLogin("Hizli", "Kisi", uniqueEmail("kanal"));
        Fixture f = board(owner, "Yavaş kanal");
        String path = slackPath();
        receiver.delay(path, 8_000);
        long hookId = createHook(owner, f.boardId, hook("SLACK", receiver.url(path), null,
                List.of("CARD_CREATED", "COMMENT_ADDED"), "tr")).getBody().get("id").asLong();

        long started = System.currentTimeMillis();
        long cardId = addCard(owner, f, "Hemen görünmeli");
        assertThat(System.currentTimeMillis() - started).as("kart eklemek kanalı beklememeli").isLessThan(2_000);

        started = System.currentTimeMillis();
        assertThat(postComment(owner, f.boardId, cardId, "Yorum da beklemez").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(System.currentTimeMillis() - started).as("yorum yazmak kanalı beklememeli").isLessThan(2_000);

        JsonNode cards = getBoard(f.boardId, owner).getBody().get("columns").get(0).get("cards");
        assertThat(cards).hasSize(1);
        // Kanal cevap vermeyince gönderim zaman aşımına düşer ve geçici hata sayılır.
        waitUntil(() -> failureCount(hookId) == 2, "zaman aşımları sayılmalı");
        assertThat(reason(hookId)).isNull();
    }

    // ── deneme mesajı ve doğrulama ──────────────────────────────────

    @Test
    @DisplayName("Deneme mesajı: kaydetmeden önce ve sonra; bağlantının durumunu değiştirmez")
    void denemeMesaji() throws Exception {
        String owner = registerAndLogin("Deneme", "Yapan", uniqueEmail("kanal"));
        Fixture f = board(owner, "Ürün");
        String path = slackPath();

        JsonNode result = testUrl(owner, f.boardId, "SLACK", receiver.url(path)).getBody();
        assertThat(result.get("delivered").asBoolean()).isTrue();
        assertThat(result.get("outcome").asText()).isEqualTo("OK");
        assertThat(result.get("status").asInt()).isEqualTo(200);
        JsonNode body = JSON.readTree(receiver.await(path, 1).body());
        assertThat(body.get("text").asText())
                .isEqualTo("Sturnio bu kanala bağlandı. “Ürün” panosunda seçilen olaylar buraya gelecek.");
        assertThat(body.get("blocks").get(0).get("text").get("text").asText())
                .endsWith("<http://localhost:8080/?board=" + f.boardId + "|Panoyu aç>");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM board_chat_hooks WHERE board_id = ?", Long.class,
                f.boardId)).isZero();

        // İngilizce, Teams biçiminde.
        String teams = teamsPath();
        rest.exchange(hooksPath(f.boardId) + "/test", HttpMethod.POST, new HttpEntity<>(
                Map.of("provider", "TEAMS", "url", receiver.url(teams), "locale", "en"), authHeaders(owner)), JsonNode.class);
        JsonNode card = JSON.readTree(receiver.await(teams, 1).body()).get("attachments").get(0).get("content");
        assertThat(card.get("body").get(0).get("text").asText())
                .isEqualTo("Sturnio is connected to this channel. Selected events from “Ürün” will show up here.");
        assertThat(card.get("actions").get(0).get("title").asText()).isEqualTo("Open board");

        // Kayıtlı bağlantıyı denemek durumunu değiştirmez: 500 dönse de sayaç artmaz.
        receiver.respond(path, 500);
        long hookId = createHook(owner, f.boardId, hook("SLACK", receiver.url(path), null, List.of("CARD_DONE"), "tr"))
                .getBody().get("id").asLong();
        JsonNode saved = rest.exchange(hooksPath(f.boardId) + "/" + hookId + "/test", HttpMethod.POST,
                new HttpEntity<>(authHeaders(owner)), JsonNode.class).getBody();
        assertThat(saved.get("delivered").asBoolean()).isFalse();
        assertThat(saved.get("outcome").asText()).isEqualTo("SERVER_ERROR");
        assertThat(failureCount(hookId)).isZero();
    }

    @Test
    @DisplayName("Doğrulama: en fazla 5 kanal, en az bir olay, kısa ad, desteklenen dil; uygulama adres olmadan değişmez")
    void dogrulama() throws Exception {
        String owner = registerAndLogin("Kural", "Koyan", uniqueEmail("kanal"));
        Fixture f = board(owner, "Kurallar");

        assertThat(message(createHook(owner, f.boardId, hook("SLACK", receiver.url(slackPath()), null, List.of(), "tr"))))
                .isEqualTo("En az bir olay seç.");
        assertThat(message(createHook(owner, f.boardId, hook("SLACK", receiver.url(slackPath()), "x".repeat(61),
                List.of("CARD_CREATED"), "tr")))).isEqualTo("Ad en fazla 60 karakter olabilir.");
        assertThat(createHook(owner, f.boardId, hook("SLACK", receiver.url(slackPath()), null,
                List.of("CARD_CREATED"), "de")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(message(createHook(owner, f.boardId, hook(null, receiver.url(slackPath()), null,
                List.of("CARD_CREATED"), "tr")))).isEqualTo("Bir uygulama seç: Slack, Microsoft Teams ya da Discord.");
        assertThat(message(createHook(owner, f.boardId, hook("SLACK", "  ", null, List.of("CARD_CREATED"), "tr"))))
                .isEqualTo("Webhook adresini yapıştır.");

        // Dil seçilmezse isteğin dili.
        HttpHeaders english = authHeaders(owner);
        english.set(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9");
        JsonNode en = rest.exchange(hooksPath(f.boardId), HttpMethod.POST, new HttpEntity<>(
                hook("SLACK", receiver.url(slackPath()), null, List.of("CARD_CREATED"), null), english), JsonNode.class).getBody();
        assertThat(en.get("locale").asText()).isEqualTo("en");

        // Uygulama değişecekse yeni adres şart.
        ResponseEntity<JsonNode> switched = rest.exchange(hooksPath(f.boardId) + "/" + en.get("id").asLong(),
                HttpMethod.PATCH, new HttpEntity<>(Map.of("provider", "DISCORD"), authHeaders(owner)), JsonNode.class);
        assertThat(switched.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        for (int i = 2; i <= ChatHookService.MAX_HOOKS_PER_BOARD; i++) {
            assertThat(createHook(owner, f.boardId, hook("SLACK", receiver.url(slackPath()), null,
                    List.of("CARD_CREATED"), "tr")).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
        ResponseEntity<JsonNode> sixth = createHook(owner, f.boardId, hook("SLACK", receiver.url(slackPath()), null,
                List.of("CARD_CREATED"), "tr"));
        assertThat(sixth.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(sixth.getBody().get("message").asText()).isEqualTo("Bir panoya en fazla 5 kanal bağlanabilir.");
        assertThat(listHooks(owner, f.boardId).get("maxHooks").asInt()).isEqualTo(5);
    }

    // ── yardımcılar ─────────────────────────────────────────────────

    private record Fixture(long boardId, List<Long> columnIds) {
    }

    private static String slackPath() {
        return "/slack/T" + shortId() + "/B" + shortId() + "/" + shortId();
    }

    private static String discordPath() {
        return "/discord/" + Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L) + "/tok-" + shortId();
    }

    private static String teamsPath() {
        return "/teams/workflows/" + shortId() + "/triggers/manual/paths/invoke";
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private Fixture board(String token, String name) {
        JsonNode board = createBoard(token, name);
        List<Long> columns = new ArrayList<>();
        board.get("columns").forEach(c -> columns.add(c.get("id").asLong()));
        return new Fixture(board.get("id").asLong(), columns);
    }

    private static Map<String, Object> hook(String provider, String url, String label, List<String> events, String locale) {
        Map<String, Object> body = new HashMap<>();
        body.put("provider", provider);
        body.put("url", url);
        body.put("label", label);
        body.put("events", events);
        body.put("locale", locale);
        return body;
    }

    private String hooksPath(long boardId) {
        return "/api/boards/" + boardId + "/chat-hooks";
    }

    private ResponseEntity<JsonNode> createHook(String token, long boardId, Map<String, Object> body) {
        return rest.exchange(hooksPath(boardId), HttpMethod.POST, new HttpEntity<>(body, authHeaders(token)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> testUrl(String token, long boardId, String provider, String url) {
        return rest.exchange(hooksPath(boardId) + "/test", HttpMethod.POST,
                new HttpEntity<>(Map.of("provider", provider, "url", url), authHeaders(token)), JsonNode.class);
    }

    private JsonNode listHooks(String token, long boardId) {
        return rest.exchange(hooksPath(boardId), HttpMethod.GET, new HttpEntity<>(authHeaders(token)), JsonNode.class)
                .getBody();
    }

    private HttpStatus call(String token, HttpMethod method, String path, Object body) {
        return HttpStatus.valueOf(rest.exchange(path, method, new HttpEntity<>(body, authHeaders(token)), String.class)
                .getStatusCode().value());
    }

    private static String message(ResponseEntity<JsonNode> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        return response.getBody().get("message").asText();
    }

    private int failureCount(long hookId) {
        return jdbc.queryForObject("SELECT failure_count FROM board_chat_hooks WHERE id = ?", Integer.class, hookId);
    }

    private String reason(long hookId) {
        return jdbc.queryForObject("SELECT disabled_reason FROM board_chat_hooks WHERE id = ?", String.class, hookId);
    }

    private Object lastSentAt(long hookId) {
        return jdbc.queryForObject("SELECT last_sent_at FROM board_chat_hooks WHERE id = ?", Object.class, hookId);
    }

    private List<JsonNode> chatNotifications(String token) {
        JsonNode items = rest.exchange("/api/notifications", HttpMethod.GET, new HttpEntity<>(authHeaders(token)),
                JsonNode.class).getBody().get("items");
        List<JsonNode> out = new ArrayList<>();
        items.forEach(n -> {
            if ("CHAT_HOOK_DISABLED".equals(n.get("type").asText())) {
                out.add(n);
            }
        });
        return out;
    }

    private long userId(String token) {
        return rest.exchange("/api/users/me", HttpMethod.GET, new HttpEntity<>(authHeaders(token)), JsonNode.class)
                .getBody().get("id").asLong();
    }

    private ResponseEntity<JsonNode> postComment(String token, long boardId, long cardId, String body) {
        return rest.exchange("/api/boards/" + boardId + "/cards/" + cardId + "/comments", HttpMethod.POST,
                new HttpEntity<>(Map.of("body", body, "mentions", List.of()), authHeaders(token)), JsonNode.class);
    }

    /** Kartı gerçek canlı operasyonla ekler; yayın gelene kadar bekler (kişinin gördüğü süre). */
    private long addCard(String token, Fixture f, String title) throws Exception {
        JsonNode event = op(token, f.boardId, Map.of("type", "ADD_CARD", "columnId", f.columnIds.get(0), "title", title),
                "ADD_CARD");
        return event.get("card").get("id").asLong();
    }

    private void moveCard(String token, Fixture f, long cardId, long toColumnId, int position) throws Exception {
        op(token, f.boardId, Map.of("type", "MOVE_CARD", "cardId", cardId, "toColumnId", toColumnId,
                "position", position), "MOVE_CARD");
    }

    private void editCard(String token, Fixture f, long cardId, String title, long assigneeId) throws Exception {
        op(token, f.boardId, Map.of("type", "EDIT_CARD", "cardId", cardId, "title", title, "assigneeId", assigneeId),
                "EDIT_CARD");
    }

    private JsonNode op(String token, long boardId, Map<String, Object> op, String type) throws Exception {
        String topic = "/topic/board." + boardId;
        StompTestClient client = StompTestClient.connect(wsUrl(), token).subscribe(topic);
        StompTestClient.awaitSubscriptions();
        long sent = System.currentTimeMillis();
        client.send("/app/board/" + boardId + "/ops", op);
        try {
            for (int i = 0; i < 10; i++) {
                JsonNode msg = client.awaitMessage(topic);
                if (type.equals(msg.path("type").asText())) {
                    assertThat(System.currentTimeMillis() - sent).as("canlı yayın gecikmemeli").isLessThan(2_000);
                    return msg;
                }
            }
            throw new AssertionError("Olay gelmedi: " + type);
        } finally {
            client.disconnect();
        }
    }
}
