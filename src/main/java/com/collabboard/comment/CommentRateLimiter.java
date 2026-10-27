package com.collabboard.comment;

import com.collabboard.common.exception.TooManyRequestsException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kişi başına yorum sınırı: dakikada 10, saatte 100. Yazma ve düzenleme aynı
 * kovadan harcar (düzenleme de yeni kişi anıp posta gönderebilir).
 *
 * Neden var? Her yorum 20 kişiye kadar bildirim ve posta gönderebiliyor;
 * sınırsız olsa panoya erişen herkes ekip arkadaşlarının gelen kutusunu
 * doldurabilirdi. Sohbet eden bir insan bu sınırlara takılmaz.
 *
 * Neden RateLimitPolicy değil? O, kimliksiz uçlarda IP başına sayar ve istek
 * kimlik doğrulamadan önce kesilir. Burada kişi bellidir; aynı ofisten yazan
 * iki kişinin birbirinin kotasını yemesi için bir sebep yok. Kovalar yine de
 * aynı yaklaşımla bellekte (RateLimitFilter'daki not geçerli: iki sunucuda
 * etkin sınır iki katı).
 */
@Component
public class CommentRateLimiter {

    static final int PER_MINUTE = 10;
    static final int PER_HOUR = 100;

    private final Map<Long, Bucket> buckets = new ConcurrentHashMap<>();

    /** Sınır aşıldıysa TooManyRequestsException (429). */
    public void consume(Long userId) {
        Bucket bucket = buckets.computeIfAbsent(userId, id -> Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(PER_MINUTE).refillGreedy(PER_MINUTE, Duration.ofMinutes(1)).build())
                .addLimit(Bandwidth.builder().capacity(PER_HOUR).refillGreedy(PER_HOUR, Duration.ofHours(1)).build())
                .build());
        if (!bucket.tryConsume(1)) {
            throw new TooManyRequestsException("error.comment.rateLimit", 60);
        }
    }
}
