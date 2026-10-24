package com.collabboard.board.dto;

import com.collabboard.board.entity.Board;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Panonun TAM hâli — "fotoğraf". GET /boards/{id} bunu döndürür; yeni bağlanan
 * istemci önce bunu alır (REST), sonra canlı operasyonları dinler (WebSocket).
 *
 * İç içe yapı:  Board → columns[] → her column'un cards[]'ı.
 */
public record BoardResponse(
        Long id,
        String name,
        List<ColumnResponse> columns,
        LocalDateTime createdAt
) {
    public static BoardResponse fromEntity(Board board) {
        return fromEntity(board, Map.of());
    }

    /** @param commentCounts kart id → yorum sayısı (tek gruplu sorgudan, bkz. CommentRepository.countPerCard) */
    public static BoardResponse fromEntity(Board board, Map<Long, Long> commentCounts) {
        List<ColumnResponse> columns = board.getColumns().stream()
                .map(column -> ColumnResponse.fromEntity(column, commentCounts))
                .toList();

        return new BoardResponse(
                board.getId(),
                board.getName(),
                columns,
                board.getCreatedAt()
        );
    }
}
