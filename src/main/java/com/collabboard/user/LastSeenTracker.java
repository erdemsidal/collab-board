package com.collabboard.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Bu kişi siteye en son ne zaman geldi?" bilgisini (users.last_seen_at) tutar.
 *
 * Çağıranlar: oturum açmış her istek (JwtAuthenticationFilter) ve jeton
 * yenileme (AuthService). Sayfayı açık bırakıp yalnızca canlı bağlantıyla
 * çalışan kişi de görünür, çünkü tarayıcı jetonunu ~12 dakikada bir yeniler.
 *
 * Her istekte yazmak her tıklamayı bir UPDATE'e çevirirdi. İki kat fren var:
 *
 *  1. Bellek: bu sunucu kişi için son {@code interval} içinde yazdıysa veritabanına
 *     hiç gidilmez. İsteklerin neredeyse hepsi burada döner.
 *  2. Veritabanı: UPDATE yalnızca kayıtlı değer {@code interval}'dan eskiyse
 *     satırı değiştirir. Birden çok sunucu çalışırken de (her birinin belleği
 *     ayrı) kişi başına aralık başına en fazla bir yazma olur.
 *
 * Sonuç: bilgi en fazla {@code interval} kadar geride kalabilir; "bugün aktif"
 * sayısı için fazlasıyla yeterli. Yazma başarısız olursa isteği bozmaz, yalnızca loglanır.
 */
@Component
public class LastSeenTracker {

    private static final Logger log = LoggerFactory.getLogger(LastSeenTracker.class);

    /** Bellekte bu kadar kişiden fazlası birikirse aralığı geçmiş olanlar atılır. */
    private static final int MAX_REMEMBERED = 10_000;

    private final JdbcTemplate jdbc;
    private final Duration interval;

    /** küçük harfli e-posta → bu sunucunun en son yazdığı an (epoch ms) */
    private final Map<String, Long> lastWrite = new ConcurrentHashMap<>();

    public LastSeenTracker(JdbcTemplate jdbc,
                           @Value("${app.admin.last-seen-interval:5m}") Duration interval) {
        this.jdbc = jdbc;
        this.interval = interval;
    }

    /**
     * Kişiyi "şimdi görüldü" diye işaretler; aralık dolmadıysa hiçbir şey yapmaz.
     *
     * @param email oturumun sahibi (JWT'deki kullanıcı adı, users.email ile birebir)
     */
    public void seen(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        String key = email.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        long gap = interval.toMillis();

        // compute atomik: aynı anda gelen iki istekten yalnızca biri yazar.
        boolean[] due = {false};
        lastWrite.compute(key, (k, previous) -> {
            if (previous != null && now - previous < gap) {
                return previous;
            }
            due[0] = true;
            return now;
        });
        if (!due[0]) {
            return;
        }
        if (lastWrite.size() > MAX_REMEMBERED) {
            lastWrite.values().removeIf(at -> now - at >= gap);
        }

        // Diğer zaman sütunları gibi UTC duvar saati (bkz. AdminDashboardService).
        LocalDateTime at = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneOffset.UTC);
        try {
            jdbc.update("""
                    UPDATE users SET last_seen_at = ?
                    WHERE email = ? AND (last_seen_at IS NULL OR last_seen_at < ?)
                    """, at, email, at.minus(interval));
        } catch (DataAccessException e) {
            // Bir sonraki istek yeniden denesin.
            lastWrite.remove(key);
            log.warn("Son görülme yazılamadı: {}", e.getMessage());
        }
    }

    /** Belleği boşaltır; yalnızca testler (veritabanı frenini tek başına sınamak için). */
    void forgetAll() {
        lastWrite.clear();
    }
}
