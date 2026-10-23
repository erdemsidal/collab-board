package com.collabboard.presence;

/**
 * Panodaki herkese yayınlanan imleç.
 *
 * Kimlik bilgileri (ad, renk) istemciden ALINMAZ; panoya katılırken sunucunun
 * kaydettiği kişiden gelir — kimse başkasının adıyla imleç gezdiremez.
 */
public record CursorEvent(
        String type,
        Long userId,
        String name,
        String initials,
        String color,
        double x,
        double y,
        Long cardId,
        boolean gone
) {
    static CursorEvent of(PresenceUser user, CursorMove move) {
        return new CursorEvent("CURSOR", user.userId(), user.name(), user.initials(), user.color(),
                move.clampedX(), move.clampedY(), move.gone() ? null : move.cardId(), move.gone());
    }
}
