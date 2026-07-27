package com.collabboard.board.dto;

import com.collabboard.board.entity.BoardColumn;

import java.util.List;

/**
 * Bir kolonun client'a gönderilen hâli — içindeki kartlarla birlikte (iç içe).
 */
public record ColumnResponse(
        Long id,
        String name,
        int position,
        List<CardResponse> cards
) {
    public static ColumnResponse fromEntity(BoardColumn column) {
        // Kolonun kartlarını tek tek CardResponse'a çevir.
        // stream().map(...).toList() = "her kartı DTO'ya dönüştür, listeye topla".
        List<CardResponse> cards = column.getCards().stream()
                .map(CardResponse::fromEntity)
                .toList();

        return new ColumnResponse(
                column.getId(),
                column.getName(),
                column.getPosition(),
                cards
        );
    }
}
