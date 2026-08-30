package com.collabboard.board.dto;

import com.collabboard.board.entity.Card;

import java.time.LocalDate;

/**
 * Bir kartın client'a gönderilen hâli.
 *
 * version, çakışma kontrolü için gerekli: istemci bir düzenleme gönderirken
 * gördüğü sürümü de bildirir (ADR 0003).
 *
 * assigneeId ham id olarak gider; istemci ismi üye listesinden eşler. Kart başına
 * kullanıcı sorgusu açmamak için (bkz. Card.assigneeId).
 */
public record CardResponse(
        Long id,
        String title,
        int position,
        String description,
        Long assigneeId,
        LocalDate dueDate,
        Long version
) {
    public static CardResponse fromEntity(Card card) {
        return new CardResponse(
                card.getId(),
                card.getTitle(),
                card.getPosition(),
                card.getDescription(),
                card.getAssigneeId(),
                card.getDueDate(),
                card.getVersion()
        );
    }
}
