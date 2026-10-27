package com.collabboard.chathook;

/**
 * Mesajın gittiği uygulama. Üçü de "gelen webhook" ile çalışır: bir adrese
 * JSON POST edilir, kanalda mesaj belirir. OAuth, uygulama kurulumu ya da
 * bize gelen bir istek yok; bu yüzden yerelde (localhost) de çalışır.
 */
public enum ChatProvider {

    SLACK("Slack"),

    /**
     * Microsoft Teams'in "Workflows" webhook'u. Eski Office 365 bağlayıcı
     * webhook'ları Mayıs 2026'da kapatıldı; yeni adresler Power Automate'ten gelir.
     */
    TEAMS("Microsoft Teams"),

    DISCORD("Discord");

    private final String displayName;

    ChatProvider(String displayName) {
        this.displayName = displayName;
    }

    /** Hata mesajlarında ve yönetici bildiriminde görünen ad. */
    public String displayName() {
        return displayName;
    }
}
