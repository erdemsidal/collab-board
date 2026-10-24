package com.collabboard.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** "Doğrulama postasını şu adrese yeniden gönder." */
public record ResendVerificationRequest(

        @NotBlank(message = "{validation.email.required}")
        @Email(message = "{validation.email.invalid}")
        String email
) {
}
