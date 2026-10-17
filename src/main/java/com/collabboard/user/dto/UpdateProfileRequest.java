package com.collabboard.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Profilde değiştirilebilen bilgiler.
 *
 * E-posta burada YOK: hesabın kimliği, girişin anahtarı ve davetlerin
 * eşleştirildiği adres. Değiştirilebilmesi yeni adresin de doğrulanmasını
 * gerektirir; o ayrı bir akış.
 */
public record UpdateProfileRequest(
        @NotBlank(message = "Ad boş olamaz")
        @Size(max = 100, message = "Ad en fazla 100 karakter olabilir")
        String firstName,

        @NotBlank(message = "Soyad boş olamaz")
        @Size(max = 100, message = "Soyad en fazla 100 karakter olabilir")
        String lastName
) {
}
