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
            "error.rateLimit.login"),

    REGISTER("/api/auth/register", 5, Duration.ofHours(1),
            "error.rateLimit.register"),

    RESEND("/api/auth/resend-verification", 3, Duration.ofHours(1),
            "error.rateLimit.resend"),

    REFRESH("/api/auth/refresh", 30, Duration.ofMinutes(1),
            "error.rateLimit.refresh"),

    /** Doğrulama postasıyla aynı gerekçe: bedeli başkasının gelen kutusu. */
    FORGOT_PASSWORD("/api/auth/forgot-password", 3, Duration.ofHours(1),
            "error.rateLimit.forgotPassword"),

    /**
     * Jeton 256 bit, tahmin edilemez; sınır kaba kuvvete karşı değil (o zaten
     * imkânsız), ucun gürültü kaynağı olmasına karşı.
     */
    RESET_PASSWORD("/api/auth/reset-password", 10, Duration.ofHours(1),
            "error.rateLimit.resetPassword"),

    /**
     * Hesabı olmayan adrese davet bir POSTA gönderir; sınırsız olsa uygulama,
     * yönetici hesabı açan herkesin elinde bir toplu posta aracına dönüşürdü.
     */
    INVITE("/api/boards/*/invitations", 20, Duration.ofHours(1),
            "error.rateLimit.invite"),

    /**
     * Google girişinin son adımı. Kod 256 bit ve 60 saniyelik; tahmin edilemez.
     * Sınır, girişle aynı mantıkla ucun kötüye kullanılmasına karşı: meşru bir
     * kişi bunu girişte bir kez çağırır.
     */
    OAUTH_EXCHANGE("/api/auth/oauth/exchange", 20, Duration.ofMinutes(1),
            "error.rateLimit.login"),

    /** Çalınmış bir oturumla mevcut şifreyi deneme yanılmayla bulmaya karşı. */
    CHANGE_PASSWORD("/api/users/me/password", 10, Duration.ofHours(1),
            "error.rateLimit.changePassword"),

    /**
     * Kanal bildirimlerinde "Deneme mesajı gönder" (kaydedilmiş ya da kaydedilmemiş
     * adres; "**" ikisini de kapsar). Her deneme gerçek bir kanala yazar; sınırsız
     * olsa sunucumuz, adresini bildiğin bir kanala mesaj yağdırmanın aracı olurdu.
     * Kurulum sırasında birkaç deneme yeter.
     */
    CHAT_HOOK_TEST("/api/boards/*/chat-hooks/**/test", 10, Duration.ofMinutes(10),
            "error.rateLimit.chatHookTest");

    private final String path;
    private final int capacity;
    private final Duration period;
    private final String messageKey;

    RateLimitPolicy(String path, int capacity, Duration period, String messageKey) {
        this.path = path;
        this.capacity = capacity;
        this.period = period;
        this.messageKey = messageKey;
    }

    public String path() {
        return path;
    }

    /** Sınıra takılana gösterilen mesajın anahtarı; metin isteğin dilinde (ADR 0020). */
    public String messageKey() {
        return messageKey;
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
