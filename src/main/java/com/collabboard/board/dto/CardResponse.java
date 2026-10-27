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
 *
 * commentCount kartın parçası değil, panonun fotoğrafına eklenen bir sayıdır:
 * yorum kartın sürümünü artırmaz (ADR 0019). Fotoğraf dışında (yeni kart olayı,
 * geçmiş görünümü) 0 gider; sayıyı canlı yorum olayları günceller.
 */
public record CardResponse(
        Long id,
        String title,
        int position,
        String description,
        Long assigneeId,
        LocalDate dueDate,
        Long version,
        long commentCount
) {
    public static CardResponse fromEntity(Card card) {
        return fromEntity(card, 0);
    }

    public static CardResponse fromEntity(Card card, long commentCount) {
        return new CardResponse(
                card.getId(),
                card.getTitle(),
                card.getPosition(),
                card.getDescription(),
                card.getAssigneeId(),
                card.getDueDate(),
                card.getVersion(),
                commentCount
        );
    }
}
