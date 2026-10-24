package com.collabboard.common.exception;

/**
 * 409 — Çakışma: istemci BAYAT bir sürümle işlem yapmaya çalıştı (ADR 0003).
 *
 * Yani istemcinin ekranında gördüğü sürüm (baseVersion), sunucudaki güncel
 * sürümle uyuşmuyor — arada başkası o kaydı değiştirmiş demektir.
 * RuntimeException olduğu için @Transactional metotta işlemi geri aldırır:
 * reddedilen operasyondan DB'ye hiçbir şey yazılmaz.
 */
public class StaleVersionException extends LocalizedException {

    private final Long cardId;
    private final transient String detail;

    public StaleVersionException(Long cardId, Long baseVersion, Long currentVersion) {
        super("error.staleVersion");
        this.cardId = cardId;
        this.detail = String.format("kart %d: gönderilen=%s, güncel=%s", cardId, baseVersion, currentVersion);
    }

    public Long getCardId() {
        return cardId;
    }

    @Override
    public String getMessage() {
        return getCode() + " (" + detail + ")";
    }
}
