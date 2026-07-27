package com.collabboard.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 403 — Refresh token geçersiz veya süresi dolmuş.
 * Token rotation sırasında kullanılır.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class TokenRefreshException extends RuntimeException {

    public TokenRefreshException(String token, String message) {
        super(String.format("Refresh token [%s] hatası: %s", token, message));
    }
}
