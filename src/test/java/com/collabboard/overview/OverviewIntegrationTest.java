package com.collabboard.overview;

import com.collabboard.audit.BoardActivity;
import com.collabboard.audit.BoardActivityRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @Autowired
    BoardActivityRepository activityRepository;

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

        // Grafik boş panoda da 7 gündür: arayüz eksen çizmek için satır saymasın.
        assertThat(body.get("activityByDay")).hasSize(7);
        body.get("activityByDay").forEach(d -> assertThat(d.get("count").asLong()).isZero());
        assertThat(body.get("doneLast7Days").asInt()).isZero();
        assertThat(body.get("donePrev7Days").asInt()).isZero();
        assertThat(body.get("liveBoards")).isEmpty();
        assertThat(body.get("team")).isEmpty();
        assertThat(body.get("boardsHealth")).isEmpty();
        assertThat(body.get("aging")).isEmpty();
    }

    @Test
    @DisplayName("Günlük hareket ve biten iş: hareketsiz günler 0, Done içinde sıra değiştirmek bitirmek değil, yabancı pano sayılmaz")
    void gunlukHareketVeBitenIs() throws Exception {
        String me = registerAndLogin("Trend", "Sahibi", uniqueEmail("trend"));
        JsonNode board = createBoard(me, "Trend Panosu");
        String stranger = registerAndLogin("Yabancı", "Trend", uniqueEmail("trend"));
        JsonNode foreign = createBoard(stranger, "Yabancı Trend");

        // Olaylar sırayla yapılır, sonra her birinin günü TODAY'e göre ayarlanır (yorumdaki gün).
        try (BoardSession b = new BoardSession(me, board)) {
            long a = b.add(0, "A");      // -20
            long bb = b.add(0, "B");     // -20
            long c = b.add(0, "C");      // -20
            long d = b.add(0, "D");      // -20
            b.move(a, 2, 0);             // -9  A → Done           (önceki hafta)
            b.move(c, 2, 0);             // -8  C → Done           (önceki hafta)
            b.move(d, 2, 0);             // -4  D → Done
            b.move(d, 1, 1);             // -4  D geri In Progress'e
            b.move(d, 2, 2);             // -3  D yine Done         (bu hafta, bir kez sayılır)
            b.move(bb, 1, 0);            // -3  B → In Progress
            b.move(c, 2, 1);             // -1  C Done içinde yer değiştirdi: yeniden bitmiş sayılmaz
            b.move(bb, 2, 1);            //  0  B → Done           (bugün)
        }
        shiftDays(board.get("id").asLong(), -20, -20, -20, -20, -9, -8, -4, -4, -3, -3, -1, 0);

        // Yabancının panosunda da hareket ve bitiş var — "boş" olması gereken -2. güne.
        try (BoardSession b = new BoardSession(stranger, foreign)) {
            b.move(b.add(0, "Gizli"), 2, 0);
        }
        shiftDays(foreign.get("id").asLong(), -2, -2);

        JsonNode overview = overview(me, TODAY).getBody();

        List<String> dates = new ArrayList<>();
        List<Long> counts = new ArrayList<>();
        overview.get("activityByDay").forEach(d -> {
            dates.add(d.get("date").asText());
            counts.add(d.get("count").asLong());
        });
        assertThat(dates).containsExactly("2026-06-09", "2026-06-10", "2026-06-11", "2026-06-12",
                "2026-06-13", "2026-06-14", "2026-06-15");
        assertThat(counts).containsExactly(0L, 0L, 2L, 2L, 0L, 1L, 1L);

        assertThat(overview.get("doneLast7Days").asInt()).isEqualTo(2);   // D ve B
        assertThat(overview.get("donePrev7Days").asInt()).isEqualTo(2);   // A ve C
    }

    @Test
    @DisplayName("Pano sağlığı ve bekleyen işler: doluluk, WIP, darboğaz, en eski kart önce; yabancı pano sızmaz")
    void panoSagligiVeBekleyenIsler() throws Exception {
        String meEmail = uniqueEmail("saglik");
        String me = registerAndLogin("Sağlık", "Sahibi", meEmail);
        long myId = userId(me);
        JsonNode health = createBoard(me, "Sağlık Panosu");
        long healthId = health.get("id").asLong();
        JsonNode yesterday = createBoard(me, "Dünkü Pano");
        for (int i = 1; i <= 5; i++) {
            createBoard(me, "Boş " + i);   // hareketsiz panolar listenin sonuna düşer; 7 panodan 6'sı görünür
        }
        String stranger = registerAndLogin("Yabancı", "Sağlık", uniqueEmail("saglik"));
        JsonNode foreign = createBoard(stranger, "Yabancı Sağlık");

        try (BoardSession b = new BoardSession(me, health)) {
            b.wip(1, 1);                                          // In Progress en fazla 1 kart
            long forgotten = b.add(0, "Unutulan iş");
            b.edit(forgotten, "Unutulan iş", myId, "2026-06-01", 0);   // TODAY'e göre gecikmiş
            b.add(1, "Süren iş");                                 // In Progress doldu
            b.move(b.add(0, "Biten iş"), 2, 0);                   // To Do'dan çıkış ölçüldü → darboğaz To Do
            b.add(0, "Yeni iş");
            b.add(0, "Ek iş");
            b.add(0, "Ek iş 2");
        }
        // Unutulan iş 10 gün, Süren iş 3 gün önce eklenmiş olsun (diğerleri şimdi).
        List<BoardActivity> rows = activityRepository.findByBoardIdOrderByIdAsc(healthId);
        rows.get(1).setCreatedAt(LocalDateTime.now().minusDays(10));
        rows.get(3).setCreatedAt(LocalDateTime.now().minusDays(3));
        activityRepository.saveAll(rows);

        try (BoardSession b = new BoardSession(me, yesterday)) {
            b.add(0, "Dünkü kart");
        }
        shiftAll(yesterday.get("id").asLong(), LocalDateTime.now().minusDays(1));

        // Yabancının 30 gündür bekleyen kartı: sızsaydı listenin başında olurdu.
        try (BoardSession b = new BoardSession(stranger, foreign)) {
            b.add(0, "Yabancı unutulan");
        }
        shiftAll(foreign.get("id").asLong(), LocalDateTime.now().minusDays(30));

        JsonNode overview = overview(me, TODAY).getBody();

        JsonNode boards = overview.get("boardsHealth");
        List<String> names = new ArrayList<>();
        boards.forEach(b -> names.add(b.get("boardName").asText()));
        assertThat(names).containsExactly("Sağlık Panosu", "Dünkü Pano", "Boş 5", "Boş 4", "Boş 3", "Boş 2");

        JsonNode h = boards.get(0);
        assertThat(h.get("boardId").asLong()).isEqualTo(healthId);
        assertThat(h.get("columns")).hasSize(3);
        assertThat(h.get("columns").get(0).get("name").asText()).isEqualTo("To Do");
        assertThat(h.get("columns").get(0).get("count").asInt()).isEqualTo(4);
        assertThat(h.get("columns").get(0).get("wipLimit").isNull()).isTrue();
        assertThat(h.get("columns").get(1).get("count").asInt()).isEqualTo(1);
        assertThat(h.get("columns").get(1).get("wipLimit").asInt()).isEqualTo(1);
        assertThat(h.get("columns").get(2).get("count").asInt()).isEqualTo(1);
        assertThat(h.get("wipFull").asText()).isEqualTo("In Progress");
        assertThat(h.get("bottleneck").asText()).isEqualTo("To Do");
        assertThat(h.get("openCards").asInt()).isEqualTo(5);      // Done'daki "Biten iş" hariç
        assertThat(h.get("overdueCards").asInt()).isEqualTo(1);
        assertThat(h.get("lastActivityAt").isNull()).isFalse();

        JsonNode y = boards.get(1);
        assertThat(y.get("bottleneck").isNull()).isTrue();        // hiç geçiş yok, ölçülemez
        assertThat(y.get("wipFull").isNull()).isTrue();
        assertThat(y.get("openCards").asInt()).isEqualTo(1);
        assertThat(boards.get(5).get("lastActivityAt").isNull()).isTrue();

        // 6 açık kart var, 5'i görünür: en uzun bekleyen önce; Done'daki ve yabancınınki yok.
        JsonNode aging = overview.get("aging");
        assertThat(aging).hasSize(5);
        assertThat(titles(aging).subList(0, 3)).containsExactly("Unutulan iş", "Süren iş", "Dünkü kart");
        assertThat(titles(aging)).doesNotContain("Biten iş", "Yabancı unutulan");
        JsonNode oldest = aging.get(0);
        assertThat(oldest.get("boardName").asText()).isEqualTo("Sağlık Panosu");
        assertThat(oldest.get("columnName").asText()).isEqualTo("To Do");
        assertThat(oldest.get("ageSeconds").asLong()).isGreaterThanOrEqualTo(10L * 24 * 3600 - 60);
        assertThat(oldest.get("estimated").asBoolean()).isFalse();

        assertThat(overview.toString()).doesNotContain("Yabancı");
    }

    @Test
    @DisplayName("Canlı panolar ve ekip: başkası açıksa görünür, kendim ve yabancılar görünmez; renk panodakiyle aynı")
    void canliPanolarVeEkip() throws Exception {
        String meEmail = uniqueEmail("canli");
        String me = registerAndLogin("Canlı", "Ben", meEmail);
        long myId = userId(me);
        long liveId = createBoard(me, "Canlı Pano").get("id").asLong();
        long quietId = createBoard(me, "Sessiz Pano").get("id").asLong();

        // Pano arkadaşı: Canlı Pano'ya davetle üye
        String mateEmail = uniqueEmail("canli");
        String mate = registerAndLogin("Pano", "Arkadaşı", mateEmail);
        addMember(liveId, me, mateEmail, "EDITOR");
        long mateId = userId(mate);

        // Alan arkadaşı: şirket üyeliğiyle şirketin panosunu görüyor; misafir görmüyor
        Workspace company = workspaceService.create("Canlı AŞ", userService.getUserByEmail(meEmail));
        rest.exchange("/api/boards", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Şirket Panosu", "workspaceId", company.getId()), authHeaders(me)),
                JsonNode.class);
        String coworkerEmail = uniqueEmail("canli");
        registerAndLogin("Alan", "Arkadaşı", coworkerEmail);
        workspaceAccessService.addMember(company.getId(),
                userService.getUserByEmail(coworkerEmail).getId(), WorkspaceRole.MEMBER);
        String guestEmail = uniqueEmail("canli");
        registerAndLogin("Misafir", "Kişi", guestEmail);
        workspaceAccessService.addMember(company.getId(),
                userService.getUserByEmail(guestEmail).getId(), WorkspaceRole.GUEST);

        // Yabancı kendi panosunda çevrimiçi: ne canlı panolarda ne ekipte görünmeli
        String stranger = registerAndLogin("Yabancı", "Kişi", uniqueEmail("canli"));
        long foreignId = createBoard(stranger, "Yabancı Pano").get("id").asLong();

        List<StompTestClient> clients = new ArrayList<>();
        try {
            JsonNode presence = joinPresence(mate, liveId, clients);
            joinPresence(me, liveId, clients);
            joinPresence(me, quietId, clients);      // orada yalnızca ben varım → "canlı" değil
            joinPresence(stranger, foreignId, clients);
            String mateColor = presence.get("users").get(0).get("color").asText();

            JsonNode overview = overview(me, TODAY).getBody();

            JsonNode live = overview.get("liveBoards");
            assertThat(live).hasSize(1);
            assertThat(live.get(0).get("boardId").asLong()).isEqualTo(liveId);
            assertThat(live.get(0).get("boardName").asText()).isEqualTo("Canlı Pano");
            JsonNode people = live.get(0).get("people");
            assertThat(people).hasSize(1);   // ben hariç
            assertThat(people.get(0).get("userId").asLong()).isEqualTo(mateId);
            assertThat(people.get(0).get("name").asText()).isEqualTo("Pano Arkadaşı");
            assertThat(people.get(0).get("initials").asText()).isEqualTo("PA");
            assertThat(people.get(0).get("color").asText()).isEqualTo(mateColor);
            assertThat(people.get(0).has("sessionId")).isFalse();

            JsonNode team = overview.get("team");
            List<String> names = new ArrayList<>();
            team.forEach(t -> names.add(t.get("name").asText()));
            assertThat(names).containsExactly("Pano Arkadaşı", "Alan Arkadaşı");   // çevrimiçi önce
            assertThat(team.get(0).get("online").asBoolean()).isTrue();
            assertThat(team.get(0).get("where").asText()).isEqualTo("Canlı Pano");
            assertThat(team.get(0).get("color").asText()).isEqualTo(mateColor);
            assertThat(team.get(1).get("online").asBoolean()).isFalse();
            assertThat(team.get(1).get("where").isNull()).isTrue();
            team.forEach(t -> assertThat(t.get("userId").asLong()).isNotEqualTo(myId));
        } finally {
            clients.forEach(StompTestClient::disconnect);
        }
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
     * Panonun olaylarını sırasıyla TODAY'e göre verilen günlere taşır (öğlen 12:00).
     * Öğlen bilinçli: sunucunun saat dilimi ne olursa olsun aynı UTC gününe düşer.
     */
    private void shiftDays(long boardId, int... dayOffsets) {
        List<BoardActivity> rows = activityRepository.findByBoardIdOrderByIdAsc(boardId);
        assertThat(rows).hasSize(dayOffsets.length);
        for (int i = 0; i < dayOffsets.length; i++) {
            rows.get(i).setCreatedAt(LocalDate.parse(TODAY).plusDays(dayOffsets[i]).atTime(12, 0));
        }
        activityRepository.saveAll(rows);
    }

    /** Panonun bütün olaylarını aynı ana taşır. */
    private void shiftAll(long boardId, LocalDateTime at) {
        List<BoardActivity> rows = activityRepository.findByBoardIdOrderByIdAsc(boardId);
        rows.forEach(r -> r.setCreatedAt(at));
        activityRepository.saveAll(rows);
    }

    /** Panoya katılır (presence); katılanın aldığı ilk çevrimiçi listesini döner. */
    private JsonNode joinPresence(String token, long boardId, List<StompTestClient> clients) throws Exception {
        String topic = "/topic/board." + boardId + "/presence";
        StompTestClient client = StompTestClient.connect(wsUrl(), token).subscribe(topic);
        clients.add(client);
        StompTestClient.awaitSubscriptions();
        client.send("/app/board/" + boardId + "/presence/join", Map.of());
        return client.awaitMessage(topic);
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

        void wip(int column, int limit) throws InterruptedException {
            client.send(ops, Map.of("type", "SET_WIP_LIMIT", "columnId", columns.get(column), "limit", limit));
            assertThat(client.awaitMessage(topic).get("type").asText()).isEqualTo("SET_WIP_LIMIT");
        }

        @Override
        public void close() {
            client.disconnect();
        }
    }
}
