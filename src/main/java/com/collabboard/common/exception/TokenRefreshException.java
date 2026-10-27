package com.collabboard.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 403 — Refresh token geçersiz veya süresi dolmuş.
 * Token rotation sırasında kullanılır.
 *
 * Jeton mesaja YAZILMAZ: mesaj hem loglanıyor hem istemciye dönüyor, ve
 * jetonun tamamı geçerli bir oturum anahtarı olabilir.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class TokenRefreshException extends LocalizedException {

    public TokenRefreshException() {
        super("error.refreshToken");
    }
}
