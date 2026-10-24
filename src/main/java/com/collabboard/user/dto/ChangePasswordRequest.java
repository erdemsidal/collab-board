package com.collabboard.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Şifre değiştirme. Kural kayıttakiyle aynı (RegisterRequest).
 *
 * currentPassword şifresi olan hesapta zorunlu; Google ile açılmış, henüz
 * şifresi olmayan hesapta boş kalabilir. Bunu alan doğrulaması bilemez (hesaba
 * bakmak gerekir), kontrol ProfileService.changePassword'de.
 */
public record ChangePasswordRequest(
        String currentPassword,

        @NotBlank(message = "Yeni şifre boş olamaz")
        @Size(min = 8, max = 100, message = "Şifre en az 8 karakter olmalı")
        String newPassword
) {
}
