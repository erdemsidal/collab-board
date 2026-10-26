package com.collabboard.board.dto;

import com.collabboard.board.entity.Board;
import com.collabboard.label.dto.LabelResponse;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Panonun TAM hâli — "fotoğraf". GET /boards/{id} bunu döndürür; yeni bağlanan
 * istemci önce bunu alır (REST), sonra canlı operasyonları dinler (WebSocket).
 *
 * İç içe yapı:  Board → columns[] → her column'un cards[]'ı.
 * labels: panonun etiket takımı; kartlar yalnızca id'lerini taşır (ADR 0022).
 */
public record BoardResponse(
        Long id,
        String name,
        List<ColumnResponse> columns,
        LocalDateTime createdAt,
        List<LabelResponse> labels
) {
    public static BoardResponse fromEntity(Board board) {
        return fromEntity(board, List.of(), CardExtras.NONE);
    }

    /**
     * @param labels panonun etiketleri
     * @param extras kartlara eklenen yorum sayısı, etiketler ve liste özetleri; her biri
     *               tek sorgudan (bkz. BoardService.getBoard)
     */
    public static BoardResponse fromEntity(Board board, List<LabelResponse> labels, CardExtras extras) {
        List<ColumnResponse> columns = board.getColumns().stream()
                .map(column -> ColumnResponse.fromEntity(column, extras))
                .toList();

        return new BoardResponse(
                board.getId(),
                board.getName(),
                columns,
                board.getCreatedAt(),
                labels
        );
    }
}
