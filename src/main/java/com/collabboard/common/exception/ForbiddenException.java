package com.collabboard.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 403 — kimlik doğrulandı ama işlem için yetki yok.
 *
 * Spring Security'nin AccessDeniedException'ıyla karışmasın diye ayrı isim.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
