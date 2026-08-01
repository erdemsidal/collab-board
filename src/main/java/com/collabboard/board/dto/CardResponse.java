package com.collabboard.board.dto;

import com.collabboard.board.entity.Card;

/**
 * Bir kartın client'a gönderilen hâli.
 *
 * version, çakışma kontrolü için gerekli: istemci bir düzenleme gönderirken
 * gördüğü sürümü de bildirir (ADR 0003).
 */
public record CardResponse(
        Long id,
        String title,
        int position,
        Long version
) {
    public static CardResponse fromEntity(Card card) {
        return new CardResponse(
                card.getId(),
                card.getTitle(),
                card.getPosition(),
                card.getVersion()
        );
    }
}
