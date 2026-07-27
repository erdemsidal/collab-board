package com.collabboard.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Token yenileme isteği DTO'su.
 */
public record TokenRefreshRequest(
        @NotBlank(message = "Refresh token boş olamaz")
        String refreshToken
) {}
