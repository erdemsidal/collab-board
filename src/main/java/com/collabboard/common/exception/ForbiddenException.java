package com.collabboard.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 403 — Kimliğin doğrulandı ama bu işlem için YETKİN yok.
 *
 * 401 ile farkı: 401 "kim olduğunu bilmiyorum", 403 "kim olduğunu biliyorum
 * ama bunu yapamazsın" demektir.
 *
 * Adı neden ForbiddenException? Spring Security'nin kendi AccessDeniedException'ı
 * var; aynı ismi kullanmak hangi sınıfın import edildiği konusunda kafa karıştırır.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
