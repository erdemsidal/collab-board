package com.collabboard.security;

import com.collabboard.common.exception.TokenRefreshException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Oturumlar (refresh token): üretme, döndürme, kapatma (ADR 0025).
 *
 * AİLE (family): Her giriş yeni bir "oturum ailesi" açar. Jeton "<aile>.<sır>"
 * biçimindedir. Her yenilemede (rotation) sır değişir, aile aynı kalır. Redis'te
 * aile başına TEK kayıt tutulur: kime ait olduğu ve ŞU ANKİ jetonun özeti
 * (SHA-256). Jetonun kendisi Redis'e yazılmaz; Redis'in kopyasını ele geçiren
 * biri oturum açamaz.
 *
 * YENİDEN KULLANIM (reuse detection): Bir jeton bir kez kullanılınca yerine
 * yenisi geçer. Ailesi hâlâ açıkken ESKİ bir jeton geri gelirse, o jetonu iki
 * kişi kullanıyor demektir: kullanıcı ve onu çalan. Hangisinin hangisi olduğunu
 * bilemeyiz; bu yüzden AİLENİN TAMAMI kapatılır ve olay loga yazılır. İki taraf
 * da yeniden giriş yapmak zorunda kalır; şifreyi bilmeyen saldırgan dışarıda kalır.
 * Kişinin başka cihazlardaki oturumları (başka aileler) etkilenmez.
 *
 * ATOMİK DÖNDÜRME: Karşılaştırma ve değiştirme tek bir Redis betiğinde (Lua)
 * yapılır. Eskiden "oku → sil → yenisini yaz" üç ayrı adımdı; aynı jetonla aynı
 * anda gelen iki istek ikisi de başarılı olabiliyordu.
 *
 * Arayüz aynı tarayıcıdaki sekmelerin yenilemesini sıraya koyar (collab-api.js,
 * Web Locks), yani meşru bir istemci aynı jetonu iki kez göndermez. Tek istisna,
 * yanıtı ağda kaybolan bir yenilemedir; o kişi bir kez yeniden giriş yapar.
 *
 * GEÇİŞ: Eski biçimdeki jetonlar (düz UUID) artık tanınmaz; bu sürüm
 * yayınlandıktan sonra herkes bir kez yeniden giriş yapar.
 */
@Slf4j
@Service
public class RefreshTokenService {

    /**
     * Token rotation sonucu — hem yeni token hem userId'yi tek seferde taşır.
     * AuthService'in ayrıca validate çağırmasına gerek kalmaz.
     */
    public record RotationResult(String newToken, Long userId) {}

    /** "refresh_family:<aile>" → { user: <id>, token: <şu anki jetonun SHA-256 özeti> } */
    private static final String FAMILY_PREFIX = "refresh_family:";

    /**
     * Kullanıcı başına açık aileler: "user_refresh_families:<userId>" → {aile...}.
     * Şifre değişince ya da sıfırlanınca bütün oturumları kapatmak için.
     * Süresi dolan ailelerin adı burada kalabilir; zararsız (olmayan anahtarı silmek
     * bir şey yapmaz). Dizinin kendisi de son aileyle birlikte süresi dolarak kaybolur.
     */
    private static final String USER_INDEX_PREFIX = "user_refresh_families:";

    /** Aile kimliği 16, sır 32 rastgele bayt; ikisi de base64url, dolgusuz. */
    private static final Pattern TOKEN_FORMAT = Pattern.compile("([A-Za-z0-9_-]{22})\\.[A-Za-z0-9_-]{43}");

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();

    /**
     * Karşılaştır ve değiştir, tek adımda.
     * KEYS[1] aile; ARGV[1] gelen jetonun özeti, ARGV[2] yenisinin özeti, ARGV[3] ömür (ms).
     * Dönüş: "missing" | "reused:<userId>" | "ok:<userId>".
     */
    private static final RedisScript<String> ROTATE = new DefaultRedisScript<>("""
            local current = redis.call('HGET', KEYS[1], 'token')
            if not current then return 'missing' end
            local user = redis.call('HGET', KEYS[1], 'user')
            if current ~= ARGV[1] then
              redis.call('DEL', KEYS[1])
              return 'reused:' .. user
            end
            redis.call('HSET', KEYS[1], 'token', ARGV[2])
            redis.call('PEXPIRE', KEYS[1], ARGV[3])
            return 'ok:' .. user
            """, String.class);

    private final StringRedisTemplate redis;

    // Oturum ömrü (application.yml, varsayılan 30 gün). Her rotasyonda süre
    // baştan başlar; kullanıldıkça uzayan bir oturum böyle oluşur.
    @Value("${app.jwt.refresh-token-expiration}")
    private Duration refreshTokenExpiration;

    public RefreshTokenService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Yeni bir oturum (aile) açar ve ilk jetonunu döner. Her giriş bunu çağırır.
     *
     * @param userId token'ın ait olduğu kullanıcının ID'si
     */
    public String createRefreshToken(Long userId) {
        String family = randomToken(16);
        String token = family + "." + randomToken(32);

        String key = FAMILY_PREFIX + family;
        redis.opsForHash().putAll(key, Map.of("user", userId.toString(), "token", hash(token)));
        redis.expire(key, refreshTokenExpiration);
        touchIndex(userId, family);

        log.info("Yeni oturum açıldı — userId: {}, aile: {}", userId, mask(family));
        return token;
    }

    /**
     * Döndürme: gelen jeton ailesinin şu anki jetonuysa yenisini verir.
     *
     * @throws TokenRefreshException jeton geçersizse, süresi dolmuşsa ya da daha
     *         önce kullanılmışsa (o durumda aile de kapatılır)
     */
    public RotationResult rotateRefreshToken(String oldToken) {
        String family = familyOf(oldToken);
        if (family == null) {
            log.warn("Tanınmayan biçimde refresh token: {}", mask(oldToken));
            throw new TokenRefreshException();
        }

        String newToken = family + "." + randomToken(32);
        String outcome = redis.execute(ROTATE, List.of(FAMILY_PREFIX + family),
                hash(oldToken), hash(newToken), String.valueOf(refreshTokenExpiration.toMillis()));

        if (outcome == null || "missing".equals(outcome)) {
            log.warn("Geçersiz veya süresi dolmuş refresh token — aile: {}", mask(family));
            throw new TokenRefreshException();
        }
        Long userId = Long.valueOf(outcome.substring(outcome.indexOf(':') + 1));
        if (outcome.startsWith("reused:")) {
            redis.opsForSet().remove(USER_INDEX_PREFIX + userId, family);
            // Güvenlik olayı: jeton iki kişinin elinde. Aile kapandı.
            log.warn("GÜVENLİK: kullanılmış refresh token yeniden geldi; oturum ailesi kapatıldı — "
                    + "userId: {}, aile: {}", userId, mask(family));
            throw new TokenRefreshException();
        }

        touchIndex(userId, family);
        log.info("Token rotation tamamlandı — userId: {}", userId);
        return new RotationResult(newToken, userId);
    }

    /**
     * Çıkış: jetonun oturumunu (ailesini) kapatır. Jeton ailenin şu anki jetonu
     * değilse hiçbir şey yapılmaz; elinde eski bir jeton olan biri başkasının
     * oturumunu kapatamaz.
     */
    public void deleteRefreshToken(String token) {
        String family = familyOf(token);
        if (family == null) {
            log.warn("Çıkışta tanınmayan biçimde refresh token: {}", mask(token));
            return;
        }
        String key = FAMILY_PREFIX + family;
        List<Object> fields = redis.opsForHash().multiGet(key, List.of("user", "token"));
        Object user = fields.get(0);
        if (user == null || !hash(token).equals(fields.get(1))) {
            log.warn("Çıkışta geçersiz ya da eski refresh token — aile: {}", mask(family));
            return;
        }
        redis.delete(key);
        redis.opsForSet().remove(USER_INDEX_PREFIX + user, family);
        log.info("Oturum kapatıldı (çıkış) — userId: {}, aile: {}", user, mask(family));
    }

    /**
     * Kullanıcının bütün oturumlarını kapatır.
     *
     * Şifre değişince ve sıfırlanınca çağrılır: şifreyi ele geçiren biri o sırada
     * oturum açmışsa, yalnızca şifreyi değiştirmek onu dışarı atmaz — elindeki
     * refresh token oturum süresi boyunca yeni erişim jetonu üretmeye devam ederdi.
     *
     * Erişim jetonları (JWT) sunucuda tutulmadığı için geri alınamaz; en fazla
     * 15 dakikalık ömürleri dolana kadar REST'te geçerli kalırlar. Kabul edilen
     * bir boşluk. Canlı bağlantılar ise hemen kapatılır (SessionTermination).
     *
     * @return kapatılan oturum sayısı
     */
    public int revokeAll(Long userId) {
        String index = USER_INDEX_PREFIX + userId;
        Set<String> families = redis.opsForSet().members(index);
        if (families == null || families.isEmpty()) {
            return 0;
        }
        families.forEach(f -> redis.delete(FAMILY_PREFIX + f));
        redis.delete(index);

        log.info("Kullanıcının tüm oturumları kapatıldı — userId: {}, oturum: {}", userId, families.size());
        return families.size();
    }

    private void touchIndex(Long userId, String family) {
        String index = USER_INDEX_PREFIX + userId;
        redis.opsForSet().add(index, family);
        redis.expire(index, refreshTokenExpiration);
    }

    /** "<aile>.<sır>" biçimindeyse aile kimliği, değilse null. */
    private static String familyOf(String token) {
        if (token == null) {
            return null;
        }
        var m = TOKEN_FORMAT.matcher(token);
        return m.matches() ? m.group(1) : null;
    }

    private static String randomToken(int bytes) {
        byte[] buf = new byte[bytes];
        RANDOM.nextBytes(buf);
        return BASE64URL.encodeToString(buf);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 yok", e);
        }
    }

    /**
     * Logda jetonun yalnızca ilk karakterleri görünür.
     *
     * Jetonun tamamı geçerli bir oturum anahtarıdır; loga düz metin yazılırsa,
     * logları okuyabilen herkes (izleme servisi, destek ekibi, sızan bir yedek)
     * o oturumu devralabilir. İlk birkaç karakter hata ayıklamaya yeter.
     */
    private static String mask(String token) {
        if (token == null || token.length() < 8) {
            return "***";
        }
        return token.substring(0, 8) + "…";
    }
}
