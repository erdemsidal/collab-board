package com.collabboard.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * "Bu andan önce verilmiş erişim jetonları canlı bağlantı için geçmez" (ADR 0025).
 *
 * Şifre değişince ya da sıfırlanınca kişinin bütün oturumları kapanır ve canlı
 * bağlantıları koparılır. Ama elindeki erişim jetonu (JWT) 15 dakika daha
 * geçerlidir; kötü niyetli bir istemci onunla hemen yeniden bağlanabilirdi.
 * Bu sınıf kişi için bir kesim anı yazar; WebSocket CONNECT'te jetonun veriliş
 * anı bundan önceyse bağlantı reddedilir.
 *
 * Yalnızca canlı bağlantıda (CONNECT ve jeton yenileme) bakılır, her REST
 * isteğinde değil: REST'te jeton ömrü kısa (15 dk) ve her isteğe Redis sorgusu
 * eklemek bu ölçekte gereksiz (güvenlik incelemesi, "yapılmaması gerekenler").
 * Canlı bağlantı ise saatlerce açık kalabildiği için orada önemli.
 *
 * Kayıt erişim jetonunun ömrü kadar yaşar: ondan sonra kesimden önceki bütün
 * jetonların süresi zaten dolmuştur.
 *
 * Saniye çözünürlüğü: JWT'nin "iat" alanı saniyedir. Kesimle aynı saniyede
 * verilen jeton geçer; böylece şifreyi değiştiren cihaza hemen verilen yeni
 * jeton reddedilmez. Bedeli, kesimden hemen önceki aynı saniyede verilmiş bir
 * jetonun da geçmesi; ihmal edilebilir.
 */
@Component
public class AccessTokenCutoff {

    private static final String KEY_PREFIX = "auth:access-cutoff:";

    private final StringRedisTemplate redis;
    private final Duration accessTokenLifetime;

    public AccessTokenCutoff(StringRedisTemplate redis,
                             @Value("${app.jwt.access-token-expiration}") Duration accessTokenLifetime) {
        this.redis = redis;
        this.accessTokenLifetime = accessTokenLifetime;
    }

    /** Kişinin şu ana kadar verilmiş bütün erişim jetonlarını canlı bağlantı için geçersiz say. */
    public void cutOff(String email) {
        long now = Instant.now().getEpochSecond();
        redis.opsForValue().set(KEY_PREFIX + email, Long.toString(now), accessTokenLifetime.plusMinutes(1));
    }

    /** Jeton kesimden önce mi verilmiş? Veriliş anı yoksa güvenli tarafta kal: evet. */
    public boolean isCutOff(String email, Date issuedAt) {
        String cutoff = redis.opsForValue().get(KEY_PREFIX + email);
        if (cutoff == null) {
            return false;
        }
        if (issuedAt == null) {
            return true;
        }
        return issuedAt.toInstant().getEpochSecond() < Long.parseLong(cutoff);
    }
}
