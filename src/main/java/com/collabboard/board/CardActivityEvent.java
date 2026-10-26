package com.collabboard.board;

/**
 * Bir kartla ilgili, panonun dışına duyurulabilecek bir şey oldu.
 *
 * Kart ve yorum servisleri işlemin İÇİNDE yayınlar; dinleyen taraf (ör. kanal
 * bildirimleri, ChatNotifier) işlem kaydedildikten SONRA ve arka planda çalışır.
 * Bu yüzden olay o anın değerlerini taşır (başlık, kolon adı): dinleyici
 * çalıştığında kart değişmiş ya da silinmiş olabilir.
 *
 * Kişi e-postayla taşınır, adı dinleyicide çözülür: adı aramak bir sorgu ve
 * kimse dinlemiyorsa (panonun bağlı kanalı yoksa) boşuna yapılmasın.
 */
public record CardActivityEvent(
        Kind kind,
        Long boardId,
        String actorEmail,
        Long cardId,
        String cardTitle,
        String columnName,
        /** Yalnızca taşımada: kartın geldiği kolon. Aynı kolon içinde sıralama da bir taşımadır. */
        Long fromColumnId,
        /** Kartın şu anki kolonu. */
        Long columnId,
        /** Yalnızca atamada: kartı üstlenen kişi. */
        Long assigneeId
) {

    public enum Kind {
        /** Yeni kart eklendi. */
        CREATED,
        /** Kart taşındı. "Son kolona girdi mi?" sorusunu dinleyici sorar. */
        MOVED,
        /** Kart birine atandı (atanan değişti ve boş değil). */
        ASSIGNED,
        /** Karta yorum yazıldı. */
        COMMENTED
    }

    public static CardActivityEvent created(Long boardId, String actor, Long cardId, String title,
                                            Long columnId, String columnName) {
        return new CardActivityEvent(Kind.CREATED, boardId, actor, cardId, title, columnName, null, columnId, null);
    }

    public static CardActivityEvent moved(Long boardId, String actor, Long cardId, String title,
                                          Long fromColumnId, Long columnId, String columnName) {
        return new CardActivityEvent(Kind.MOVED, boardId, actor, cardId, title, columnName, fromColumnId, columnId, null);
    }

    public static CardActivityEvent assigned(Long boardId, String actor, Long cardId, String title,
                                             Long columnId, String columnName, Long assigneeId) {
        return new CardActivityEvent(Kind.ASSIGNED, boardId, actor, cardId, title, columnName, null, columnId, assigneeId);
    }

    public static CardActivityEvent commented(Long boardId, String actor, Long cardId, String title,
                                              Long columnId, String columnName) {
        return new CardActivityEvent(Kind.COMMENTED, boardId, actor, cardId, title, columnName, null, columnId, null);
    }
}
