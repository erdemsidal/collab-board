package com.collabboard.invitation.dto;

import com.collabboard.board.entity.BoardRole;
import com.collabboard.invitation.BoardInvitation;
import com.collabboard.invitation.InvitationStatus;

import java.time.LocalDateTime;

/**
 * Bekleyen davet — yalnızca panonun yöneticisine gösterilir.
 *
 * @param hasAccount davet edilen adresle bir hesap var mı. Yoksa arayüz
 *                   "kayıt olunca görecek" notunu gösterir.
 * @param inviteeName hesap varsa kişinin adı; yoksa null
 */
public record InvitationResponse(
        Long id,
        Long boardId,
        String email,
        BoardRole role,
        InvitationStatus status,
        boolean hasAccount,
        String inviteeName,
        String inviteeAvatarUrl,
        String invitedByName,
        LocalDateTime createdAt
) {
    public static InvitationResponse of(BoardInvitation inv, boolean hasAccount, String inviteeName,
                                        String inviteeAvatarUrl, String invitedByName) {
        return new InvitationResponse(inv.getId(), inv.getBoardId(), inv.getEmail(), inv.getRole(),
                inv.getStatus(), hasAccount, inviteeName, inviteeAvatarUrl, invitedByName, inv.getCreatedAt());
    }
}
