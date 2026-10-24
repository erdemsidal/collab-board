package com.collabboard.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** "Şu adrese şifre sıfırlama bağlantısı gönder." */
public record ForgotPasswordRequest(

        @NotBlank(message = "{validation.email.required}")
        @Email(message = "{validation.email.invalid}")
        String email
) {
}
