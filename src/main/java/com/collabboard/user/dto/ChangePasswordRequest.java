package com.collabboard.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Şifre değiştirme. Kural kayıttakiyle aynı (RegisterRequest). */
public record ChangePasswordRequest(
        @NotBlank(message = "Mevcut şifre boş olamaz")
        String currentPassword,

        @NotBlank(message = "Yeni şifre boş olamaz")
        @Size(min = 8, max = 100, message = "Şifre en az 8 karakter olmalı")
        String newPassword
) {
}
