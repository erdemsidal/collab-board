package com.collabboard.auth.dto;

/**
 * Token refresh response — yeni access + refresh token.
 */
public record TokenRefreshResponse(
        String accessToken,
        String refreshToken,
        String tokenType
) {
    public TokenRefreshResponse(String accessToken, String refreshToken) {
        this(accessToken, refreshToken, "Bearer");
    }
}
