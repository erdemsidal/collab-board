package com.collabboard.i18n;

import com.collabboard.support.IntegrationTestBase;
import com.collabboard.support.StompTestClient;
import com.collabboard.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Türkçe ve İngilizce (ADR 0020).
 *
 * REST hatası isteğin dilinde (Accept-Language, yoksa Türkçe); hesap kayıt
 * isteğinin diliyle açılır; postalar ve canlı kanaldaki reddetmeler kişinin
 * kayıtlı dilinde; geçmiş satırı arayüzün kendi dilinde kurabileceği parçalarla gelir.
 */
class LocaleIntegrationTest extends IntegrationTestBase {

    @Autowired
    UserRepository userRepository;

    // ── REST hataları ────────────────────────────────────────

    @Test
    @DisplayName("Hata mesajı isteğin dilinde; başlık yoksa Türkçe, desteklenmeyen dilde İngilizce")
    void hataIsteginDilinde() {
        String email = uniqueEmail("dil-hata");
        registerAndLogin("Dil", "Hata", email);
        Map<String, String> wrong = Map.of("email", email, "password", "yanlis-sifre-1");

        assertThat(post("/api/auth/login", wrong, null).getBody().get("message").asText())
                .isEqualTo("Email veya şifre hatalı");
        assertThat(post("/api/auth/login", wrong, "en-US,en;q=0.9").getBody().get("message").asText())
                .isEqualTo("Wrong email or password");
        assertThat(post("/api/auth/login", wrong, "tr-TR,tr;q=0.9,en;q=0.8").getBody().get("message").asText())
                .isEqualTo("Email veya şifre hatalı");
        assertThat(post("/api/auth/login", wrong, "de-DE,de;q=0.9").getBody().get("message").asText())
                .isEqualTo("Wrong email or password");
    }

    @Test
    @DisplayName("Alan doğrulama mesajları da isteğin dilinde; durum kodu ve gövde biçimi aynı")
    void alanDogrulamaDilinde() {
        Map<String, String> blank = Map.of("firstName", "", "lastName", "Soyad",
                "email", uniqueEmail("dil-alan"), "password", PASSWORD);

        ResponseEntity<JsonNode> en = post("/api/auth/register", blank, "en");
        assertThat(en.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(en.getBody().get("message").asText()).isEqualTo("The request is not valid");
        assertThat(en.getBody().get("fieldErrors").get(0).get("field").asText()).isEqualTo("firstName");
        assertThat(messagesOf(en.getBody().get("fieldErrors"))).contains("First name can't be empty");

        ResponseEntity<JsonNode> tr = post("/api/auth/register", blank, null);
        assertThat(tr.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(tr.getBody().get("message").asText()).isEqualTo("İstek doğrulama hatası");
        assertThat(messagesOf(tr.getBody().get("fieldErrors"))).contains("Ad boş olamaz");
    }

    // ── Hesabın dili ─────────────────────────────────────────

    @Test
    @DisplayName("Kayıt isteğinin dili hesaba yazılır; doğrulama postası ve giriş yanıtı o dilde")
    void kayitDili() throws Exception {
        String enEmail = uniqueEmail("dil-kayit-en");
        String trEmail = uniqueEmail("dil-kayit-tr");

        ResponseEntity<JsonNode> registered = post("/api/auth/register", Map.of("firstName", "Jane",
                "lastName", "Doe", "email", enEmail, "password", PASSWORD), "en-GB,en;q=0.9");
        post("/api/auth/register", Map.of("firstName", "Ayşe", "lastName", "Yılmaz",
                "email", trEmail, "password", PASSWORD), null);

        assertThat(registered.getBody().get("locale").asText()).isEqualTo("en");
        assertThat(userRepository.findByEmail(enEmail).orElseThrow().getLocale()).isEqualTo("en");
        assertThat(userRepository.findByEmail(trEmail).orElseThrow().getLocale()).isEqualTo("tr");

        MimeMessage enMail = awaitMailTo(enEmail);
        assertThat(enMail.getSubject()).isEqualTo("Verify your Sturnio account");
        assertThat(textOf(enMail)).contains("Hi Jane,", "This link works for 24 hours", "/?verify=")
                .doesNotContain("Merhaba");
        MimeMessage trMail = awaitMailTo(trEmail);
        assertThat(trMail.getSubject()).isEqualTo("Sturnio hesabını doğrula");
        assertThat(textOf(trMail)).contains("Merhaba Ayşe,", "24 saat geçerli").doesNotContain("—");

        verifyEmail(registered.getBody().get("id").asLong());
        JsonNode login = post("/api/auth/login", Map.of("email", enEmail, "password", PASSWORD), null).getBody();
        assertThat(login.get("user").get("locale").asText()).isEqualTo("en");
    }

    @Test
    @DisplayName("Dil profilden değişir; yalnızca tr ve en kabul edilir, ad olduğu gibi kalır")
    void dilDegisir() {
        String token = registerAndLogin("Dil", "Degistiren", uniqueEmail("dil-patch"));

        for (String bad : List.of("de", "EN", "en-US", "")) {
            ResponseEntity<JsonNode> rejected = patchMe(token, Map.of("locale", bad));
            assertThat(rejected.getStatusCode()).as(bad).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(rejected.getBody().get("message").asText()).contains("tr, en");
        }

        ResponseEntity<JsonNode> changed = patchMe(token, Map.of("locale", "en"));
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(changed.getBody().get("locale").asText()).isEqualTo("en");
        assertThat(changed.getBody().get("firstName").asText()).isEqualTo("Dil");

        JsonNode me = rest.exchange("/api/users/me", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), JsonNode.class).getBody();
        assertThat(me.get("locale").asText()).isEqualTo("en");
        assertThat(me.get("lastName").asText()).isEqualTo("Degistiren");

        // Ad formu dili göndermez; dil yerinde kalır.
        assertThat(patchMe(token, Map.of("firstName", "Yeni", "lastName", "Ad")).getBody().get("locale").asText())
                .isEqualTo("en");
    }

    // ── Kişinin kayıtlı dili ─────────────────────────────────

    @Test
    @DisplayName("Anılan kişiye posta kendi dilinde; anan kişinin dili önemsiz")
    void bahsetmePostasiAlicininDilinde() throws Exception {
        String ownerEmail = uniqueEmail("dil-anan"), memberEmail = uniqueEmail("dil-anilan");
        String owner = registerAndLogin("Ayse", "Yilmaz", ownerEmail);
        String member = registerAndLogin("Mehmet", "Kaya", memberEmail);
        patchMe(member, Map.of("locale", "en"));
        JsonNode board = createBoard(owner, "Marketing");
        long boardId = board.get("id").asLong();
        long cardId = addCard(owner, boardId, board.get("columns").get(0).get("id").asLong(), "Login page");
        addMember(boardId, owner, memberEmail, "EDITOR");
        long memberId = rest.exchange("/api/users/me", HttpMethod.GET,
                new HttpEntity<>(authHeaders(member)), JsonNode.class).getBody().get("id").asLong();
        mailSender.clear();

        ResponseEntity<JsonNode> created = rest.exchange("/api/boards/" + boardId + "/cards/" + cardId + "/comments",
                HttpMethod.POST, new HttpEntity<>(Map.of("body", "@Mehmet Kaya can you check the copy?",
                        "mentions", List.of(memberId)), authHeaders(owner)), JsonNode.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        MimeMessage mail = awaitMailTo(memberEmail);
        assertThat(mail.getSubject()).isEqualTo("Ayse Yilmaz mentioned you in a comment: Login page");
        assertThat(textOf(mail)).contains("mentioned you in a comment on the card \"Login page\" in \"Marketing\"",
                "Open card: ", "/?board=" + boardId + "&card=" + cardId).doesNotContain("andı");
    }

    @Test
    @DisplayName("Canlı kanaldaki ret kişinin kayıtlı dilinde")
    void canliRetKisininDilinde() throws Exception {
        String viewerEmail = uniqueEmail("dil-izleyici");
        String owner = registerAndLogin("Sahip", "Kisi", uniqueEmail("dil-sahip"));
        String viewer = registerAndLogin("Viewer", "Person", viewerEmail);
        patchMe(viewer, Map.of("locale", "en"));
        JsonNode board = createBoard(owner, "Read only");
        long boardId = board.get("id").asLong();
        addMember(boardId, owner, viewerEmail, "VIEWER");

        StompTestClient client = StompTestClient.connect(wsUrl(), viewer).subscribe("/user/queue/errors");
        StompTestClient.awaitSubscriptions();
        client.send("/app/board/" + boardId + "/ops", Map.of("type", "ADD_CARD",
                "columnId", board.get("columns").get(0).get("id").asLong(), "title", "Not allowed"));

        JsonNode rejection = client.awaitMessage("/user/queue/errors");
        assertThat(rejection.get("reason").asText()).isEqualTo("FORBIDDEN");
        assertThat(rejection.get("message").asText()).isEqualTo("You can't make changes on this board (your role: VIEWER)");
        client.disconnect();
    }

    // ── Geçmiş satırının parçaları ───────────────────────────

    @Test
    @DisplayName("Geçmiş satırı türü ve parçalarıyla gelir; Türkçe açıklama yedek olarak kalır")
    void gecmisParcalari() throws Exception {
        String owner = registerAndLogin("Parca", "Sahibi", uniqueEmail("dil-parca"));
        JsonNode board = createBoard(owner, "Parçalar");
        long boardId = board.get("id").asLong();
        addCard(owner, boardId, board.get("columns").get(0).get("id").asLong(), "Süt al");

        JsonNode latest = rest.exchange("/api/boards/" + boardId + "/activity", HttpMethod.GET,
                new HttpEntity<>(authHeaders(owner)), JsonNode.class).getBody().get(0);
        assertThat(latest.get("type").asText()).isEqualTo("ADD_CARD");
        assertThat(latest.get("params").get("card").asText()).isEqualTo("Süt al");
        assertThat(latest.get("params").get("column").asText()).isEqualTo("Yapılacak");
        assertThat(latest.get("description").asText()).isEqualTo("'Süt al' kartını Yapılacak kolonuna ekledi");

        JsonNode moment = rest.exchange("/api/boards/" + boardId + "/timeline", HttpMethod.GET,
                new HttpEntity<>(authHeaders(owner)), JsonNode.class).getBody().get(0);
        assertThat(moment.get("params").get("card").asText()).isEqualTo("Süt al");
    }

    // ── yardımcılar ──────────────────────────────────────────

    private ResponseEntity<JsonNode> post(String path, Object body, String acceptLanguage) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (acceptLanguage != null) {
            headers.set(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage);
        }
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private ResponseEntity<JsonNode> patchMe(String token, Map<String, String> body) {
        return rest.exchange("/api/users/me", HttpMethod.PATCH, new HttpEntity<>(body, authHeaders(token)),
                JsonNode.class);
    }

    private static List<String> messagesOf(JsonNode fieldErrors) {
        return java.util.stream.StreamSupport.stream(fieldErrors.spliterator(), false)
                .map(e -> e.get("message").asText()).toList();
    }

    private long addCard(String token, long boardId, long columnId, String title) throws Exception {
        String topic = "/topic/board." + boardId;
        StompTestClient client = StompTestClient.connect(wsUrl(), token).subscribe(topic);
        StompTestClient.awaitSubscriptions();
        Map<String, Object> op = new HashMap<>();
        op.put("type", "ADD_CARD");
        op.put("columnId", columnId);
        op.put("title", title);
        client.send("/app/board/" + boardId + "/ops", op);
        long cardId = client.awaitMessage(topic).get("card").get("id").asLong();
        client.disconnect();
        return cardId;
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
    private static String textOf(Part part) throws Exception {
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
