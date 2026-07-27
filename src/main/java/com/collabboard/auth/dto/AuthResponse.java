package com.collabboard.auth.dto;

import com.collabboard.user.dto.UserResponse;

/**
 * Auth response — login ve register sonrası döner.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        UserResponse user
) {
    public AuthResponse(String accessToken, String refreshToken, UserResponse user) {
        this(accessToken, refreshToken, "Bearer", user);
    }
}
