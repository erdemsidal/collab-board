package com.collabboard.auth.dto;

import com.collabboard.security.password.NotCommonPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * "Bu bağlantıyla şifremi şuna çevir."
 *
 * Şifre kuralı kayıttakiyle aynı (RegisterRequest); sıfırlama, kayıtta
 * reddedilecek bir şifreyi kabul etmenin arka kapısı olmamalı.
 */
public record ResetPasswordRequest(

        @NotBlank(message = "{validation.resetToken.required}")
        String token,

        @NotBlank(message = "{validation.password.required}")
        @Size(min = 8, max = 100, message = "{validation.password.size}")
        @NotCommonPassword
        String newPassword
) {
}
