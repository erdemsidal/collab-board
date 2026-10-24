package com.collabboard.presence;

import com.collabboard.support.IntegrationTestBase;
import com.collabboard.support.StompTestClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Canlı imleçler: panodaki herkes diğerlerinin faresini ve sürüklediği kartı görür. */
class LiveCursorIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("Panoya katılan kişinin imleci, sunucunun bildiği adıyla diğer üyelere ulaşır")
    void imlecDigerUyeyeUlasir() throws Exception {
        Board board = boardWithTwoMembers();
        StompTestClient erdem = StompTestClient.connect(wsUrl(), board.ownerToken).subscribe(board.cursors());
        StompTestClient ayse = StompTestClient.connect(wsUrl(), board.memberToken).subscribe(board.cursors());
        StompTestClient.awaitSubscriptions();
        join(erdem, board.id);

        erdem.send(board.cursorApp(), move(120.5, 340, 77L, false));

        JsonNode seen = ayse.awaitMessage(board.cursors());
        assertThat(seen.get("type").asText()).isEqualTo("CURSOR");
        assertThat(seen.get("name").asText()).isEqualTo("Erdem Sidal");
        assertThat(seen.get("x").asDouble()).isEqualTo(120.5);
        assertThat(seen.get("y").asDouble()).isEqualTo(340);
        assertThat(seen.get("cardId").asLong()).isEqualTo(77L);
        assertThat(seen.get("color").asText()).isNotBlank();

        erdem.disconnect();
        ayse.disconnect();
    }

    @Test
    @DisplayName("Panoya katılmamış oturumun imleci yayınlanmaz")
    void katilmayanOturumunImleciDuser() throws Exception {
        Board board = boardWithTwoMembers();
        StompTestClient ayse = StompTestClient.connect(wsUrl(), board.memberToken).subscribe(board.cursors());
        // Erdem üye ama bu bağlantıyla panoya katılmadı (presence/join yok).
        StompTestClient erdem = StompTestClient.connect(wsUrl(), board.ownerToken);
        StompTestClient.awaitSubscriptions();

        erdem.send(board.cursorApp(), move(10, 10, null, false));

        assertThat(ayse.poll(board.cursors(), 1)).isNull();
        erdem.disconnect();
        ayse.disconnect();
    }

    @Test
    @DisplayName("Çok sık gelen konumlar seyreltilir; 'ayrıldı' mesajı her zaman geçer")
    void sikMesajSeyreltilirAyrilmaGecer() throws Exception {
        Board board = boardWithTwoMembers();
        StompTestClient erdem = StompTestClient.connect(wsUrl(), board.ownerToken);
        StompTestClient ayse = StompTestClient.connect(wsUrl(), board.memberToken).subscribe(board.cursors());
        StompTestClient.awaitSubscriptions();
        join(erdem, board.id);

        // Arka arkaya 20 konum: sunucu en fazla ~30/sn geçirir, hepsi geçmemeli.
        for (int i = 0; i < 20; i++) {
            erdem.send(board.cursorApp(), move(i, i, null, false));
        }
        erdem.send(board.cursorApp(), move(null, null, null, true));

        int moves = 0;
        boolean goneSeen = false;
        JsonNode message;
        while ((message = ayse.poll(board.cursors(), 1)) != null) {
            if (message.get("gone").asBoolean()) {
                goneSeen = true;
            } else {
                moves++;
            }
        }
        assertThat(moves).isBetween(1, 19);
        assertThat(goneSeen).isTrue();

        erdem.disconnect();
        ayse.disconnect();
    }

    // ── yardımcılar ──────────────────────────────────────────────────

    private record Board(long id, String ownerToken, String memberToken) {
        String cursors() {
            return "/topic/board." + id + "/cursors";
        }

        String cursorApp() {
            return "/app/board/" + id + "/cursor";
        }
    }

    private Board boardWithTwoMembers() {
        String ownerToken = registerAndLogin("Erdem", "Sidal", uniqueEmail("imlec"));
        long boardId = createBoard(ownerToken, "İmleç panosu").get("id").asLong();
        String memberEmail = uniqueEmail("imlec");
        registerAndLogin("Ayse", "Yilmaz", memberEmail);
        addMember(boardId, ownerToken, memberEmail, "VIEWER");
        return new Board(boardId, ownerToken, loginToken(memberEmail));
    }

    /** Panoya katıl ve katılmanın sunucuda işlenmesini bekle. */
    private void join(StompTestClient client, long boardId) throws Exception {
        String presence = "/topic/board." + boardId + "/presence";
        client.subscribe(presence);
        StompTestClient.awaitSubscriptions();
        client.send("/app/board/" + boardId + "/presence/join", Map.of());
        client.awaitMessage(presence);
    }

    private static Map<String, Object> move(Number x, Number y, Long cardId, boolean gone) {
        // Map.of null kabul etmez; ayrılma mesajında konum yok.
        Map<String, Object> body = new HashMap<>();
        body.put("x", x);
        body.put("y", y);
        body.put("cardId", cardId);
        body.put("gone", gone);
        return body;
    }
}
