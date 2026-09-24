package com.collabboard.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * "Bu bağlantıyla şifremi şuna çevir."
 *
 * Şifre kuralı kayıttakiyle aynı (RegisterRequest); sıfırlama, kayıtta
 * reddedilecek bir şifreyi kabul etmenin arka kapısı olmamalı.
 */
public record ResetPasswordRequest(

        @NotBlank(message = "Bağlantı geçersiz")
        String token,

        @NotBlank(message = "Şifre boş olamaz")
        @Size(min = 8, max = 100, message = "Şifre en az 8 karakter olmalı")
        String newPassword
) {
}
