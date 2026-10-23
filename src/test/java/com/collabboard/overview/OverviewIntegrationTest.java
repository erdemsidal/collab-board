package com.collabboard.overview;

import com.collabboard.support.IntegrationTestBase;
import com.collabboard.support.StompTestClient;
import com.collabboard.user.UserService;
import com.collabboard.workspace.WorkspaceAccessService;
import com.collabboard.workspace.WorkspaceService;
import com.collabboard.workspace.entity.Workspace;
import com.collabboard.workspace.entity.WorkspaceRole;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Ana ekran özeti (GET /api/me/overview).
 *
 * Asıl sınanan şey sızıntı: özet birden çok panoyu tek yanıtta karıştırır, yani
 * yetki hatası burada tek bir panonun değil hepsinin içeriğini açardı. Bu yüzden
 * senaryoda kişinin üç farklı yoldan eriştiği panoların yanında, ona kart atayan
 * ama panosuna erişimi OLMAYAN bir yabancı da var.
 *
 * Gecikme "bugün"e bağlı; testin takvimden bağımsız olması için gün istekte
 * sabitlenir (istemci de kendi gününü böyle gönderir).
 */
class OverviewIntegrationTest extends IntegrationTestBase {

    private static final String TODAY = "2026-06-15";

    @Autowired
    WorkspaceService workspaceService;

    @Autowired
    WorkspaceAccessService workspaceAccessService;

    @Autowired
    UserService userService;

    @Test
    @DisplayName("Özet yalnızca erişilebilen panolardan derlenir: atanmış açık kartlar, gecikenler, hareketler")
    void ozetYalnizcaErisilebilenPanolardanDerlenir() throws Exception {
        String meEmail = uniqueEmail("ozet");
        String me = registerAndLogin("Özet", "Sahibi", meEmail);
        long myId = userId(me);

        // 1) Kendi panom (sahibiyim)
        JsonNode mine = createBoard(me, "Benim Panom");
        long mineId = mine.get("id").asLong();

        // 2) Ekip arkadaşının panosu — davetle EDİTÖR'üm
        String mateEmail = uniqueEmail("ekip");
        String mate = registerAndLogin("Ekip", "Arkadaşı", mateEmail);
        JsonNode team = createBoard(mate, "Ekip Panosu");
        long teamId = team.get("id").asLong();
        addMember(teamId, mate, meEmail, "EDITOR");

        // 3) Şirket panosu — panoya davet yok, şirket üyeliğiyle erişiyorum
        String bossEmail = uniqueEmail("sirket");
        String boss = registerAndLogin("Şirket", "Sahibi", bossEmail);
        Workspace acme = workspaceService.create("Acme", userService.getUserByEmail(bossEmail));
        workspaceAccessService.addMember(acme.getId(), myId, WorkspaceRole.MEMBER);
        JsonNode company = rest.exchange("/api/boards", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Şirket Panosu", "workspaceId", acme.getId()), authHeaders(boss)),
                JsonNode.class).getBody();
        long companyId = company.get("id").asLong();

        // 4) Yabancının panosu — erişimim YOK, ama bir kartı bana atıyor
        String stranger = registerAndLogin("Yabancı", "Kişi", uniqueEmail("yabanci"));
        JsonNode foreign = createBoard(stranger, "Yabancı Pano");
        long foreignId = foreign.get("id").asLong();

        // Kendi panomda: biri gecikmiş, biri bugün teslim, biri bitmiş (son kolonda), biri atanmamış
        try (BoardSession b = new BoardSession(me, mine)) {
            long late = b.add(0, "Gecikmiş iş");
            b.edit(late, "Gecikmiş iş", myId, "2026-06-10", 0);
            long dueToday = b.add(1, "Bugün teslim");
            b.edit(dueToday, "Bugün teslim", myId, TODAY, 0);
            long finished = b.add(0, "Bitmiş iş");
            b.edit(finished, "Bitmiş iş", myId, "2026-06-01", 0);
            b.move(finished, 2, 1);   // Done = son kolon → artık açık değil, gecikmiş de sayılmaz
            b.add(0, "Atanmamış iş");
        }
        // Şirket panosunda: tarihsiz, bana atanmış
        try (BoardSession b = new BoardSession(boss, company)) {
            long work = b.add(0, "Şirketteki işim");
            b.edit(work, "Şirketteki işim", myId, null, 0);
        }
        // Yabancı panoda: bana atanmış, gecikmiş — ÖZETE GİRMEMELİ
        try (BoardSession b = new BoardSession(stranger, foreign)) {
            long secret = b.add(0, "Gizli iş");
            b.edit(secret, "Gizli iş", myId, "2026-06-01", 0);
        }
        // Ekip panosunda: biri bana, biri arkadaşıma atanmış. EN SON yapılan hareket bu.
        try (BoardSession b = new BoardSession(mate, team)) {
            long mineThere = b.add(0, "Ekipteki işim");
            b.edit(mineThere, "Ekipteki işim", myId, "2026-06-20", 0);
            long theirs = b.add(0, "Arkadaşın işi");
            b.edit(theirs, "Arkadaşın işi", userId(mate), "2026-06-01", 0);
        }

        JsonNode overview = overview(me, TODAY).getBody();

        assertThat(overview.get("boardCount").asInt()).isEqualTo(3);

        // Açık + bana atanmış: bitmiş, atanmamış, arkadaşın ve yabancının kartları dışarıda.
        // Sıra: son tarihi en yakın önce, tarihsiz en sonda.
        JsonNode cards = overview.get("assignedCards");
        assertThat(titles(cards)).containsExactly("Gecikmiş iş", "Bugün teslim", "Ekipteki işim", "Şirketteki işim");
        assertThat(overview.get("assignedOpenCount").asInt()).isEqualTo(4);

        // Gecikmiş: yalnızca 10 Haziran'lık. Bugün teslim olan gecikmiş değil;
        // bitmiş kartın tarihi geçmiş ama iş bitti.
        assertThat(overview.get("overdueCount").asInt()).isEqualTo(1);

        JsonNode late = cards.get(0);
        assertThat(late.get("boardId").asLong()).isEqualTo(mineId);
        assertThat(late.get("boardName").asText()).isEqualTo("Benim Panom");
        assertThat(late.get("columnName").asText()).isEqualTo("To Do");
        assertThat(late.get("dueDate").asText()).isEqualTo("2026-06-10");
        assertThat(cards.get(1).get("columnName").asText()).isEqualTo("In Progress");
        assertThat(cards.get(2).get("boardName").asText()).isEqualTo("Ekip Panosu");
        assertThat(cards.get(3).get("boardName").asText()).isEqualTo("Şirket Panosu");
        assertThat(cards.get(3).get("dueDate").isNull()).isTrue();

        // Hareketler: yalnızca erişilebilen panolardan, en yeni önce.
        JsonNode activity = overview.get("recentActivity");
        assertThat(activity.size()).isPositive();
        List<Long> ids = new ArrayList<>();
        activity.forEach(a -> {
            ids.add(a.get("id").asLong());
            assertThat(a.get("boardId").asLong()).isIn(mineId, teamId, companyId);
            assertThat(a.get("boardId").asLong()).isNotEqualTo(foreignId);
        });
        assertThat(ids).isSortedAccordingTo((x, y) -> Long.compare(y, x));
        assertThat(activity.toString()).doesNotContain("Gizli iş", "Yabancı Pano");

        JsonNode newest = activity.get(0);
        assertThat(newest.get("boardId").asLong()).isEqualTo(teamId);
        assertThat(newest.get("boardName").asText()).isEqualTo("Ekip Panosu");
        assertThat(newest.get("actorName").asText()).isEqualTo("Ekip Arkadaşı");
        assertThat(newest.get("type").asText()).isEqualTo("EDIT_CARD");

        // Son 7 gün sayacı: üç panonun kendi geçmiş uçlarının toplamıyla aynı.
        long expected = activityCount(me, mineId) + activityCount(me, teamId) + activityCount(me, companyId);
        assertThat(overview.get("activityLast7Days").asLong()).isEqualTo(expected);
    }

    @Test
    @DisplayName("Panosu olmayan kullanıcı boş bir özet alır")
    void panosuzKullaniciBosOzetAlir() {
        String token = registerAndLogin("Yeni", "Gelen", uniqueEmail("ozet"));

        ResponseEntity<JsonNode> response = overview(token, null);   // gün verilmezse sunucunun günü

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body.get("boardCount").asInt()).isZero();
        assertThat(body.get("assignedOpenCount").asInt()).isZero();
        assertThat(body.get("overdueCount").asInt()).isZero();
        assertThat(body.get("activityLast7Days").asLong()).isZero();
        assertThat(body.get("assignedCards")).isEmpty();
        assertThat(body.get("recentActivity")).isEmpty();
    }

    @Test
    @DisplayName("Bozuk gün 400 alır, kimliksiz istek 401")
    void gecersizIstekler() {
        String token = registerAndLogin("Hatalı", "Tarih", uniqueEmail("ozet"));

        assertThat(overview(token, "15.06.2026").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rest.getForEntity("/api/me/overview", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── Yardımcılar ─────────────────────────────────────────

    private ResponseEntity<JsonNode> overview(String token, String today) {
        String url = "/api/me/overview" + (today == null ? "" : "?today=" + today);
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(authHeaders(token)), JsonNode.class);
    }

    private long userId(String token) {
        return rest.exchange("/api/users/me", HttpMethod.GET, new HttpEntity<>(authHeaders(token)), JsonNode.class)
                .getBody().get("id").asLong();
    }

    private long activityCount(String token, long boardId) {
        return rest.exchange("/api/boards/" + boardId + "/activity?limit=100", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), JsonNode.class).getBody().size();
    }

    private static List<String> titles(JsonNode cards) {
        List<String> out = new ArrayList<>();
        cards.forEach(c -> out.add(c.get("title").asText()));
        return out;
    }

    /**
     * Bir panoda, sahibi ya da editörü adına canlı kanaldan işlem yapar.
     * Her işlemden sonra yayının gelmesi beklenir: işlemler sırayla uygulanır ve
     * geçmişe sırayla yazılır (testin "en yeni" beklentisi buna dayanıyor).
     */
    private final class BoardSession implements AutoCloseable {
        private final StompTestClient client;
        private final String topic;
        private final String ops;
        private final List<Long> columns = new ArrayList<>();

        BoardSession(String token, JsonNode board) throws Exception {
            long id = board.get("id").asLong();
            board.get("columns").forEach(c -> columns.add(c.get("id").asLong()));
            topic = "/topic/board." + id;
            ops = "/app/board/" + id + "/ops";
            client = StompTestClient.connect(wsUrl(), token).subscribe(topic);
            StompTestClient.awaitSubscriptions();
        }

        long add(int column, String title) throws InterruptedException {
            client.send(ops, Map.of("type", "ADD_CARD", "columnId", columns.get(column), "title", title));
            return client.awaitMessage(topic).get("card").get("id").asLong();
        }

        void edit(long cardId, String title, long assigneeId, String dueDate, long baseVersion)
                throws InterruptedException {
            Map<String, Object> op = new HashMap<>();
            op.put("type", "EDIT_CARD");
            op.put("cardId", cardId);
            op.put("title", title);
            op.put("assigneeId", assigneeId);
            op.put("dueDate", dueDate);
            op.put("baseVersion", baseVersion);
            client.send(ops, op);
            assertThat(client.awaitMessage(topic).get("type").asText()).isEqualTo("EDIT_CARD");
        }

        void move(long cardId, int column, long baseVersion) throws InterruptedException {
            client.send(ops, Map.of("type", "MOVE_CARD", "cardId", cardId,
                    "toColumnId", columns.get(column), "position", 0, "baseVersion", baseVersion));
            assertThat(client.awaitMessage(topic).get("type").asText()).isEqualTo("MOVE_CARD");
        }

        @Override
        public void close() {
            client.disconnect();
        }
    }
}
