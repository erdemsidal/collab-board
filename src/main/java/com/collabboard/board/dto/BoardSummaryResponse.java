package com.collabboard.board.dto;

import com.collabboard.board.entity.Board;
import com.collabboard.board.entity.BoardRole;

/**
 * "Panolarım" listesindeki bir satır — panonun tamamı değil, özeti.
 *
 * role: kullanıcının o panodaki ETKİN rolü (pano istisnası ya da şirket rolünden
 * türetilmiş). Arayüz buna göre düzenleme öğelerini gösterir/gizler.
 * cardCount / memberCount: pano kartında gösterilen "N kart · M üye" bilgisi.
 */
public record BoardSummaryResponse(
        Long id,
        String name,
        Long workspaceId,
        BoardRole role,
        long cardCount,
        long memberCount
) {
    public static BoardSummaryResponse of(Board board, BoardRole role, long cardCount, long memberCount) {
        return new BoardSummaryResponse(board.getId(), board.getName(), board.getWorkspaceId(),
                role, cardCount, memberCount);
    }
}
