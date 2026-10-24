package com.collabboard.auth.oauth;

/** Dış hesapla giriş reddedildi; sebebi arayüze bildirilecek anahtardır. */
public class OAuthLoginException extends RuntimeException {

    private final OAuthLoginError error;

    public OAuthLoginException(OAuthLoginError error, String message) {
        super(message);
        this.error = error;
    }

    public OAuthLoginError error() {
        return error;
    }
}
