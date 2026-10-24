package com.collabboard.auth.oauth;

import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Google, OpenID Connect ile.
 *
 * Uç adresleri Spring Security'nin hazır tanımından gelir (CommonOAuth2Provider):
 * yetkilendirme, jeton, imza anahtarları (JWKS) ve userinfo. Kimlik jetonunun
 * imzası, yayıncısı (issuer), hedef kitlesi (audience) ve nonce'u Spring
 * tarafından doğrulanır; buraya gelen bilgiler doğrulanmış bilgilerdir.
 */
@Component
public class GoogleOAuthProvider implements OAuthProvider {

    public static final String ID = "google";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Google";
    }

    @Override
    public ClientRegistration.Builder registration() {
        return CommonOAuth2Provider.GOOGLE.getBuilder(ID)
                .scope("openid", "email", "profile");
    }

    /**
     * Google'ın standart OpenID Connect bilgileri: sub, email, email_verified,
     * given_name, family_name, name. Ad bilgileri kişinin Google profilinde
     * yoksa gelmeyebilir; boşlukları hesap tarafı doldurur.
     */
    @Override
    public OAuthProfile profile(Map<String, Object> attributes) {
        return new OAuthProfile(
                ID,
                text(attributes.get("sub")),
                text(attributes.get("email")),
                isTrue(attributes.get("email_verified")),
                text(attributes.get("given_name")),
                text(attributes.get("family_name")),
                text(attributes.get("name")));
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    /** Google bunu mantıksal değer olarak verir; dize olarak gelirse de doğru okunsun. */
    private static boolean isTrue(Object value) {
        return value instanceof Boolean b ? b : "true".equalsIgnoreCase(String.valueOf(value));
    }
}
