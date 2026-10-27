package com.collabboard.invitation.dto;

import com.collabboard.board.entity.BoardRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** "Bu adresi panoya şu rolle davet et." */
public record InviteRequest(
        @NotBlank(message = "{validation.invite.email.required}")
        @Email(message = "{validation.invite.email.invalid}")
        String email,

        @NotNull(message = "{validation.role.required}")
        BoardRole role
) {
}
