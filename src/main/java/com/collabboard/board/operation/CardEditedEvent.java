package com.collabboard.board.operation;

/**
 * "Bir kartın başlığı değişti" olayı → /topic/board.{id}'e yayınlanır.
 */
public record CardEditedEvent(
        String type,
        Long cardId,
        String title,
        Long version
) implements BoardEvent {

    public static CardEditedEvent of(Long cardId, String title, Long version) {
        return new CardEditedEvent("EDIT_CARD", cardId, title, version);
    }
}
