package com.collabboard.security;

import com.collabboard.common.exception.TokenRefreshException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j // Lombok: otomatik olarak private static final Logger log = ... üretir
@Service
public class RefreshTokenService {

    /**
     * Token rotation sonucu — hem yeni token hem userId'yi tek seferde taşır.
     * AuthService'in ayrıca validate çağırmasına gerek kalmaz.
     */
    public record RotationResult(String newToken, Long userId) {}

    // Redis'te token key'lerinin prefix'i — arama ve silme işlemlerinde karışıklığı önler
    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";

    // Genel amaçlı RedisTemplate; RedisConfig'de tanımlanan bean enjekte edilir
    private final RedisTemplate<String, Object> redisTemplate;

    // application.yml'den okunan refresh token ömrü (milisaniye cinsinden, 7 gün = 604800000 ms)
    @Value("${app.jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    // Constructor injection: Spring Boot tek constructor varsa otomatik enjeksiyon yapar
    public RefreshTokenService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Yeni bir refresh token oluşturur ve Redis'e kaydeder.
     *
     * @param userId token'ın ait olduğu kullanıcının ID'si
     * @return üretilen UUID tabanlı refresh token string'i
     */
    public String createRefreshToken(Long userId) {
        // UUID ile benzersiz, tahmin edilemez bir token üret (JWT değil, opaque token)
        String token = UUID.randomUUID().toString();

        // Redis key'i: "refresh_token:<uuid>" şeklinde oluştur
        String redisKey = REFRESH_TOKEN_PREFIX + token;

        // Redis'e userId'yi String olarak kaydet; TTL süresi dolunca Redis otomatik siler
        redisTemplate.opsForValue().set(redisKey, userId.toString(), refreshTokenExpiration, TimeUnit.MILLISECONDS);

        log.info("Yeni refresh token oluşturuldu — userId: {}", userId);
        return token;
    }

    /**
     * Refresh token'ı doğrular ve ilişkili userId'yi döner.
     *
     * @param token doğrulanacak refresh token string'i
     * @return token geçerliyse userId
     * @throws TokenRefreshException token Redis'te bulunamazsa (süresi dolmuş veya geçersiz)
     */
    public Long validateRefreshToken(String token) {
        String redisKey = REFRESH_TOKEN_PREFIX + token;

        // Redis'ten token'a karşılık gelen userId'yi oku
        Object value = redisTemplate.opsForValue().get(redisKey);

        // Token Redis'te yoksa: ya süresi dolmuştur ya da hiç var olmamıştır
        if (value == null) {
            log.warn("Geçersiz veya süresi dolmuş refresh token: {}", token);
            throw new TokenRefreshException(token, "Token bulunamadı veya süresi dolmuş.");
        }

        // Redis'ten gelen değer String olarak saklandığı için Long'a parse ediyoruz
        Long userId = Long.valueOf(value.toString());

        log.debug("Refresh token doğrulandı — userId: {}", userId);
        return userId;
    }

    /**
     * Refresh token'ı Redis'ten siler. Logout işleminde kullanılır.
     *
     * @param token silinecek refresh token string'i
     */
    public void deleteRefreshToken(String token) {
        String redisKey = REFRESH_TOKEN_PREFIX + token;

        // Redis'ten key'i sil; Boolean.TRUE dönerse silme başarılı
        Boolean deleted = redisTemplate.delete(redisKey);

        if (Boolean.TRUE.equals(deleted)) {
            log.info("Refresh token silindi (logout): {}", token);
        } else {
            // Silinecek token zaten yoksa uyar; bu durum normal olabilir (TTL dolmuş olabilir)
            log.warn("Silinmeye çalışılan refresh token Redis'te bulunamadı: {}", token);
        }
    }

    /**
     * Token rotation: eski token'ı doğrula → sil → yeni token üret.
     * Bu yöntem güvenliği artırır; çalınmış bir token yeniden kullanılamaz.
     * Tek çağrıda hem yeni token hem userId döner — çift validate sorununu önler.
     *
     * @param oldToken yenilenecek eski refresh token
     * @return RotationResult — yeni token ve userId içerir
     * @throws TokenRefreshException eski token geçersizse
     */
    public RotationResult rotateRefreshToken(String oldToken) {
        // 1. Eski token'ı doğrula ve userId'yi al (geçersizse exception fırlatır)
        Long userId = validateRefreshToken(oldToken);

        // 2. Eski token'ı Redis'ten sil — artık kullanılamaz hale gelir
        deleteRefreshToken(oldToken);

        // 3. Aynı kullanıcı için yepyeni bir refresh token oluştur
        String newToken = createRefreshToken(userId);

        log.info("Token rotation tamamlandı — userId: {}, eskiToken: {}, yeniToken: {}", userId, oldToken, newToken);
        return new RotationResult(newToken, userId);
    }
}
