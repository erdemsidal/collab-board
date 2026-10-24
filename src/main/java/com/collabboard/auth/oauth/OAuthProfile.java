package com.collabboard.auth.oauth;

/**
 * Dış hesaptan gelen kişi bilgisi, sağlayıcıdan bağımsız biçimde.
 *
 * Her sağlayıcı bilgiyi farklı adlarla verir (Google "given_name", GitHub
 * yalnızca "name"); bu farkı sağlayıcının kendi eşleyicisi kapatır
 * (OAuthProvider.profile). Hesap tarafı (OAuthAccountService) yalnızca bu
 * kaydı tanır.
 *
 * @param provider      sağlayıcının kısa adı: "google"
 * @param subject       sağlayıcıdaki kalıcı kimlik (Google'da "sub"); e-posta değişse de aynı kalır
 * @param email         sağlayıcının bildirdiği adres
 * @param emailVerified sağlayıcı bu adresin kişiye ait olduğunu doğruladı mı
 * @param givenName     ad; yoksa null
 * @param familyName    soyad; yoksa null
 * @param fullName      tam ad; ad ve soyad ayrı gelmezse kullanılır, yoksa null
 */
public record OAuthProfile(
        String provider,
        String subject,
        String email,
        boolean emailVerified,
        String givenName,
        String familyName,
        String fullName
) {
}
