package com.collabboard.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 403 — kimlik doğrulandı ama işlem için yetki yok.
 *
 * Spring Security'nin AccessDeniedException'ıyla karışmasın diye ayrı isim.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class ForbiddenException extends LocalizedException {

    /** @param code mesaj anahtarı (messages_*.properties) */
    public ForbiddenException(String code, Object... args) {
        super(code, args);
    }
}
