package com.collabboard.invitation.dto;

import com.collabboard.board.entity.BoardRole;

/** Kabul edilen davetin götürdüğü yer: arayüz "Panoya git" bağlantısını buradan kurar. */
public record AcceptInvitationResponse(
        Long boardId,
        String boardName,
        BoardRole role
) {
}
