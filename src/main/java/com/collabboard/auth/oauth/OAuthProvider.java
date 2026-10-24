package com.collabboard.auth.oauth;

import org.springframework.security.oauth2.client.registration.ClientRegistration;

import java.util.Map;

/**
 * Bağlanabildiğimiz bir dış hesap sağlayıcısı.
 *
 * Yeni bir sağlayıcı (GitHub, Microsoft) eklemek iki adım:
 *  1. Bu arayüzü uygulayan küçük bir sınıf: uç adresleri ve bilgilerin eşlenmesi.
 *  2. Ortam değişkenleri: app.oauth.&lt;id&gt;.client-id ve client-secret.
 * Geri kalan her şey (akış, hesap bulma ve bağlama, oturum açma) ortaktır.
 */
public interface OAuthProvider {

    /** Kısa ad: adreslerde (/oauth2/authorization/google) ve user_identities.provider'da geçer. */
    String id();

    /** Düğmede görünen ad: "Google". */
    String displayName();

    /**
     * Sağlayıcının uç adresleri ve kapsamları. Kimlik bilgileri ve dönüş adresi
     * burada verilmez; onları OAuthClients ekler.
     */
    ClientRegistration.Builder registration();

    /**
     * Sağlayıcının verdiği bilgileri ortak biçime çevirir.
     *
     * @param attributes OpenID Connect'te kimlik jetonunun (ID token) ve
     *                   userinfo ucunun birleşik bilgileri
     */
    OAuthProfile profile(Map<String, Object> attributes);
}
