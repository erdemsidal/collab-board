package com.collabboard.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Kayıt isteği DTO'su.
 */
public record RegisterRequest(
        @NotBlank(message = "Ad boş olamaz")
        @Size(min = 2, max = 100, message = "Ad 2-100 karakter arasında olmalı")
        String firstName,

        @NotBlank(message = "Soyad boş olamaz")
        @Size(min = 2, max = 100, message = "Soyad 2-100 karakter arasında olmalı")
        String lastName,

        @NotBlank(message = "Email boş olamaz")
        @Email(message = "Geçerli bir email adresi giriniz")
        String email,

        @NotBlank(message = "Şifre boş olamaz")
        @Size(min = 8, max = 100, message = "Şifre en az 8 karakter olmalı")
        String password
) {}
