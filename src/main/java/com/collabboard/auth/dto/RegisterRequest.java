package com.collabboard.auth.dto;

import com.collabboard.security.password.NotCommonPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Kayıt isteği DTO'su.
 */
public record RegisterRequest(
        @NotBlank(message = "{validation.firstName.required}")
        @Size(min = 2, max = 100, message = "{validation.firstName.size}")
        String firstName,

        @NotBlank(message = "{validation.lastName.required}")
        @Size(min = 2, max = 100, message = "{validation.lastName.size}")
        String lastName,

        @NotBlank(message = "{validation.account.email.required}")
        @Email(message = "{validation.account.email.invalid}")
        String email,

        @NotBlank(message = "{validation.password.required}")
        @Size(min = 8, max = 100, message = "{validation.password.size}")
        @NotCommonPassword
        String password
) {}
