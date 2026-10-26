package com.collabboard.chathook;

/**
 * Bir kanal bağlantısının dinleyebileceği olaylar; her bağlantı kendi seçimini yapar.
 * Adlar veritabanında (board_chat_hooks.events) ve arayüzde aynen geçer.
 */
public enum ChatEvent {

    /** Panoya yeni kart eklendi. */
    CARD_CREATED,

    /**
     * Kart panonun SON kolonuna girdi ("bitti"). Yalnızca girişte: o kolonun
     * içinde sıralama değiştirmek yeni bir mesaj değildir. Tek kolonlu panoda
     * son kolon "bitti" anlamına gelmez (ana ekran ve akış ölçümüyle aynı kural).
     */
    CARD_DONE,

    /** Karta yorum yazıldı. Yorumun metni gitmez. */
    COMMENT_ADDED,

    /** Kart birine atandı. */
    CARD_ASSIGNED
}
