package com.collabboard.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 404 — Kaynak bulunamadı.
 * Örnek: Kullanıcı ID ile arandı ama yok.
 *
 * Kullanıcıya yalnızca neyin bulunamadığı söylenir ("Pano bulunamadı.");
 * aranan değer (id, e-posta) mesaja girmez, yalnızca loglara.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends LocalizedException {

    private final transient Object lookup;

    /**
     * @param code   "error.notFound.board" gibi mesaj anahtarı
     * @param lookup aranan değer; yalnızca loglar için
     */
    public ResourceNotFoundException(String code, Object lookup) {
        super(code);
        this.lookup = lookup;
    }

    @Override
    public String getMessage() {
        return getCode() + " (" + lookup + ")";
    }
}
