package com.collabboard.user.dto;

import com.collabboard.security.password.NotCommonPassword;
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

        @NotBlank(message = "{validation.newPassword.required}")
        @Size(min = 8, max = 100, message = "{validation.password.size}")
        @NotCommonPassword
        String newPassword
) {
}
