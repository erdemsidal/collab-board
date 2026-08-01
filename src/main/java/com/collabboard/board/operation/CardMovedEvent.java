package com.collabboard.board.operation;

/**
 * "Bir kart taşındı" olayı → /topic/board.{id}'e yayınlanır.
 * version: taşıma sonrası güncel sürüm — istemci bunu saklayıp sonraki
 * operasyonunda baseVersion olarak gönderir.
 */
public record CardMovedEvent(
        String type,
        Long cardId,
        Long toColumnId,
        int position,
        Long version
) implements BoardEvent {

    public static CardMovedEvent of(Long cardId, Long toColumnId, int position, Long version) {
        return new CardMovedEvent("MOVE_CARD", cardId, toColumnId, position, version);
    }
}
