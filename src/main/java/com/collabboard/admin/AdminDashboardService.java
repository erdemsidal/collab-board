package com.collabboard.admin;

import com.collabboard.admin.dto.AdminDashboardResponse;
import com.collabboard.admin.dto.AdminDashboardResponse.Activity;
import com.collabboard.admin.dto.AdminDashboardResponse.ActiveBoard;
import com.collabboard.admin.dto.AdminDashboardResponse.Content;
import com.collabboard.admin.dto.AdminDashboardResponse.Counter;
import com.collabboard.admin.dto.AdminDashboardResponse.DayCount;
import com.collabboard.admin.dto.AdminDashboardResponse.LocaleCount;
import com.collabboard.admin.dto.AdminDashboardResponse.Methods;
import com.collabboard.admin.dto.AdminDashboardResponse.RecentSignup;
import com.collabboard.admin.dto.AdminDashboardResponse.Retention;
import com.collabboard.admin.dto.AdminDashboardResponse.Users;
import com.collabboard.presence.PresenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Yönetim panelinin sayıları (ADR 0024). Hepsi bizim veritabanımızdan.
 *
 * Neden JPA değil, düz SQL (JdbcTemplate)? Buradaki her şey bir sayım: tabloyu
 * bir kez gezip birden çok sayıyı birlikte çıkarmak (COUNT ... FILTER) ya da
 * gruplamak. Entity yüklemek hem gereksiz hem pahalı olurdu. Sorgu sayısı sabit
 * (yedi); kullanıcı ya da pano sayısı arttıkça artmaz (N+1 yok).
 *
 * ZAMAN: Tablolardaki TIMESTAMP sütunları UTC duvar saatini tutar. Hibernate
 * yazarken saati UTC'ye çevirir (hibernate.jdbc.time_zone: UTC), canlıdaki
 * sunucu zaten UTC'de çalışır. Bu yüzden sınırlar burada UTC LocalDateTime
 * olarak hesaplanıp sorguya verilir; "bugün" ise panelin sahibinin saat
 * diliminde başlar (Türkiye'de gece yarısı, UTC'de değil).
 */
@Service
public class AdminDashboardService {

    private static final Logger log = LoggerFactory.getLogger(AdminDashboardService.class);

    static final int CHART_DAYS = 30;
    static final int RECENT_LIMIT = 50;
    static final int TOP_BOARDS_LIMIT = 10;

    /** "Z" değil "UTC": ad olduğu gibi Postgres'e de gidiyor. */
    static final ZoneId UTC = ZoneId.of("UTC");

    private final JdbcTemplate jdbc;
    private final PresenceService presence;

    /** Postgres'in tanıdığı saat dilimleri; her ad için bir kez sorulur. */
    private final Set<String> zonesKnownToDb = ConcurrentHashMap.newKeySet();

    public AdminDashboardService(JdbcTemplate jdbc, PresenceService presence) {
        this.jdbc = jdbc;
        this.presence = presence;
    }

    /**
     * Tarayıcının gönderdiği saat dilimi. Yalnızca "Europe/Istanbul" gibi bölge
     * adları kabul edilir: "GMT+3" gibi yazımlarda Java ile Postgres işareti ters
     * yorumlar. Boş, tanınmayan ya da Postgres'in bilmediği ad UTC'ye düşer.
     */
    public ZoneId resolveZone(String requested) {
        if (requested == null || requested.isBlank() || requested.length() > 64) {
            return UTC;
        }
        ZoneId zone;
        try {
            zone = ZoneId.of(requested.trim());
        } catch (java.time.DateTimeException e) {
            return UTC;
        }
        if (!zone.getId().contains("/")) {
            return UTC;
        }
        if (!zonesKnownToDb.contains(zone.getId())) {
            try {
                jdbc.queryForObject("SELECT now() AT TIME ZONE ?", Object.class, zone.getId());
                zonesKnownToDb.add(zone.getId());
            } catch (DataAccessException e) {
                log.debug("Saat dilimi veritabanında yok, UTC kullanılıyor: {}", zone.getId());
                return UTC;
            }
        }
        return zone;
    }

    public AdminDashboardResponse build(ZoneId zone) {
        Instant now = Instant.now();
        LocalDate today = LocalDate.ofInstant(now, zone);

        // Takvim günleri, panelin sahibinin saatinde; sorguya UTC olarak gider.
        LocalDateTime todayStart = utc(today, zone);
        LocalDateTime last7Start = utc(today.minusDays(6), zone);
        LocalDateTime last30Start = utc(today.minusDays(CHART_DAYS - 1), zone);

        UserCounts counts = userCounts(todayStart, last7Start, last30Start);

        return new AdminDashboardResponse(
                now,
                zone.getId(),
                new Users(counts.total, counts.today, counts.last7, counts.last30,
                        counts.verified, counts.total - counts.verified),
                signupsByDay(today, zone, last30Start),
                new Activity(presence.onlineUserCount(), counts.activeToday, counts.active7, counts.active30),
                new Retention(counts.cohort, counts.returned),
                new Methods(counts.googleOnly, counts.passwordOnly, counts.both),
                locales(),
                content(last7Start),
                recentSignups(),
                topBoards(last7Start));
    }

    private static LocalDateTime utc(LocalDate day, ZoneId zone) {
        return LocalDateTime.ofInstant(day.atStartOfDay(zone).toInstant(), ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    // ── Kullanıcılar: tek geçişte bütün sayılar ─────────────────────────

    private record UserCounts(long total, long today, long last7, long last30, long verified,
                              long activeToday, long active7, long active30,
                              long cohort, long returned,
                              long googleOnly, long passwordOnly, long both) {}

    /**
     * Geri gelenler: 7 ile 30 gün önce kayıt olanlar (son 30 günün ilk 23 günü)
     * içinden son 7 günde görülenler. Kayıt günü sayılmasın diye kohort son 7
     * günü dışarıda bırakır: yeni gelen herkes zaten "son 7 günde görüldü".
     */
    private UserCounts userCounts(LocalDateTime todayStart, LocalDateTime last7Start, LocalDateTime last30Start) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)                                                                    AS total,
                       COUNT(*) FILTER (WHERE u.created_at >= ?)                                   AS today,
                       COUNT(*) FILTER (WHERE u.created_at >= ?)                                   AS last7,
                       COUNT(*) FILTER (WHERE u.created_at >= ?)                                   AS last30,
                       COUNT(*) FILTER (WHERE u.enabled)                                           AS verified,
                       COUNT(*) FILTER (WHERE u.last_seen_at >= ?)                                 AS active_today,
                       COUNT(*) FILTER (WHERE u.last_seen_at >= ?)                                 AS active7,
                       COUNT(*) FILTER (WHERE u.last_seen_at >= ?)                                 AS active30,
                       COUNT(*) FILTER (WHERE u.created_at >= ? AND u.created_at < ?)              AS cohort,
                       COUNT(*) FILTER (WHERE u.created_at >= ? AND u.created_at < ?
                                          AND u.last_seen_at >= ?)                                 AS returned,
                       COUNT(*) FILTER (WHERE g.user_id IS NOT NULL AND u.password IS NULL)        AS google_only,
                       COUNT(*) FILTER (WHERE g.user_id IS NULL AND u.password IS NOT NULL)        AS password_only,
                       COUNT(*) FILTER (WHERE g.user_id IS NOT NULL AND u.password IS NOT NULL)    AS both_methods
                FROM users u
                LEFT JOIN (SELECT DISTINCT user_id FROM user_identities WHERE provider = 'google') g
                       ON g.user_id = u.id
                """,
                (rs, i) -> new UserCounts(
                        rs.getLong("total"), rs.getLong("today"), rs.getLong("last7"), rs.getLong("last30"),
                        rs.getLong("verified"),
                        rs.getLong("active_today"), rs.getLong("active7"), rs.getLong("active30"),
                        rs.getLong("cohort"), rs.getLong("returned"),
                        rs.getLong("google_only"), rs.getLong("password_only"), rs.getLong("both_methods")),
                todayStart, last7Start, last30Start,
                todayStart, last7Start, last30Start,
                last30Start, last7Start,
                last30Start, last7Start, last7Start);
    }

    /**
     * Son 30 günün her günü için kayıt sayısı; kaydın olmadığı gün 0. Günler
     * panelin sahibinin saatine göre kesilir (gece yarısı orada).
     */
    private List<DayCount> signupsByDay(LocalDate today, ZoneId zone, LocalDateTime from) {
        Map<LocalDate, Long> byDay = new HashMap<>();
        jdbc.query("""
                SELECT ((created_at AT TIME ZONE 'UTC') AT TIME ZONE ?)::date AS day, COUNT(*) AS n
                FROM users
                WHERE created_at >= ?
                GROUP BY 1
                """,
                (RowCallbackHandler) rs -> byDay.put(rs.getObject("day", LocalDate.class), rs.getLong("n")),
                zone.getId(), from);

        List<DayCount> days = new ArrayList<>(CHART_DAYS);
        for (int back = CHART_DAYS - 1; back >= 0; back--) {
            LocalDate day = today.minusDays(back);
            days.add(new DayCount(day, byDay.getOrDefault(day, 0L)));
        }
        return days;
    }

    private List<LocaleCount> locales() {
        return jdbc.query("""
                SELECT locale, COUNT(*) AS n FROM users GROUP BY locale ORDER BY n DESC, locale
                """,
                (rs, i) -> new LocaleCount(rs.getString("locale"), rs.getLong("n")));
    }

    /** Var olan satırlar; tek turda dört tablo. */
    private Content content(LocalDateTime last7Start) {
        return jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM workspaces)                      AS workspaces,
                       (SELECT COUNT(*) FROM workspaces WHERE created_at >= ?) AS workspaces7,
                       (SELECT COUNT(*) FROM boards)                          AS boards,
                       (SELECT COUNT(*) FROM boards WHERE created_at >= ?)     AS boards7,
                       (SELECT COUNT(*) FROM cards)                           AS cards,
                       (SELECT COUNT(*) FROM cards WHERE created_at >= ?)      AS cards7,
                       (SELECT COUNT(*) FROM comments)                        AS comments,
                       (SELECT COUNT(*) FROM comments WHERE created_at >= ?)   AS comments7
                """,
                (rs, i) -> new Content(
                        new Counter(rs.getLong("workspaces"), rs.getLong("workspaces7")),
                        new Counter(rs.getLong("boards"), rs.getLong("boards7")),
                        new Counter(rs.getLong("cards"), rs.getLong("cards7")),
                        new Counter(rs.getLong("comments"), rs.getLong("comments7"))),
                last7Start, last7Start, last7Start, last7Start);
    }

    /**
     * En yeni 50 hesap. Giriş yöntemi ve pano sayısı satır başına alt sorgu,
     * ama hepsi tek sorguda ve indeksli (user_identities.user_id,
     * board_members.user_id): 50 satır, 50 indeks okuması.
     */
    private List<RecentSignup> recentSignups() {
        return jdbc.query("""
                SELECT u.first_name, u.last_name, u.email, u.created_at, u.enabled, u.last_seen_at,
                       u.password IS NOT NULL AS has_password,
                       EXISTS (SELECT 1 FROM user_identities i
                               WHERE i.user_id = u.id AND i.provider = 'google') AS google,
                       (SELECT COUNT(*) FROM board_members m WHERE m.user_id = u.id) AS boards
                FROM users u
                ORDER BY u.created_at DESC, u.id DESC
                LIMIT ?
                """,
                (rs, i) -> new RecentSignup(
                        (rs.getString("first_name") + " " + rs.getString("last_name")).trim(),
                        rs.getString("email"),
                        instant(rs, "created_at"),
                        method(rs.getBoolean("google"), rs.getBoolean("has_password")),
                        rs.getBoolean("enabled"),
                        instant(rs, "last_seen_at"),
                        rs.getLong("boards")),
                RECENT_LIMIT);
    }

    private static String method(boolean google, boolean password) {
        if (google && password) {
            return "both";
        }
        return google ? "google" : "password";
    }

    /**
     * Son 7 günde en çok işlem gören panolar. Önce hareketler sayılır (BRIN
     * indeksi eski kayıtları atlar), en çok 10 pano seçilir; ad ve üye sayısı
     * yalnızca o 10 pano için okunur. Kart başlığı ya da hareketin içeriği okunmaz.
     */
    private List<ActiveBoard> topBoards(LocalDateTime since) {
        return jdbc.query("""
                SELECT b.name, a.n AS activity,
                       (SELECT COUNT(*) FROM board_members m WHERE m.board_id = b.id) AS members
                FROM (SELECT board_id, COUNT(*) AS n
                      FROM board_activities
                      WHERE created_at >= ?
                      GROUP BY board_id
                      ORDER BY n DESC, board_id
                      LIMIT ?) a
                JOIN boards b ON b.id = a.board_id
                ORDER BY a.n DESC, b.id
                """,
                (rs, i) -> new ActiveBoard(rs.getString("name"), rs.getLong("members"), rs.getLong("activity")),
                since, TOP_BOARDS_LIMIT);
    }
}
