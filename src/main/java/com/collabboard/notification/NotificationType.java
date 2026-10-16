package com.collabboard.notification;

/** Kullanıcıya özel bildirim türleri. */
public enum NotificationType {
    /** Bir panoya davet edildin — kabul / ret bekliyor. */
    BOARD_INVITE,
    /** Gönderdiğin daveti karşı taraf kabul etti. */
    INVITE_ACCEPTED,
    /** Gönderdiğin daveti karşı taraf reddetti. */
    INVITE_DECLINED,
    /** Sana gelen davet, gönderen tarafından geri çekildi. */
    INVITE_CANCELLED
}
