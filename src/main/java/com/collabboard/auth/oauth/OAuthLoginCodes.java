package com.collabboard.auth.oauth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * Google girişinin sonunda tarayıcıya verilen tek kullanımlık kod.
 *
 * NEDEN JETONLAR DOĞRUDAN VERİLMİYOR? Google'dan dönüş bir tarayıcı
 * yönlendirmesi; sunucunun tarayıcıya bir şey iletebileceği tek yer adres.
 * Oturum jetonları adrese yazılırsa tarayıcı geçmişine, vekil ve sunucu
 * loglarına, Referer başlığına düşer. Bunun yerine adrese anlamsız, kısa
 * ömürlü bir kod yazılır; arayüz bu kodu hemen POST gövdesiyle jetonlara
 * çevirir (/api/auth/oauth/exchange). Kod sızsa bile:
 *  - 60 saniye yaşar,
 *  - bir kez kullanılır (okunurken silinir, GETDEL; iki istek aynı anda
 *    gelse de yalnızca biri alır),
 *  - akışı başlatan tarayıcının çereziyle birlikte gelmezse işe yaramaz.
 */
@Component
public class OAuthLoginCodes {

    static final String KEY_PREFIX = "oauth_login_code:";
    static final Duration TTL = Duration.ofSeconds(60);

    /** 32 bayt = 256 bit; tahmin edilemez. */
    private static final int CODE_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;

    public OAuthLoginCodes(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * @param browserFingerprint akışı başlatan tarayıcının bağ özeti (OAuthBrowserBinding)
     */
    public String issue(Long userId, String browserFingerprint) {
        byte[] bytes = new byte[CODE_BYTES];
        RANDOM.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(KEY_PREFIX + code, userId + ":" + browserFingerprint, TTL);
        return code;
    }

    /**
     * Kodu kullanır. Bilinmeyen, süresi dolmuş, daha önce kullanılmış ya da başka
     * tarayıcıdan gelen kod için boş döner. Başka tarayıcıdan gelen kod da
     * harcanır: bir daha kimse kullanamaz.
     */
    public Optional<Long> redeem(String code, Optional<String> browserFingerprint) {
        if (code == null || code.isBlank() || code.length() > 128) {
            return Optional.empty();
        }
        String value = redis.opsForValue().getAndDelete(KEY_PREFIX + code);
        if (value == null) {
            return Optional.empty();
        }
        int sep = value.indexOf(':');
        if (sep < 0 || !OAuthBrowserBinding.same(value.substring(sep + 1), browserFingerprint)) {
            return Optional.empty();
        }
        return Optional.of(Long.valueOf(value.substring(0, sep)));
    }
}
