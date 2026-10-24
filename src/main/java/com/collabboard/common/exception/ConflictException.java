package com.collabboard.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 409 — Conflict.
 * Örnek: Bu email ile zaten kayıtlı kullanıcı var.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ConflictException extends LocalizedException {

    /** @param code mesaj anahtarı (messages_*.properties) */
    public ConflictException(String code, Object... args) {
        super(code, args);
    }
}
