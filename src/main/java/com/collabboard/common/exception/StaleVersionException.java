package com.collabboard.common.exception;

/**
 * 409 — Çakışma: istemci BAYAT bir sürümle işlem yapmaya çalıştı (ADR 0003).
 *
 * Yani istemcinin ekranında gördüğü sürüm (baseVersion), sunucudaki güncel
 * sürümle uyuşmuyor — arada başkası o kaydı değiştirmiş demektir.
 * RuntimeException olduğu için @Transactional metotta işlemi geri aldırır:
 * reddedilen operasyondan DB'ye hiçbir şey yazılmaz.
 */
public class StaleVersionException extends RuntimeException {

    private final Long cardId;

    public StaleVersionException(Long cardId, Long baseVersion, Long currentVersion) {
        super(String.format("Kart %d bayat sürümle güncellenmeye çalışıldı: gönderilen=%s, güncel=%s",
                cardId, baseVersion, currentVersion));
        this.cardId = cardId;
    }

    public Long getCardId() {
        return cardId;
    }
}
