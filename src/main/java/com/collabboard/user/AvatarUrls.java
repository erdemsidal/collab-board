package com.collabboard.user;

/**
 * Profil fotoğrafı adresi.
 *
 * Adres kullanıcı numarasından değil fotoğrafın rastgele anahtarından türer:
 * fotoğraf değişince adres de değişir, böylece tarayıcı önbelleği eskiyi
 * göstermez ve fotoğraflar numara artırılarak taranamaz (bkz. V15).
 */
public final class AvatarUrls {

    public static final String PREFIX = "/api/avatars/";

    private AvatarUrls() {
    }

    /** Fotoğraf yoksa null — arayüz baş harflere düşer. */
    public static String of(String avatarKey) {
        return avatarKey == null ? null : PREFIX + avatarKey;
    }
}
