package com.collabboard.board.operation;

import com.collabboard.board.dto.CardResponse;

/**
 * "Bir kart eklendi" olayı → /topic/board.{id}'e yayınlanır.
 * type alanı istemcinin (frontend) hangi olay olduğunu ayırt etmesi için.
 * card: yeni kartın tam hâli (sunucunun ürettiği id/version dahil).
 */
public record CardAddedEvent(
        String type,
        Long columnId,
        CardResponse card
) implements BoardEvent {

    public static CardAddedEvent of(Long columnId, CardResponse card) {
        return new CardAddedEvent("ADD_CARD", columnId, card);
    }
}
