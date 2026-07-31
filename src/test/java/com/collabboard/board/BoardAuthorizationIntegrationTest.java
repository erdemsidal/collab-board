package com.collabboard.board;

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
 * Pano yetkilendirmesi: kim neyi görebilir, kim neyi değiştirebilir.
 *
 * Yetki hem REST hem WebSocket tarafında ayrı ayrı sınanır — birini korumak
 * diğerini açık bırakır ve açık kalan taraf sessiz bir güvenlik açığı olur.
 */
class BoardAuthorizationIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("Üye olmayan kullanıcı panoyu göremez (403)")
    void uyeOlmayanPanoyuGoremez() {
        String owner = registerAndLogin("Sahip", "Kisi", uniqueEmail("auth"));
        String yabanci = registerAndLogin("Yabanci", "Kisi", uniqueEmail("auth"));
        long boardId = createBoard(owner, "Özel Pano").get("id").asLong();

        ResponseEntity<JsonNode> response = rest.exchange("/api/boards/" + boardId,
                HttpMethod.GET, new HttpEntity<>(authHeaders(yabanci)), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Panoyu oluşturan otomatik OWNER olur ve erişebilir")
    void olusturanOtomatikSahipOlur() {
        String owner = registerAndLogin("Sahip", "Kisi", uniqueEmail("auth"));
        long boardId = createBoard(owner, "Sahiplik Testi").get("id").asLong();

        ResponseEntity<JsonNode> board = rest.exchange("/api/boards/" + boardId,
                HttpMethod.GET, new HttpEntity<>(authHeaders(owner)), JsonNode.class);
        assertThat(board.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> members = rest.exchange("/api/boards/" + boardId + "/members",
                HttpMethod.GET, new HttpEntity<>(authHeaders(owner)), JsonNode.class);
        assertThat(members.getBody()).hasSize(1);
        assertThat(members.getBody().get(0).get("role").asText()).isEqualTo("OWNER");
    }

    @Test
    @DisplayName("'Panolarım' yalnızca üye olunan panoları döner")
    void panolarimSadeceUyeOlunanlariDoner() {
        String ben = registerAndLogin("Ben", "Kullanici", uniqueEmail("auth"));
        String baskasi = registerAndLogin("Baska", "Kullanici", uniqueEmail("auth"));

        long benimPano = createBoard(ben, "Benim Panom").get("id").asLong();
        createBoard(baskasi, "Başkasının Panosu");

        ResponseEntity<JsonNode> response = rest.exchange("/api/boards",
                HttpMethod.GET, new HttpEntity<>(authHeaders(ben)), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).get("id").asLong()).isEqualTo(benimPano);
        assertThat(response.getBody().get(0).get("myRole").asText()).isEqualTo("OWNER");
    }

    @Test
    @DisplayName("Sadece OWNER üye ekleyebilir; EDITOR ekleyemez (403)")
    void sadeceSahipUyeEkleyebilir() {
        String ownerEmail = uniqueEmail("auth");
        String editorEmail = uniqueEmail("auth");
        String yeniEmail = uniqueEmail("auth");

        String owner = registerAndLogin("Sahip", "Kisi", ownerEmail);
        String editor = registerAndLogin("Editor", "Kisi", editorEmail);
        registerAndLogin("Yeni", "Kisi", yeniEmail);

        long boardId = createBoard(owner, "Üye Yönetimi").get("id").asLong();

        // OWNER ekleyebilir
        assertThat(addMember(boardId, owner, editorEmail, "EDITOR").getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // EDITOR ekleyemez
        assertThat(addMember(boardId, editor, yeniEmail, "EDITOR").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("VIEWER panoyu okuyabilir ama operasyonu reddedilir")
    void viewerDegisiklikYapamaz() throws Exception {
        String viewerEmail = uniqueEmail("auth");
        String owner = registerAndLogin("Sahip", "Kisi", uniqueEmail("auth"));
        String viewer = registerAndLogin("Izleyici", "Kisi", viewerEmail);

        JsonNode board = createBoard(owner, "Salt Okunur");
        long boardId = board.get("id").asLong();
        long todoId = board.get("columns").get(0).get("id").asLong();
        addMember(boardId, owner, viewerEmail, "VIEWER");

        // Okuyabilir
        ResponseEntity<JsonNode> read = rest.exchange("/api/boards/" + boardId,
                HttpMethod.GET, new HttpEntity<>(authHeaders(viewer)), JsonNode.class);
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Ama kart ekleyemez: operasyon reddedilir ve panoya hiçbir şey yayınlanmaz
        String topic = "/topic/board." + boardId;
        StompTestClient izleyici = StompTestClient.connect(wsUrl(), viewer)
                .subscribe(topic).subscribe("/user/queue/errors");
        StompTestClient.awaitSubscriptions();

        izleyici.send("/app/board/" + boardId + "/ops",
                Map.of("type", "ADD_CARD", "columnId", todoId, "title", "İzinsiz kart"));

        JsonNode rejection = izleyici.awaitMessage("/user/queue/errors");
        assertThat(rejection.get("type").asText()).isEqualTo("OP_REJECTED");
        assertThat(rejection.get("reason").asText()).isEqualTo("FORBIDDEN");
        assertThat(izleyici.poll(topic, 1)).isNull();   // panoya yayın gitmedi

        izleyici.disconnect();
    }

    @Test
    @DisplayName("Üye olmayan panonun yayınını dinleyemez (okuma sızıntısı yok)")
    void uyeOlmayanaVeriSizmaz() throws Exception {
        String owner = registerAndLogin("Sahip", "Kisi", uniqueEmail("auth"));
        String yabanci = registerAndLogin("Yabanci", "Kisi", uniqueEmail("auth"));

        JsonNode board = createBoard(owner, "Gizli Pano");
        long boardId = board.get("id").asLong();
        long todoId = board.get("columns").get(0).get("id").asLong();
        String topic = "/topic/board." + boardId;

        // Yabancının kimliği geçerli, bağlantı kurulabilir; asıl soru NE GÖRDÜĞÜ.
        StompTestClient davetsiz = StompTestClient.connect(wsUrl(), yabanci).subscribe(topic);
        StompTestClient sahip = StompTestClient.connect(wsUrl(), owner).subscribe(topic);
        StompTestClient.awaitSubscriptions();

        sahip.send("/app/board/" + boardId + "/ops",
                Map.of("type", "ADD_CARD", "columnId", todoId, "title", "Gizli kart"));

        // Sahip olayı alır...
        assertThat(sahip.awaitMessage(topic).get("type").asText()).isEqualTo("ADD_CARD");
        // ...ama davetsiz misafire HİÇBİR ŞEY sızmaz (aboneliği reddedildi).
        assertThat(davetsiz.poll(topic, 2)).isNull();

        sahip.disconnect();
    }

    @Test
    @DisplayName("Panonun son sahibi çıkarılamaz (pano yönetilemez hâle gelmesin)")
    void sonSahipCikarilamaz() {
        String ownerEmail = uniqueEmail("auth");
        String owner = registerAndLogin("Tek", "Sahip", ownerEmail);
        long boardId = createBoard(owner, "Tek Sahipli").get("id").asLong();

        JsonNode members = rest.exchange("/api/boards/" + boardId + "/members",
                HttpMethod.GET, new HttpEntity<>(authHeaders(owner)), JsonNode.class).getBody();
        long ownerUserId = members.get(0).get("userId").asLong();

        ResponseEntity<JsonNode> response = rest.exchange(
                "/api/boards/" + boardId + "/members/" + ownerUserId,
                HttpMethod.DELETE, new HttpEntity<>(authHeaders(owner)), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
