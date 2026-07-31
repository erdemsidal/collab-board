package com.collabboard.board.dto;

import com.collabboard.board.entity.Board;
import com.collabboard.board.entity.BoardRole;

import java.time.LocalDateTime;

/**
 * "Panolarım" listesindeki bir satır — panonun tamamı değil, özeti.
 *
 * myRole: kullanıcının o panodaki rolü. Arayüz bunu bilirse VIEWER'a düzenleme
 * düğmelerini hiç göstermez (yetkiyi sunucu zaten uyguluyor; bu sadece deneyim).
 */
public record BoardSummaryResponse(
        Long id,
        String name,
        BoardRole myRole,
        LocalDateTime createdAt
) {
    public static BoardSummaryResponse of(Board board, BoardRole role) {
        return new BoardSummaryResponse(board.getId(), board.getName(), role, board.getCreatedAt());
    }
}
