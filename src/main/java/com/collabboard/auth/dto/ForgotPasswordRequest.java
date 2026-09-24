package com.collabboard.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** "Şu adrese şifre sıfırlama bağlantısı gönder." */
public record ForgotPasswordRequest(

        @NotBlank(message = "E-posta zorunludur")
        @Email(message = "Geçerli bir e-posta adresi girin")
        String email
) {
}
