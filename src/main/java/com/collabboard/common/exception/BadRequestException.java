package com.collabboard.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 400 — Geçersiz istek.
 * Örnek: Eksik veya hatalı parametre.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class BadRequestException extends LocalizedException {

    /** @param code mesaj anahtarı (messages_*.properties) */
    public BadRequestException(String code, Object... args) {
        super(code, args);
    }
}
