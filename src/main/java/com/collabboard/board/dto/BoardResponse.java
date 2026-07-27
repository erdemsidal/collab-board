package com.collabboard.board.dto;

import com.collabboard.board.entity.Board;

import java.time.LocalDateTime;
import java.util.List;

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
        List<ColumnResponse> columns = board.getColumns().stream()
                .map(ColumnResponse::fromEntity)
                .toList();

        return new BoardResponse(
                board.getId(),
                board.getName(),
                columns,
                board.getCreatedAt()
        );
    }
}
