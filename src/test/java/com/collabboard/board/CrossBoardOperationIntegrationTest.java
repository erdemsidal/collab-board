package com.collabboard.board;

import com.collabboard.support.IntegrationTestBase;
import com.collabboard.support.StompTestClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Başka bir panonun kartına ya da kolonuna, kendi panosunun kanalından işlem
 * gönderilemez.
 *
 * Saldırgan kendi panosunun sahibi (yani orada editör); kurbanın panosunda hiçbir
 * rolü yok. Önceden yetki yalnızca adresteki panoya bakıldığı için bu işlemlerin
 * hepsi kurbanın panosunda uygulanıyordu.
 */
class CrossBoardOperationIntegrationTest extends IntegrationTestBase {

    private String victim;
    private long victimBoard;
    private long victimColumn;
    private long victimCard;

    private String attacker;
    private long attackerBoard;
    private long attackerColumn;

    private StompTestClient client;
    private String ops;

    @BeforeEach
    void setUp() throws Exception {
        victim = registerAndLogin("Kurban", "Kisi", uniqueEmail("victim"));
        JsonNode vb = createBoard(victim, "Kurbanın Panosu");
        victimBoard = vb.get("id").asLong();
        victimColumn = vb.get("columns").get(0).get("id").asLong();

        StompTestClient owner = StompTestClient.connect(wsUrl(), victim).subscribe("/topic/board." + victimBoard);
        StompTestClient.awaitSubscriptions();
        owner.send("/app/board/" + victimBoard + "/ops",
                Map.of("type", "ADD_CARD", "columnId", victimColumn, "title", "Gizli kart"));
        victimCard = owner.awaitMessage("/topic/board." + victimBoard).get("card").get("id").asLong();
        owner.disconnect();

        attacker = registerAndLogin("Saldirgan", "Kisi", uniqueEmail("attacker"));
        JsonNode ab = createBoard(attacker, "Saldırganın Panosu");
        attackerBoard = ab.get("id").asLong();
        attackerColumn = ab.get("columns").get(0).get("id").asLong();

        ops = "/app/board/" + attackerBoard + "/ops";
        client = StompTestClient.connect(wsUrl(), attacker)
                .subscribe("/topic/board." + attackerBoard).subscribe("/user/queue/errors");
        StompTestClient.awaitSubscriptions();
    }

    @Test
    @DisplayName("Başka panonun kolonu silinemez")
    void kolonSilinemez() throws Exception {
        rejected(Map.of("type", "DELETE_COLUMN", "columnId", victimColumn));
        JsonNode board = victimBoard();
        assertThat(board.get("columns")).hasSize(3);
        assertThat(board.get("columns").get(0).get("cards")).hasSize(1);
    }

    @Test
    @DisplayName("Başka panonun kartı kendi panoya taşınamaz, düzenlenemez, silinemez")
    void kartaDokunulamaz() throws Exception {
        rejected(Map.of("type", "MOVE_CARD", "cardId", victimCard, "toColumnId", attackerColumn,
                "position", 0, "baseVersion", 0));
        rejected(Map.of("type", "EDIT_CARD", "cardId", victimCard, "title", "Ele geçirildi", "baseVersion", 0));
        rejected(Map.of("type", "DELETE_CARD", "cardId", victimCard));

        JsonNode card = victimBoard().get("columns").get(0).get("cards").get(0);
        assertThat(card.get("id").asLong()).isEqualTo(victimCard);
        assertThat(card.get("title").asText()).isEqualTo("Gizli kart");
    }

    @Test
    @DisplayName("Başka panonun kolonuna kart ya da başka panoya kolon eklenemez, kolonu değiştirilemez")
    void kolonaYazilamaz() throws Exception {
        rejected(Map.of("type", "ADD_CARD", "columnId", victimColumn, "title", "Sızma"));
        rejected(Map.of("type", "ADD_COLUMN", "boardId", victimBoard, "name", "Sızma"));
        rejected(Map.of("type", "RENAME_COLUMN", "columnId", victimColumn, "name", "Sızma"));
        rejected(Map.of("type", "MOVE_COLUMN", "columnId", victimColumn, "position", 2));
        rejected(Map.of("type", "SET_WIP_LIMIT", "columnId", victimColumn, "limit", 1));

        JsonNode board = victimBoard();
        assertThat(board.get("columns").findValuesAsText("name")).containsExactly("To Do", "In Progress", "Done");
        assertThat(board.get("columns").get(0).get("cards")).hasSize(1);
        assertThat(board.get("columns").get(0).get("wipLimit").isNull()).isTrue();
    }

    @Test
    @DisplayName("Kendi kartını kendi panosunda taşımak eskisi gibi çalışır")
    void kendiPanosundaCalisir() throws Exception {
        client.send(ops, Map.of("type", "ADD_CARD", "columnId", attackerColumn, "title", "Kendi kartım"));
        JsonNode added = client.awaitMessage("/topic/board." + attackerBoard);
        assertThat(added.get("type").asText()).isEqualTo("ADD_CARD");
        client.disconnect();
    }

    // ── yardımcılar ──────────────────────────────────────────────────

    /** İşlem gönderene "bulunamadı" ile reddedilmeli ve yayına hiçbir şey çıkmamalı. */
    private void rejected(Map<String, Object> op) throws Exception {
        client.send(ops, op);
        JsonNode rejection = client.awaitMessage("/user/queue/errors");
        assertThat(rejection.get("reason").asText()).as("%s", op.get("type")).isEqualTo("NOT_FOUND");
        assertThat(client.poll("/topic/board." + attackerBoard, 1)).as("%s yayına çıktı", op.get("type")).isNull();
    }

    private JsonNode victimBoard() {
        return rest.exchange("/api/boards/" + victimBoard, HttpMethod.GET,
                new HttpEntity<>(authHeaders(victim)), JsonNode.class).getBody();
    }
}
