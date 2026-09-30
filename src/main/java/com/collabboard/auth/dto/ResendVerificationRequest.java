package com.collabboard.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** "Doğrulama postasını şu adrese yeniden gönder." */
public record ResendVerificationRequest(

        @NotBlank(message = "E-posta zorunludur")
        @Email(message = "Geçerli bir e-posta adresi girin")
        String email
) {
}
