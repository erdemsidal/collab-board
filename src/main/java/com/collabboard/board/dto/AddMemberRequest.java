package com.collabboard.board.dto;

import com.collabboard.board.entity.BoardRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Panoya üye ekleme/rol güncelleme isteği.
 * Kullanıcıyı e-posta ile buluyoruz — davet eden kişi karşı tarafın id'sini bilmez.
 */
public record AddMemberRequest(
        @NotBlank(message = "E-posta boş olamaz")
        @Email(message = "Geçerli bir e-posta giriniz")
        String email,

        @NotNull(message = "Rol belirtilmeli (OWNER, EDITOR, VIEWER)")
        BoardRole role
) {
}
