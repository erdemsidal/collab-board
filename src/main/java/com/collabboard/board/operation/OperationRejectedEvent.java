package com.collabboard.board.operation;

/**
 * "Operasyonun reddedildi" bildirimi (ADR 0003).
 *
 * DİKKAT: Bu event /topic'e YAYINLANMAZ — sadece operasyonu gönderen kişiye
 * gider (/user/queue/errors). Diğer kullanıcıların bu gürültüyü görmesi gereksiz.
 *
 * İstemci bunu alınca "snapshot resync" yapar: panonun tam hâlini REST'ten
 * yeniden çekip ekranını tazeler.
 */
public record OperationRejectedEvent(
        String type,
        String reason,     // STALE_VERSION | NOT_FOUND
        Long cardId,
        String message
) implements BoardEvent {

    /** Bayat sürüm: arada başkası değiştirmiş. */
    public static OperationRejectedEvent staleVersion(Long cardId) {
        return new OperationRejectedEvent("OP_REJECTED", "STALE_VERSION", cardId,
                "Bu kartı senden önce biri değiştirdi. Ekran güncellendi, tekrar dene.");
    }

    /** Kayıt yok: örn. kart bu arada silinmiş. */
    public static OperationRejectedEvent notFound(String message) {
        return new OperationRejectedEvent("OP_REJECTED", "NOT_FOUND", null, message);
    }

    /** Yetki yok: panonun üyesi değil ya da rolü değişiklik yapmaya yetmiyor. */
    public static OperationRejectedEvent forbidden(String message) {
        return new OperationRejectedEvent("OP_REJECTED", "FORBIDDEN", null, message);
    }
}
