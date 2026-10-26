package com.collabboard.admin;

import com.collabboard.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Yönetim paneli (ADR 0024): kim görür, sayılar doğru mu.
 *
 * AYRI BAĞLAM: sahip listesi ve Umami kimliği bu sınıfa özel. Listedeki adres
 * bilerek karışık harfli; kayıt küçük harfle yapılıyor.
 *
 * Veritabanı bütün test sınıflarıyla ortak, başka testlerin kullanıcıları da
 * sayılıyor. Bu yüzden sayılar mutlak değil, FARK olarak sınanır: önce panelin
 * fotoğrafı alınır, sonra kayıt tarihleri ve son görülmeleri bilinen kişiler,
 * panolar, kartlar, yorumlar doğrudan veritabanına yazılır, ikinci fotoğraf
 * aradaki farkı tam olarak göstermeli.
 */
@TestPropertySource(properties = {
        "app.admin.emails= Patron-Panel@Test.com ;ikinci-sahip@test.com,",
        "app.analytics.umami.website-id=" + AdminDashboardIntegrationTest.WEBSITE_ID
})
class AdminDashboardIntegrationTest extends IntegrationTestBase {

    static final String WEBSITE_ID = "0b6a1c2d-3e4f-4a6b-8c8d-9e0f1a2b3c4d";

    private static final String OWNER_EMAIL = "patron-panel@test.com";
    private static final String ZONE = "Europe/Istanbul";
    /** Panoda geçen ama yanıtta asla görünmemesi gereken içerik. */
    private static final String SECRET = "Çok gizli kart başlığı";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StringRedisTemplate redis;

    // ── Kim görür ───────────────────────────────────────────────────

    @Test
    @DisplayName("Sahip olmayana panel yok: olmayan bir adresle aynı 404, menü bayrağı da yok")
    void sahipOlmayanaYok() {
        String member = registerAndLogin("Sıradan", "Üye", uniqueEmail("uye"));

        ResponseEntity<JsonNode> hidden = get("/api/admin/dashboard?tz=" + ZONE, member);
        ResponseEntity<JsonNode> missing = get("/api/admin/boyle-bir-adres-yok", member);
        assertThat(hidden.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(hidden.getBody().get("error")).isEqualTo(missing.getBody().get("error"));
        assertThat(hidden.getBody().get("message")).isEqualTo(missing.getBody().get("message"));
        assertThat(hidden.getBody().has("users")).isFalse();

        // Oturumsuz istek, kimlik isteyen her adres gibi 401.
        assertThat(rest.getForEntity("/api/admin/dashboard", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        // Hesap bilgisinde bayrak hiç yok (false bile değil).
        assertThat(get("/api/users/me", member).getBody().has("platformOwner")).isFalse();
    }

    @Test
    @DisplayName("Sahip: giriş yanıtında ve /me'de bayrak var, panel açılır; liste harf farkı gözetmez")
    void sahipGorur() {
        String owner = ownerToken();
        assertThat(get("/api/users/me", owner).getBody().get("platformOwner").asBoolean()).isTrue();
        JsonNode login = rest.postForEntity("/api/auth/login",
                Map.of("email", OWNER_EMAIL, "password", PASSWORD), JsonNode.class).getBody();
        assertThat(login.at("/user/platformOwner").asBoolean()).isTrue();

        ResponseEntity<JsonNode> dashboard = get("/api/admin/dashboard?tz=" + ZONE, owner);
        assertThat(dashboard.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dashboard.getHeaders().getCacheControl()).contains("no-store");
        assertThat(dashboard.getBody().get("timeZone").asText()).isEqualTo(ZONE);
    }

    // ── Sayılar ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Bilinen veriyle sayılar: kayıtlar, günlük grafik, etkinlik, geri gelenler, yöntem, dil, içerik, listeler")
    void sayilarDogru() {
        String owner = ownerToken();
        ZoneId zone = ZoneId.of(ZONE);
        LocalDate today = LocalDate.now(zone);
        JsonNode before = dashboard(owner);

        String tag = UUID.randomUUID().toString().substring(0, 8);
        // ad, gün önce (kayıt), yöntem, doğrulanmış, dil, gün önce (son görülme; null = hiç)
        long a = user("A" + tag, day(zone, 0), "password", true, "tr", day(zone, 0));
        long b = user("B" + tag, day(zone, 3), "google", true, "en", day(zone, 2));
        long c = user("C" + tag, day(zone, 10).plusHours(1), "both", true, "en", day(zone, 1));
        long d = user("D" + tag, day(zone, 20), "password", false, "tr", null);
        user("E" + tag, day(zone, 40), "password", true, "tr", day(zone, 15));
        user("F" + tag, day(zone, 25), "google", true, "tr", day(zone, 9));
        // En yeni kayıt: listenin başında görünmeli.
        LocalDateTime justNow = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
        long r = user("R" + tag, justNow, "google", false, "en", null);

        // İçerik: bir alan, iki pano (biri son 7 günde), kartlar, yorumlar, hareketler.
        long ws = jdbc.queryForObject("INSERT INTO workspaces(name, slug, created_at) VALUES (?, ?, ?) RETURNING id",
                Long.class, "Panel Alanı " + tag, "panel-" + tag, day(zone, 2));
        long busy = board(ws, "Yoğun Pano " + tag, day(zone, 1));
        long calm = board(ws, "Sakin Pano " + tag, day(zone, 12));
        for (long member : List.of(a, b, c, r)) {
            jdbc.update("INSERT INTO board_members(board_id, user_id, role) VALUES (?, ?, 'EDITOR')", busy, member);
        }
        jdbc.update("INSERT INTO board_members(board_id, user_id, role) VALUES (?, ?, 'OWNER')", calm, d);
        long column = jdbc.queryForObject(
                "INSERT INTO board_columns(board_id, name, position) VALUES (?, 'Yapılacak', 0) RETURNING id",
                Long.class, busy);
        long card = card(column, SECRET, day(zone, 0), 0);
        card(column, "İkinci kart", day(zone, 0), 1);
        card(column, "Eski kart", day(zone, 30), 2);
        jdbc.update("INSERT INTO comments(card_id, author_id, body, created_at) VALUES (?, ?, ?, ?)",
                card, a, "Gizli yorum " + SECRET, day(zone, 0));
        jdbc.update("INSERT INTO comments(card_id, author_id, body, created_at) VALUES (?, ?, ?, ?)",
                card, a, "Eski yorum", day(zone, 8));
        activities(busy, a, 1500, day(zone, 1));
        activities(calm, d, 1200, day(zone, 2));
        activities(calm, d, 50, day(zone, 8));   // pencerenin dışında: sayılmaz

        JsonNode after = dashboard(owner);

        // Kullanıcılar
        assertThat(diff(before, after, "/users/total")).isEqualTo(7);
        assertThat(diff(before, after, "/users/today")).isEqualTo(2);          // A, R
        assertThat(diff(before, after, "/users/last7Days")).isEqualTo(3);      // A, B, R
        assertThat(diff(before, after, "/users/last30Days")).isEqualTo(6);     // E hariç
        assertThat(diff(before, after, "/users/verified")).isEqualTo(5);
        assertThat(diff(before, after, "/users/unverified")).isEqualTo(2);     // D, R

        // Grafik: 30 gün, bugün sonda; fark yalnızca kayıt günlerinde.
        JsonNode days = after.get("signupsByDay");
        assertThat(days).hasSize(30);
        assertThat(days.get(29).get("date").asText()).isEqualTo(today.toString());
        assertThat(days.get(0).get("date").asText()).isEqualTo(today.minusDays(29).toString());
        Map<String, Long> chartDiff = chartDiff(before, after);
        assertThat(chartDiff).containsExactlyInAnyOrderEntriesOf(Map.of(
                today.toString(), 2L,
                today.minusDays(3).toString(), 1L,
                today.minusDays(10).toString(), 1L,
                today.minusDays(20).toString(), 1L,
                today.minusDays(25).toString(), 1L));
        long chartSum = 0;
        for (JsonNode day : days) {
            chartSum += day.get("count").asLong();
        }
        assertThat(chartSum).isEqualTo(after.at("/users/last30Days").asLong());

        // Etkinlik (son görülme)
        assertThat(diff(before, after, "/activity/today")).isEqualTo(1);       // A
        assertThat(diff(before, after, "/activity/last7Days")).isEqualTo(3);   // A, B, C
        assertThat(diff(before, after, "/activity/last30Days")).isEqualTo(5);  // D ve R hiç görülmedi
        // Geri gelenler: 7 ile 30 gün önce kayıt olanlar C, D, F; son 7 günde gelen yalnızca C.
        assertThat(diff(before, after, "/retention/cohort")).isEqualTo(3);
        assertThat(diff(before, after, "/retention/returned")).isEqualTo(1);

        // Giriş yöntemi ve dil
        assertThat(diff(before, after, "/methods/google")).isEqualTo(3);       // B, F, R
        assertThat(diff(before, after, "/methods/password")).isEqualTo(3);     // A, D, E
        assertThat(diff(before, after, "/methods/both")).isEqualTo(1);         // C
        assertThat(localeCount(after, "tr") - localeCount(before, "tr")).isEqualTo(4);
        assertThat(localeCount(after, "en") - localeCount(before, "en")).isEqualTo(3);

        // İçerik: var olan satırlar, son 7 gün bugünle birlikte 7 takvim günü.
        assertThat(diff(before, after, "/content/workspaces/total")).isEqualTo(1);
        assertThat(diff(before, after, "/content/workspaces/last7Days")).isEqualTo(1);
        assertThat(diff(before, after, "/content/boards/total")).isEqualTo(2);
        assertThat(diff(before, after, "/content/boards/last7Days")).isEqualTo(1);
        assertThat(diff(before, after, "/content/cards/total")).isEqualTo(3);
        assertThat(diff(before, after, "/content/cards/last7Days")).isEqualTo(2);
        assertThat(diff(before, after, "/content/comments/total")).isEqualTo(2);
        assertThat(diff(before, after, "/content/comments/last7Days")).isEqualTo(1);

        // Son kayıtlar: en yeni 50, yeniden eskiye; R başta ve alanları doğru.
        JsonNode recent = after.get("recentSignups");
        assertThat(recent.size()).isBetween(1, 50);
        JsonNode first = recent.get(0);
        assertThat(first.get("email").asText()).isEqualTo(email("R" + tag));
        assertThat(first.get("name").asText()).isEqualTo("R" + tag + " Deneme");
        assertThat(first.get("method").asText()).isEqualTo("google");
        assertThat(first.get("verified").asBoolean()).isFalse();
        assertThat(first.path("lastSeenAt").isNull() || first.path("lastSeenAt").isMissingNode()).isTrue();
        assertThat(first.get("boards").asLong()).isEqualTo(1);
        assertThat(Instant.parse(first.get("createdAt").asText()))
                .isEqualTo(justNow.toInstant(ZoneOffset.UTC));
        for (int i = 1; i < recent.size(); i++) {
            assertThat(Instant.parse(recent.get(i - 1).get("createdAt").asText()))
                    .isAfterOrEqualTo(Instant.parse(recent.get(i).get("createdAt").asText()));
        }

        // En hareketli panolar: ad, üye ve hareket sayısı; içerik yok.
        JsonNode top = after.get("topBoards");
        assertThat(top.get(0).get("name").asText()).isEqualTo("Yoğun Pano " + tag);
        assertThat(top.get(0).get("members").asLong()).isEqualTo(4);
        assertThat(top.get(0).get("activity").asLong()).isEqualTo(1500);
        assertThat(top.get(1).get("name").asText()).isEqualTo("Sakin Pano " + tag);
        assertThat(top.get(1).get("members").asLong()).isEqualTo(1);
        assertThat(top.get(1).get("activity").asLong()).isEqualTo(1200);
        assertThat(top.size()).isLessThanOrEqualTo(10);
        assertThat(after.toString()).doesNotContain(SECRET).doesNotContain("Gizli yorum");
    }

    /**
     * Panelin bütün zaman sınırları bu varsayıma dayanır: uygulamanın yazdığı
     * kayıt anı UTC duvar saatidir, sunucunun saat dilimi ne olursa olsun
     * (hibernate.jdbc.time_zone: UTC). Testler Türkiye saatindeki bir makinede
     * de koşuyor; varsayım bozulursa "bugün" 3 saat kayardı.
     */
    @Test
    @DisplayName("Uygulamanın yazdığı kayıt anı UTC duvar saati (panelin sınırları buna göre)")
    void kayitAniUtc() {
        String email = uniqueEmail("utc");
        registerAndLogin("Saat", "Dilimi", email);
        LocalDateTime stored = jdbc.queryForObject("SELECT created_at FROM users WHERE email = ?",
                LocalDateTime.class, email);
        assertThat(ChronoUnit.SECONDS.between(stored, LocalDateTime.now(ZoneOffset.UTC))).isBetween(-5L, 120L);
    }

    @Test
    @DisplayName("Şu an bir panoda: canlı bağlantılardaki kişiler, aynı kişinin iki sekmesi bir sayılır")
    void suAnPanoda() {
        String owner = ownerToken();
        long before = dashboard(owner).at("/activity/onlineNow").asLong();

        String key1 = "presence:board:" + (900_000_000 + (int) (Math.random() * 1000));
        String key2 = key1 + "1";
        try {
            redis.opsForHash().put(key1, "s1", presence("s1", 77_000_001L));
            redis.opsForHash().put(key1, "s2", presence("s2", 77_000_002L));
            redis.opsForHash().put(key2, "s3", presence("s3", 77_000_001L));   // aynı kişi, başka sekme
            assertThat(dashboard(owner).at("/activity/onlineNow").asLong()).isEqualTo(before + 2);
        } finally {
            redis.delete(List.of(key1, key2));
        }
    }

    @Test
    @DisplayName("Saat dilimi: bölge adı kabul edilir; tanınmayan ya da GMT+3 gibi yazım UTC'ye düşer")
    void saatDilimi() {
        String owner = ownerToken();
        assertThat(get("/api/admin/dashboard?tz=America/New_York", owner).getBody().get("timeZone").asText())
                .isEqualTo("America/New_York");
        for (String bad : List.of("Mars/Olympus", "GMT+3", "", "'; DROP TABLE users; --")) {
            JsonNode body = get("/api/admin/dashboard?tz=" + bad, owner).getBody();
            assertThat(body.get("timeZone").asText()).as(bad).isEqualTo("UTC");
            assertThat(body.get("signupsByDay").get(29).get("date").asText())
                    .isEqualTo(LocalDate.now(ZoneOffset.UTC).toString());
        }
        assertThat(get("/api/admin/dashboard", owner).getBody().get("timeZone").asText()).isEqualTo("UTC");
    }

    @Test
    @DisplayName("Umami kimliği verilmişse sayfalar onu ve betiğin adresini öğrenir, kimliksiz")
    void analitikAcik() {
        ResponseEntity<JsonNode> config = rest.getForEntity("/api/public/config", JsonNode.class);
        assertThat(config.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(config.getBody().at("/analytics/umamiWebsiteId").asText()).isEqualTo(WEBSITE_ID);
        assertThat(config.getBody().at("/analytics/scriptUrl").asText()).isEqualTo("https://cloud.umami.is/script.js");
    }

    // ── yardımcılar ─────────────────────────────────────────────────

    /** Sahip hesabı bu bağlamda bir kez açılır; sonraki testler yalnızca giriş yapar. */
    private String ownerToken() {
        Long existing = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Long.class, OWNER_EMAIL);
        String token = existing == 0 ? registerAndLogin("Site", "Sahibi", OWNER_EMAIL) : loginToken(OWNER_EMAIL);
        // İlk kimlikli istek sahibin kendi "son görülme"sini yazar; fotoğraflar arasında değişmesin.
        get("/api/users/me", token);
        return token;
    }

    private ResponseEntity<JsonNode> get(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(authHeaders(token)), JsonNode.class);
    }

    private JsonNode dashboard(String token) {
        ResponseEntity<JsonNode> response = get("/api/admin/dashboard?tz=" + ZONE, token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private static long diff(JsonNode before, JsonNode after, String pointer) {
        return after.at(pointer).asLong() - before.at(pointer).asLong();
    }

    private static long localeCount(JsonNode dashboard, String locale) {
        for (JsonNode row : dashboard.get("locales")) {
            if (row.get("locale").asText().equals(locale)) {
                return row.get("count").asLong();
            }
        }
        return 0;
    }

    /** Gün → sayı farkı; farkı olmayan günler listede yok. */
    private static Map<String, Long> chartDiff(JsonNode before, JsonNode after) {
        Map<String, Long> counts = new HashMap<>();
        after.get("signupsByDay").forEach(d -> counts.merge(d.get("date").asText(), d.get("count").asLong(), Long::sum));
        before.get("signupsByDay").forEach(d -> counts.merge(d.get("date").asText(), -d.get("count").asLong(), Long::sum));
        counts.values().removeIf(v -> v == 0);
        return counts;
    }

    /** Panelin saat diliminde {@code back} gün önceki gece yarısı, veritabanının tuttuğu UTC duvar saatiyle. */
    private static LocalDateTime day(ZoneId zone, int back) {
        return LocalDateTime.ofInstant(LocalDate.now(zone).minusDays(back).atStartOfDay(zone).toInstant(), ZoneOffset.UTC);
    }

    private static String email(String name) {
        return name.toLowerCase(Locale.ROOT) + "@panel.test";
    }

    /** method: "password", "google" ya da "both". lastSeen null: hiç görülmedi. */
    private long user(String name, LocalDateTime createdAt, String method, boolean verified, String locale,
                      LocalDateTime lastSeen) {
        String hash = method.equals("google") ? null : "$2a$10$abcdefghijklmnopqrstuuJ8i3f0yX0Qw5rV9nqEo3C2b1Y7wqZHe";
        long id = jdbc.queryForObject("""
                INSERT INTO users(first_name, last_name, email, password, enabled, locale, created_at, updated_at, last_seen_at)
                VALUES (?, 'Deneme', ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, name, email(name), hash, verified, locale, createdAt, createdAt, lastSeen);
        if (!method.equals("password")) {
            jdbc.update("INSERT INTO user_identities(user_id, provider, subject, email) VALUES (?, 'google', ?, ?)",
                    id, "sub-" + name, email(name));
        }
        return id;
    }

    private long board(long workspaceId, String name, LocalDateTime createdAt) {
        return jdbc.queryForObject(
                "INSERT INTO boards(name, workspace_id, created_at, updated_at) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, name, workspaceId, createdAt, createdAt);
    }

    private long card(long columnId, String title, LocalDateTime createdAt, int position) {
        return jdbc.queryForObject("""
                INSERT INTO cards(column_id, title, position, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?) RETURNING id
                """, Long.class, columnId, title, position, createdAt, createdAt);
    }

    /** Panoya {@code count} hareket; açıklamada gizli kart başlığı (yanıta sızmamalı). */
    private void activities(long boardId, long userId, int count, LocalDateTime at) {
        jdbc.update("""
                INSERT INTO board_activities(board_id, user_id, actor_name, type, description, created_at)
                SELECT ?, ?, 'Deneme', 'EDIT_CARD', ?, ? FROM generate_series(1, ?)
                """, boardId, userId, "'" + SECRET + "' kartını düzenledi", at, count);
    }

    private static String presence(String sessionId, long userId) {
        return "{\"sessionId\":\"" + sessionId + "\",\"userId\":" + userId
                + ",\"name\":\"Canlı Kişi\",\"initials\":\"CK\",\"color\":\"#35b37e\"}";
    }
}
