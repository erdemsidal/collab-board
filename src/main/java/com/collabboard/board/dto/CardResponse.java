package com.collabboard.board.dto;

import com.collabboard.board.entity.Card;

import java.time.LocalDate;
import java.util.List;

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
 *
 * labelIds ve checklists de öyle (ADR 0022): takılı etiketlerin id'leri (adı ve
 * rengi panonun labels listesinde) ve kontrol listelerinin özeti. Yeni kartta
 * ikisi de boş; geçmiş görünümünde etiketler o anki hâliyle, listeler boş.
 */
public record CardResponse(
        Long id,
        String title,
        int position,
        String description,
        Long assigneeId,
        LocalDate dueDate,
        Long version,
        long commentCount,
        List<Long> labelIds,
        List<ChecklistSummary> checklists
) {
    public static CardResponse fromEntity(Card card) {
        return fromEntity(card, CardExtras.NONE);
    }

    public static CardResponse fromEntity(Card card, CardExtras extras) {
        return new CardResponse(
                card.getId(),
                card.getTitle(),
                card.getPosition(),
                card.getDescription(),
                card.getAssigneeId(),
                card.getDueDate(),
                card.getVersion(),
                extras.commentCounts().getOrDefault(card.getId(), 0L),
                extras.labelIds().getOrDefault(card.getId(), List.of()),
                extras.checklists().getOrDefault(card.getId(), List.of())
        );
    }
}
