package com.collabboard.board.operation;

/**
 * "Bir kart silindi" olayı → /topic/board.{id}'e yayınlanır.
 * Abonelerin tek ihtiyacı: hangi kartı DOM'dan kaldıracakları → cardId.
 */
public record CardDeletedEvent(
        String type,
        Long cardId
) implements BoardEvent {

    public static CardDeletedEvent of(Long cardId) {
        return new CardDeletedEvent("DELETE_CARD", cardId);
    }
}
