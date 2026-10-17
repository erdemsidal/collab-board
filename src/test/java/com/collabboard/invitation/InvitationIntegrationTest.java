package com.collabboard.invitation;

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
 * Pano davetleri: üyelik artık davet edilenin onayıyla başlıyor ve her adım
 * karşı tarafa canlı ulaşıyor.
 */
class InvitationIntegrationTest extends IntegrationTestBase {

    private static final String INBOX = "/user/queue/notifications";

    @Test
    @DisplayName("Davet, davet edilene hangi ekranda olursa olsun canlı ulaşır")
    void davetCanliUlasir() throws Exception {
        String ownerEmail = uniqueEmail("davet");
        String guestEmail = uniqueEmail("davet");
        String owner = registerAndLogin("Davet", "Eden", ownerEmail);
        String guest = registerAndLogin("Davet", "Edilen", guestEmail);
        long boardId = createBoard(owner, "Pazarlama").get("id").asLong();

        // Davet edilen hiçbir panoda değil — yalnızca kişisel kanalını dinliyor.
        StompTestClient inbox = StompTestClient.connect(wsUrl(), guest).subscribe(INBOX);
        StompTestClient.awaitSubscriptions();

        assertThat(invite(boardId, owner, guestEmail, "VIEWER").getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode event = inbox.awaitMessage(INBOX);
        assertThat(event.get("type").asText()).isEqualTo("NOTIFICATION");
        JsonNode n = event.get("notification");
        assertThat(n.get("type").asText()).isEqualTo("BOARD_INVITE");
        assertThat(n.get("actorName").asText()).isEqualTo("Davet Eden");
        assertThat(n.get("boardName").asText()).isEqualTo("Pazarlama");
        assertThat(n.get("role").asText()).isEqualTo("VIEWER");
        assertThat(n.get("invitationStatus").asText()).isEqualTo("PENDING");
        inbox.disconnect();

        // Davet henüz kabul edilmedi: pano davet edilene AÇIK DEĞİL.
        assertThat(getBoard(boardId, guest).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Kabul edilen davet üyelik açar; davet edene canlı haber gider, pano üye listesini tazeler")
    void kabulUyelikAcar() throws Exception {
        String ownerEmail = uniqueEmail("davet");
        String guestEmail = uniqueEmail("davet");
        String owner = registerAndLogin("Pano", "Sahibi", ownerEmail);
        String guest = registerAndLogin("Yeni", "Uye", guestEmail);
        long boardId = createBoard(owner, "Ürün").get("id").asLong();

        long invitationId = invite(boardId, owner, guestEmail, "EDITOR").getBody().get("id").asLong();

        StompTestClient ownerClient = StompTestClient.connect(wsUrl(), owner)
                .subscribe(INBOX).subscribe("/topic/board." + boardId);
        StompTestClient.awaitSubscriptions();

        ResponseEntity<JsonNode> accepted = respond(invitationId, guest, "accept");
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(accepted.getBody().get("boardId").asLong()).isEqualTo(boardId);
        assertThat(accepted.getBody().get("role").asText()).isEqualTo("EDITOR");

        JsonNode toOwner = ownerClient.awaitMessage(INBOX).get("notification");
        assertThat(toOwner.get("type").asText()).isEqualTo("INVITE_ACCEPTED");
        assertThat(toOwner.get("actorName").asText()).isEqualTo("Yeni Uye");

        // Panodaki herkese "üye listesi değişti" sinyali — içeriksiz.
        JsonNode signal = awaitSignal(ownerClient, "/topic/board." + boardId, "MEMBERS_CHANGED");
        assertThat(signal.size()).isEqualTo(1);   // yalnızca "type", e-posta yok
        ownerClient.disconnect();

        assertThat(getBoard(boardId, guest).getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode members = rest.exchange("/api/boards/" + boardId + "/members", HttpMethod.GET,
                new HttpEntity<>(authHeaders(owner)), JsonNode.class).getBody();
        assertThat(members.findValuesAsText("email")).contains(guestEmail);
    }

    @Test
    @DisplayName("Reddedilen davet üyelik açmaz; davet edene haber gider")
    void retUyelikAcmaz() throws Exception {
        String ownerEmail = uniqueEmail("davet");
        String guestEmail = uniqueEmail("davet");
        String owner = registerAndLogin("Pano", "Sahibi", ownerEmail);
        String guest = registerAndLogin("Istemeyen", "Kisi", guestEmail);
        long boardId = createBoard(owner, "Gizli").get("id").asLong();
        long invitationId = invite(boardId, owner, guestEmail, "VIEWER").getBody().get("id").asLong();

        assertThat(respond(invitationId, guest, "decline").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(getBoard(boardId, guest).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        JsonNode ownerInbox = notifications(owner);
        assertThat(ownerInbox.get("items").get(0).get("type").asText()).isEqualTo("INVITE_DECLINED");

        // Aynı davete ikinci yanıt yok.
        assertThat(respond(invitationId, guest, "accept").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Daveti yalnızca davet edilen yanıtlayabilir; başkasına davet yokmuş gibi görünür")
    void baskasininDavetiYanitlanamaz() {
        String ownerEmail = uniqueEmail("davet");
        String guestEmail = uniqueEmail("davet");
        String strangerEmail = uniqueEmail("davet");
        String owner = registerAndLogin("Pano", "Sahibi", ownerEmail);
        registerAndLogin("Asil", "Davetli", guestEmail);
        String stranger = registerAndLogin("Baska", "Biri", strangerEmail);
        long boardId = createBoard(owner, "Özel").get("id").asLong();
        long invitationId = invite(boardId, owner, guestEmail, "OWNER").getBody().get("id").asLong();

        // 403 değil 404: davetin varlığını bile doğrulamamak için.
        assertThat(respond(invitationId, stranger, "accept").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(getBoard(boardId, stranger).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Geri çekilen davet kabul edilemez; davet edilene haber gider")
    void geriCekilenDavet() throws Exception {
        String ownerEmail = uniqueEmail("davet");
        String guestEmail = uniqueEmail("davet");
        String owner = registerAndLogin("Pano", "Sahibi", ownerEmail);
        String guest = registerAndLogin("Az", "Kalsin", guestEmail);
        long boardId = createBoard(owner, "Taslak").get("id").asLong();
        long invitationId = invite(boardId, owner, guestEmail, "EDITOR").getBody().get("id").asLong();

        StompTestClient guestClient = StompTestClient.connect(wsUrl(), guest).subscribe(INBOX);
        StompTestClient.awaitSubscriptions();

        ResponseEntity<Void> cancelled = rest.exchange("/api/boards/" + boardId + "/invitations/" + invitationId,
                HttpMethod.DELETE, new HttpEntity<>(authHeaders(owner)), Void.class);
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        JsonNode n = guestClient.awaitMessage(INBOX).get("notification");
        assertThat(n.get("type").asText()).isEqualTo("INVITE_CANCELLED");
        assertThat(n.get("invitationId").asLong()).isEqualTo(invitationId);
        guestClient.disconnect();

        ResponseEntity<JsonNode> late = respond(invitationId, guest, "accept");
        assertThat(late.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(late.getBody().get("message").asText()).contains("geri çekildi");
    }

    @Test
    @DisplayName("Hesabı olmayan adrese davet: postayla haber verilir, kayıt olunca davet onu bekler")
    void hesapsizAdreseDavet() throws Exception {
        String ownerEmail = uniqueEmail("davet");
        String laterEmail = uniqueEmail("sonradan");
        String owner = registerAndLogin("Pano", "Sahibi", ownerEmail);
        long boardId = createBoard(owner, "Açık Kaynak").get("id").asLong();

        mailSender.clear();
        ResponseEntity<JsonNode> sent = invite(boardId, owner, laterEmail, "EDITOR");
        assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(sent.getBody().get("hasAccount").asBoolean()).isFalse();
        awaitMails(1);

        // Kişi sonra kayıt olur — davet adrese bağlı olduğu için zil menüsünde hazır.
        String later = registerAndLogin("Sonradan", "Gelen", laterEmail);
        JsonNode inbox = notifications(later);
        assertThat(inbox.get("unreadCount").asLong()).isEqualTo(1);
        JsonNode n = inbox.get("items").get(0);
        assertThat(n.get("type").asText()).isEqualTo("BOARD_INVITE");
        assertThat(n.get("actorName").asText()).isEqualTo("Pano Sahibi");

        // İkinci okuma bildirimi tekrar üretmez.
        assertThat(notifications(later).get("items")).hasSize(1);

        assertThat(respond(n.get("invitationId").asLong(), later, "accept").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getBoard(boardId, later).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Aynı adrese ikinci bekleyen davet ve zaten üye olana davet reddedilir")
    void tekrarDavetReddedilir() {
        String ownerEmail = uniqueEmail("davet");
        String guestEmail = uniqueEmail("davet");
        String owner = registerAndLogin("Pano", "Sahibi", ownerEmail);
        registerAndLogin("Tekrar", "Davetli", guestEmail);
        long boardId = createBoard(owner, "Tekrar").get("id").asLong();

        assertThat(invite(boardId, owner, guestEmail, "VIEWER").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // Büyük harfle yazılsa da aynı adres.
        assertThat(invite(boardId, owner, guestEmail.toUpperCase(), "EDITOR").getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(invite(boardId, owner, ownerEmail, "EDITOR").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);   // kendini davet edemez
    }

    @Test
    @DisplayName("Bekleyen davetleri (e-postalarıyla) yalnızca yönetici görür")
    void bekleyenDavetlerYoneticiye() {
        String ownerEmail = uniqueEmail("davet");
        String viewerEmail = uniqueEmail("davet");
        String owner = registerAndLogin("Pano", "Sahibi", ownerEmail);
        String viewer = registerAndLogin("Sadece", "Izleyen", viewerEmail);
        long boardId = createBoard(owner, "Liste").get("id").asLong();
        addMember(boardId, owner, viewerEmail, "VIEWER");
        invite(boardId, owner, uniqueEmail("bekleyen"), "EDITOR");

        ResponseEntity<JsonNode> asOwner = rest.exchange("/api/boards/" + boardId + "/invitations", HttpMethod.GET,
                new HttpEntity<>(authHeaders(owner)), JsonNode.class);
        assertThat(asOwner.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(asOwner.getBody()).hasSize(1);

        ResponseEntity<JsonNode> asViewer = rest.exchange("/api/boards/" + boardId + "/invitations", HttpMethod.GET,
                new HttpEntity<>(authHeaders(viewer)), JsonNode.class);
        assertThat(asViewer.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Mevcut üyenin rolü değiştirilebilir; yeni kişi rol değişikliğiyle eklenemez")
    void rolDegisikligiYalnizcaUyeye() {
        String ownerEmail = uniqueEmail("davet");
        String memberEmail = uniqueEmail("davet");
        String outsiderEmail = uniqueEmail("davet");
        String owner = registerAndLogin("Pano", "Sahibi", ownerEmail);
        registerAndLogin("Rolu", "Degisen", memberEmail);
        registerAndLogin("Disaridaki", "Kisi", outsiderEmail);
        long boardId = createBoard(owner, "Roller").get("id").asLong();
        addMember(boardId, owner, memberEmail, "VIEWER");

        long memberId = userIdOf(boardId, owner, memberEmail);
        ResponseEntity<JsonNode> changed = rest.exchange("/api/boards/" + boardId + "/members/" + memberId,
                HttpMethod.PATCH, new HttpEntity<>(Map.of("role", "EDITOR"), authHeaders(owner)), JsonNode.class);
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(changed.getBody().get("role").asText()).isEqualTo("EDITOR");

        // Onay vermemiş biri rol değişikliği yoluyla panoya sokulamaz.
        long outsiderId = rest.exchange("/api/users/me", HttpMethod.GET,
                new HttpEntity<>(authHeaders(loginToken(outsiderEmail))), JsonNode.class).getBody().get("id").asLong();
        ResponseEntity<JsonNode> sneaky = rest.exchange("/api/boards/" + boardId + "/members/" + outsiderId,
                HttpMethod.PATCH, new HttpEntity<>(Map.of("role", "EDITOR"), authHeaders(owner)), JsonNode.class);
        assertThat(sneaky.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── yardımcılar ──────────────────────────────────────────────────

    private ResponseEntity<JsonNode> respond(long invitationId, String token, String action) {
        return rest.exchange("/api/invitations/" + invitationId + "/" + action, HttpMethod.POST,
                new HttpEntity<>(authHeaders(token)), JsonNode.class);
    }

    private JsonNode notifications(String token) {
        return rest.exchange("/api/notifications", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), JsonNode.class).getBody();
    }

    private long userIdOf(long boardId, String ownerToken, String email) {
        JsonNode members = rest.exchange("/api/boards/" + boardId + "/members", HttpMethod.GET,
                new HttpEntity<>(authHeaders(ownerToken)), JsonNode.class).getBody();
        for (JsonNode m : members) {
            if (m.get("email").asText().equalsIgnoreCase(email)) {
                return m.get("id").asLong();
            }
        }
        throw new IllegalStateException("Üye bulunamadı: " + email);
    }

    /** Pano kanalında belirli bir sinyali bekler; araya giren başka olayları atlar. */
    private JsonNode awaitSignal(StompTestClient client, String topic, String type) throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            JsonNode msg = client.awaitMessage(topic);
            if (type.equals(msg.get("type").asText())) {
                return msg;
            }
        }
        throw new AssertionError("Sinyal gelmedi: " + type);
    }

    private void awaitMails(int expected) throws InterruptedException {
        for (int i = 0; i < 100 && mailSender.sent().size() < expected; i++) {
            Thread.sleep(20);
        }
        assertThat(mailSender.sent()).hasSize(expected);
    }
}
