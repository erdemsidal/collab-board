package com.collabboard.common.exception;

/**
 * 503 — özellik bu sunucuda kurulu değil (ör. kanal bildirimleri için şifreleme
 * anahtarları tanımlanmamış). İstek doğru, kişinin yetkisi de var; eksik olan
 * sunucunun ayarı. Mesaj bunu açıkça söyler, "bir hata oluştu" demez.
 */
public class FeatureUnavailableException extends LocalizedException {

    /** @param code mesaj anahtarı (messages_*.properties) */
    public FeatureUnavailableException(String code, Object... args) {
        super(code, args);
    }
}
