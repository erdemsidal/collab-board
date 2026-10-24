package com.collabboard.common.exception;

import java.util.Arrays;

/**
 * Kullanıcıya gösterilen hataların ortak atası: metin değil, mesaj anahtarı taşır.
 *
 * Hatanın fırladığı yer kullanıcının dilini bilmez (servis katmanı, canlı kanal);
 * metne çeviren yer bilir: REST'te GlobalExceptionHandler isteğin diliyle, canlı
 * kanalda BoardOperationController kişinin kayıtlı diliyle (ADR 0020).
 * getMessage() anahtarı ve parametreleri döner; loglarda aranabilir kalır.
 */
public abstract class LocalizedException extends RuntimeException {

    private final String code;
    private final transient Object[] args;

    protected LocalizedException(String code, Object... args) {
        super(code);
        this.code = code;
        this.args = args == null ? new Object[0] : args.clone();
    }

    /** messages_*.properties içindeki anahtar. */
    public String getCode() {
        return code;
    }

    /** Mesajdaki {0}, {1}... yerine geçenler. */
    public Object[] getArgs() {
        return args.clone();
    }

    @Override
    public String getMessage() {
        return args.length == 0 ? code : code + " " + Arrays.toString(args);
    }
}
