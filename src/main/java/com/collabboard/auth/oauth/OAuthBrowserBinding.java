package com.collabboard.auth.oauth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Google akışını, onu başlatan tarayıcıya bağlayan kısa ömürlü çerez.
 *
 * NEDEN? Spring Security bekleyen akışı normalde HttpSession'da tutar; oturum
 * çerezi akışı başlatan tarayıcıya bağlar. Biz akışı Redis'te, state değeriyle
 * tutuyoruz (oturumsuz, çok sunucuda çalışsın diye). Tek başına state, akışı
 * tarayıcıya bağlamaz: saldırgan kendi Google hesabıyla bir akış başlatıp
 * Google'ın döndüğü adresi (ya da sonundaki tek kullanımlık kodu) kurbana
 * açtırırsa, kurban farkında olmadan SALDIRGANIN hesabına girmiş olur ve
 * oraya yazdıklarını saldırgan okur ("login CSRF").
 *
 * Bu çerez o boşluğu kapatır: akış başlarken rastgele bir değer üretilir,
 * özeti akışla birlikte saklanır; Google'dan dönüşte ve kod takasında aynı
 * değer bu tarayıcıdan gelmelidir. Çerez bir oturum değildir: hiçbir sunucuda
 * tutulmaz, yalnızca karşılaştırılır ve içeriği asla çözümlenmez.
 *
 * SameSite=Lax: Google'dan dönüş üst düzey bir GET yönlendirmesidir; Lax çerez
 * bu yönlendirmede gönderilir, başka sitelerin arka plan isteklerinde gönderilmez.
 */
@Component
public class OAuthBrowserBinding {

    static final String COOKIE = "cb_oauth";
    static final Duration MAX_AGE = Duration.ofMinutes(10);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final boolean secure;

    public OAuthBrowserBinding(@Value("${app.base-url}") String baseUrl) {
        // Canlıda HTTPS: çerez yalnızca şifreli bağlantıda gider. Yerelde http.
        this.secure = baseUrl.startsWith("https://");
    }

    /** Yeni bir bağ üretir, çerezini yazar ve saklanacak özetini döner. */
    public String issue(HttpServletResponse response) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        write(response, value, MAX_AGE);
        return fingerprint(value);
    }

    /** Bu tarayıcının gönderdiği bağın özeti; çerez yoksa boş. */
    public Optional<String> fingerprint(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (COOKIE.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return Optional.of(fingerprint(cookie.getValue()));
            }
        }
        return Optional.empty();
    }

    /** İki özet aynı mı? Sabit sürede karşılaştırılır; süre farkından bilgi sızmaz. */
    public static boolean same(String expected, Optional<String> actual) {
        return expected != null && actual.isPresent()
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                actual.get().getBytes(StandardCharsets.US_ASCII));
    }

    /** Akış bitti; çerez silinir. */
    public void clear(HttpServletResponse response) {
        write(response, "", Duration.ZERO);
    }

    private void write(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    static String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 her JVM'de bulunur", e);
        }
    }
}
