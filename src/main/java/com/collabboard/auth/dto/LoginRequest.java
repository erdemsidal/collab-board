package com.collabboard.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login isteği DTO'su.
 */
public record LoginRequest(
        @NotBlank(message = "{validation.account.email.required}")
        @Email(message = "{validation.account.email.invalid}")
        String email,

        @NotBlank(message = "{validation.password.required}")
        String password
) {}
