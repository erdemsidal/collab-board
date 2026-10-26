package com.collabboard.security.ratelimit;

import com.collabboard.common.exception.TooManyRequestsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Hesap başına başarısız giriş sınırı (ADR 0025, güvenlik incelemesi Y3).
 *
 * NEDEN IP SINIRI YETMİYOR? RateLimitFilter'ın LOGIN politikası IP başına
 * dakikada 10 deneme. Çok sayıda IP'den (bot ağı, mobil ağ) gelen saldırgan
 * için bu sınır, tek bir hesaba yapılan toplam denemeyi sınırlamaz. Bu sınıf
 * IP'ye bakmaz; hedef hesaba bakar.
 *
 * KURAL: Bir e-posta için {@value #MAX_FAILURES} başarısız deneme olunca, ilk
 * hatadan itibaren pencere (varsayılan 15 dakika) dolana kadar o hesapla giriş denenemez (429).
 *  - Pencere SABİT ve KISA: ilk hatayla başlar, uzamaz, katlanarak büyümez.
 *    Saldırgan hesabı kalıcı olarak kilitleyemez; en kötü durumda kişi en fazla
 *    15 dakika bekler ya da "Şifremi unuttum"la şifresini sıfırlar (sıfırlama
 *    sayacı da temizler). Hesap kilitleme (lockout) bilerek YOK: o, saldırganın
 *    elinde bir hizmet engelleme aracı olurdu.
 *  - Başarılı giriş sayacı sıfırlar.
 *  - Kilitliyken şifre HİÇ denetlenmez (BCrypt çalışmaz): doğru şifre bile
 *    beklemek zorunda, yoksa kilit tahmin edilen şifreyi doğrulamaya yarardı.
 *
 * Sayaç Redis'te: birden çok sunucuda da tek sayaç. Anahtar e-postanın kendisi
 * değil SHA-256 özeti; Redis'e bakan biri e-posta listesini görmez. E-posta
 * küçük harfe çevrilip boşlukları atılarak sayılır: "Ali@X.com " ile "ali@x.com"
 * aynı hesaba yapılan denemedir.
 *
 * Kayıtlı olmayan e-postalar da sayılır: hesabın var olup olmadığı yanıttan
 * anlaşılmasın (Spring de ikisine aynı "hatalı giriş" yanıtını veriyor).
 */
@Component
public class AccountLoginThrottle {

    private static final Logger log = LoggerFactory.getLogger(AccountLoginThrottle.class);

    /** Pencere başına izin verilen başarısız deneme. */
    static final int MAX_FAILURES = 10;

    private static final String KEY_PREFIX = "auth:login-failures:";

    private final StringRedisTemplate redis;
    private final boolean enabled;

    /**
     * Sayacın ömrü: ilk başarısız denemeden itibaren. Varsayılan 15 dakika;
     * testler pencerenin dolduğunu beklemek için kısaltır.
     */
    private final Duration window;

    public AccountLoginThrottle(StringRedisTemplate redis,
                                @Value("${app.rate-limit.enabled:true}") boolean enabled,
                                @Value("${app.rate-limit.login-account-window:15m}") Duration window) {
        this.redis = redis;
        this.enabled = enabled;
        this.window = window;
    }

    /**
     * Giriş denemesinden ÖNCE çağrılır. Hesap pencere dolana kadar kapalıysa 429.
     */
    public void checkAllowed(String email) {
        if (!enabled || email == null) {
            return;
        }
        String key = key(email);
        String count = redis.opsForValue().get(key);
        if (count == null || Integer.parseInt(count) < MAX_FAILURES) {
            return;
        }
        long seconds = Math.max(1, ttlSeconds(key));
        long minutes = (seconds + 59) / 60;
        throw new TooManyRequestsException("error.rateLimit.loginAccount", seconds, minutes);
    }

    /** Hatalı şifre: sayacı artır. İlk hatada pencere başlar. */
    public void recordFailure(String email) {
        if (!enabled || email == null) {
            return;
        }
        String key = key(email);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, window);
        }
        if (count != null && count == MAX_FAILURES) {
            // E-posta loga yazılmaz (KVKK); özetin başı ilişkilendirmeye yeter.
            log.warn("Hesap başına giriş sınırına ulaşıldı; {} dakika kapalı — hesap özeti: {}",
                    window.toMinutes(), key.substring(KEY_PREFIX.length(), KEY_PREFIX.length() + 12));
        }
    }

    /** Başarılı giriş ya da şifre sıfırlama: sayaç silinir. */
    public void reset(String email) {
        if (email != null) {
            redis.delete(key(email));
        }
    }

    private long ttlSeconds(String key) {
        Long ttl = redis.getExpire(key);
        if (ttl == null || ttl < 0) {
            // Süresiz kalmış bir sayaç olmamalı; olursa pencereyi yeniden kur.
            redis.expire(key, window);
            return window.toSeconds();
        }
        return ttl;
    }

    static String key(String email) {
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 yok", e);
        }
    }
}
