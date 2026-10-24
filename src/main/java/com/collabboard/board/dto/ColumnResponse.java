package com.collabboard.board.dto;

import com.collabboard.board.entity.BoardColumn;

import java.util.List;
import java.util.Map;

/**
 * Bir kolonun client'a gönderilen hâli — içindeki kartlarla birlikte (iç içe).
 */
public record ColumnResponse(
        Long id,
        String name,
        int position,
        Integer wipLimit,
        List<CardResponse> cards
) {
    public static ColumnResponse fromEntity(BoardColumn column) {
        return fromEntity(column, Map.of());
    }

    /** @param commentCounts kart id → yorum sayısı; listede olmayan kartın yorumu yok. */
    public static ColumnResponse fromEntity(BoardColumn column, Map<Long, Long> commentCounts) {
        // Kolonun kartlarını tek tek CardResponse'a çevir.
        // stream().map(...).toList() = "her kartı DTO'ya dönüştür, listeye topla".
        List<CardResponse> cards = column.getCards().stream()
                .map(card -> CardResponse.fromEntity(card, commentCounts.getOrDefault(card.getId(), 0L)))
                .toList();

        return new ColumnResponse(
                column.getId(),
                column.getName(),
                column.getPosition(),
                column.getWipLimit(),
                cards
        );
    }
}
