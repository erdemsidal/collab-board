package com.collabboard.chathook;

/**
 * Bir kanal bağlantısının neden durdurulduğu. Durdurulan bağlantıya mesaj gitmez;
 * yönetici "Yeniden aç" diyene kadar öyle kalır. Arayüz her nedeni kendi dilinde anlatır.
 */
public enum DisabledReason {

    /** Kanal adresi tanımıyor (404 ya da 410): webhook silinmiş ya da kanal arşivlenmiş. */
    GONE,

    /** Kanal mesajı reddetti (403): izin geri alınmış. */
    FORBIDDEN,

    /** Üst üste 5 gönderim başarısız (429, 5xx, zaman aşımı...). */
    FAILING,

    /** Kayıtlı adres artık izin listesine uymuyor (liste daraltılmış). */
    BLOCKED,

    /** Kayıtlı adres çözülemedi: şifreleme anahtarı değişmiş. Adres yeniden yapıştırılmalı. */
    UNREADABLE
}
