package com.collabboard.security.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

import java.time.Duration;

/**
 * Korunan bir uç ve ona uygulanan sınır.
 *
 * Sınırlar uca göre değişir çünkü kötüye kullanımın MALİYETİ değişir:
 *
 *  - LOGIN: şifre deneme saldırısına açık. Sık denenmesi normaldir (insan
 *    şifresini yanlış yazar), ama dakikada onlarca deneme insan davranışı değildir.
 *
 *  - REGISTER: her istek veritabanına satır YAZAR ve posta gönderir. Serbest
 *    bırakılırsa hem tablo şişer hem posta kotamız tükenir.
 *
 *  - RESEND: en pahalısı. Saldırgan bunu, seçtiği bir adrese posta yağdırmak
 *    için kullanabilir — bedeli bizim değil, o kişinin gelen kutusunun.
 *    Bu yüzden en sıkı sınır burada.
 *
 *  - REFRESH: meşru istemci 15 dakikada bir çağırır. Cömert bir sınır bile
 *    çalınmış bir jetonla yapılan deneme yanılmayı görünür kılar.
 *
 * Sayaç IP başınadır. Kusurlu bir anahtar: aynı ofisten çıkan herkes tek IP
 * paylaşır, mobil ağlarda IP sık değişir. Yine de kimlik gerektirmeyen uçlarda
 * elimizdeki tek ölçüttür ve amaç kesin adalet değil, ucuz kötüye kullanımı
 * pahalı hâle getirmektir.
 */
public enum RateLimitPolicy {

    LOGIN("/api/auth/login", 10, Duration.ofMinutes(1),
            "Çok fazla giriş denemesi yapıldı. Bir dakika sonra tekrar dene."),

    REGISTER("/api/auth/register", 5, Duration.ofHours(1),
            "Çok fazla kayıt isteği gönderildi. Bir saat sonra tekrar dene."),

    RESEND("/api/auth/resend-verification", 3, Duration.ofHours(1),
            "Doğrulama postası çok sık istendi. Bir saat sonra tekrar dene."),

    REFRESH("/api/auth/refresh", 30, Duration.ofMinutes(1),
            "Çok fazla oturum yenileme isteği. Bir dakika sonra tekrar dene."),

    /** Doğrulama postasıyla aynı gerekçe: bedeli başkasının gelen kutusu. */
    FORGOT_PASSWORD("/api/auth/forgot-password", 3, Duration.ofHours(1),
            "Şifre sıfırlama çok sık istendi. Bir saat sonra tekrar dene."),

    /**
     * Jeton 256 bit, tahmin edilemez; sınır kaba kuvvete karşı değil (o zaten
     * imkânsız), ucun gürültü kaynağı olmasına karşı.
     */
    RESET_PASSWORD("/api/auth/reset-password", 10, Duration.ofHours(1),
            "Çok fazla şifre sıfırlama denemesi. Bir saat sonra tekrar dene.");

    private final String path;
    private final int capacity;
    private final Duration period;
    private final String message;

    RateLimitPolicy(String path, int capacity, Duration period, String message) {
        this.path = path;
        this.capacity = capacity;
        this.period = period;
        this.message = message;
    }

    public String path() {
        return path;
    }

    public String message() {
        return message;
    }

    /** Sınır aşıldığında istemciye kaç saniye sonra deneyeceğini söylemek için. */
    public long retryAfterSeconds() {
        return period.toSeconds();
    }

    /**
     * Bu politika için yeni bir kova.
     *
     * "Greedy" doldurma: jetonlar periyot boyunca damla damla geri gelir, periyot
     * sonunda toptan değil. Toptan yenileme, sınıra takılan herkesin aynı anda
     * yeniden denemesine yol açar (gürleyen sürü); damlama bunu yayar.
     */
    public Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillGreedy(capacity, period)
                        .build())
                .build();
    }
}
