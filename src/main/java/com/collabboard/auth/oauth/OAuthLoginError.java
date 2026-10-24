package com.collabboard.auth.oauth;

/**
 * Dış hesapla giriş neden olmadı? Arayüze /?oauth_error=&lt;key&gt; ile bildirilir.
 *
 * Bilerek kaba: arayüz kişiye ne yapacağını söyleyebilsin diye üç durum yeter.
 * Ayrıntı (Google'ın hata kodu, istisna) yalnızca loga yazılır; adres çubuğuna
 * iç bilgi taşınmaz.
 */
public enum OAuthLoginError {

    /** Kişi Google'ın izin ekranında vazgeçti. */
    CANCELLED("cancelled"),

    /** Google adresin kişiye ait olduğunu doğrulamamış; bu adresle hesap açılamaz ya da bağlanamaz. */
    EMAIL_UNVERIFIED("email_unverified"),

    /** Geri kalan her şey: süresi dolmuş akış, Google'a ulaşılamaması, beklenmeyen hata. */
    FAILED("failed");

    private final String key;

    OAuthLoginError(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
