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
        Integer wipLimit,
        List<CardResponse> cards
) {
    public static ColumnResponse fromEntity(BoardColumn column) {
        return fromEntity(column, CardExtras.NONE);
    }

    /** @param extras kartlara eklenen yorum sayısı, etiketler ve liste özetleri (bkz. CardExtras) */
    public static ColumnResponse fromEntity(BoardColumn column, CardExtras extras) {
        // Kolonun kartlarını tek tek CardResponse'a çevir.
        // stream().map(...).toList() = "her kartı DTO'ya dönüştür, listeye topla".
        List<CardResponse> cards = column.getCards().stream()
                .map(card -> CardResponse.fromEntity(card, extras))
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
