package com.collabboard.common.exception;

/**
 * 429 — kişi başına sınır aşıldı (ör. çok hızlı yorum yazmak).
 *
 * Kimliksiz uçların IP sınırı bir filtrede, istek controller'a varmadan
 * kesilir (RateLimitFilter). Kimliği bilinen kişiye uygulanan sınır ise iş
 * mantığının içinde; bu istisna onu aynı 429 biçimine çevirir.
 */
public class TooManyRequestsException extends LocalizedException {

    private final long retryAfterSeconds;

    /** @param code mesaj anahtarı (messages_*.properties) */
    public TooManyRequestsException(String code, long retryAfterSeconds) {
        super(code);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
