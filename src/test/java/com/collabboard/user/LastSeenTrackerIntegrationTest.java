package com.collabboard.user;

import com.collabboard.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.assertj.core.api.Assertions;
import org.assertj.core.data.TemporalUnitOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Son görülme" (users.last_seen_at, ADR 0024): kimlikli istekte ve jeton
 * yenilemede yazılır, ama kişi başına en fazla 5 dakikada bir.
 *
 * İki fren ayrı ayrı sınanır: bellekteki (aynı sunucu ikinci kez veritabanına
 * gitmez) ve veritabanındaki (bellek boş olsa da, örneğin başka bir sunucudan
 * gelse de, yakın zamanda yazılmış değer ezilmez).
 */
class LastSeenTrackerIntegrationTest extends IntegrationTestBase {

    @Autowired
    LastSeenTracker tracker;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("Kimlikli istek son görülmeyi yazar; kayıt ve giriş tek başına yazmaz")
    void istekYazar() {
        String email = uniqueEmail("gorulme");
        String token = registerAndLogin("Son", "Görülme", email);
        assertThat(lastSeen(email)).isNull();   // kayıt, doğrulama ve giriş kimliksiz uçlar

        rest.exchange("/api/users/me", HttpMethod.GET, new HttpEntity<>(authHeaders(token)), JsonNode.class);
        assertThat(lastSeen(email)).isCloseTo(nowUtc(), within());
    }

    @Test
    @DisplayName("Jeton yenileme de yazar: sayfayı açık bırakıp yalnızca canlı bağlantıyla çalışan kişi")
    void yenilemeYazar() {
        String email = uniqueEmail("yenileme");
        registerAndLogin("Jeton", "Yenileyen", email);
        String refresh = rest.postForEntity("/api/auth/login", Map.of("email", email, "password", PASSWORD),
                JsonNode.class).getBody().get("refreshToken").asText();
        assertThat(lastSeen(email)).isNull();

        rest.postForEntity("/api/auth/refresh", Map.of("refreshToken", refresh), JsonNode.class);
        assertThat(lastSeen(email)).isCloseTo(nowUtc(), within());
    }

    @Test
    @DisplayName("Bellekteki fren: aralık dolmadan ikinci kez veritabanına gidilmez")
    void bellekFreni() {
        String email = uniqueEmail("fren");
        registerAndLogin("Bellek", "Freni", email);

        tracker.seen(email);
        assertThat(lastSeen(email)).isCloseTo(nowUtc(), within());

        // Değeri elle eskit: veritabanının freni artık yazmaya izin verirdi.
        LocalDateTime old = nowUtc().minusHours(3);
        setLastSeen(email, old);
        tracker.seen(email);
        assertThat(lastSeen(email)).isEqualTo(old);
    }

    @Test
    @DisplayName("Veritabanındaki fren: bellek boşken de 5 dakikadan yeni değer ezilmez, eskisi güncellenir")
    void veritabaniFreni() {
        String email = uniqueEmail("dbfren");
        registerAndLogin("Veritabanı", "Freni", email);

        LocalDateTime recent = nowUtc().minusMinutes(2);
        setLastSeen(email, recent);
        tracker.forgetAll();   // başka bir sunucudan gelen istek gibi: bu sunucunun belleğinde kayıt yok
        tracker.seen(email);
        assertThat(lastSeen(email)).isEqualTo(recent);

        setLastSeen(email, nowUtc().minusMinutes(6));
        tracker.forgetAll();
        tracker.seen(email);
        assertThat(lastSeen(email)).isCloseTo(nowUtc(), within());
    }

    private LocalDateTime lastSeen(String email) {
        return jdbc.queryForObject("SELECT last_seen_at FROM users WHERE email = ?", LocalDateTime.class, email);
    }

    private void setLastSeen(String email, LocalDateTime at) {
        jdbc.update("UPDATE users SET last_seen_at = ? WHERE email = ?", at, email);
    }

    private static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    private static TemporalUnitOffset within() {
        return Assertions.within(30, ChronoUnit.SECONDS);
    }
}
