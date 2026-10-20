package com.collabboard.invitation.dto;

import com.collabboard.board.entity.BoardRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** "Bu adresi panoya şu rolle davet et." */
public record InviteRequest(
        @NotBlank(message = "E-posta boş olamaz")
        @Email(message = "Geçerli bir e-posta giriniz")
        String email,

        @NotNull(message = "Rol belirtilmeli (OWNER, EDITOR, VIEWER)")
        BoardRole role
) {
}
