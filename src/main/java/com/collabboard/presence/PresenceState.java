package com.collabboard.presence;

import java.util.List;

/**
 * Panonun anlık çevrimiçi listesi → /topic/board.{id}/presence adresine yayınlanır.
 *
 * Neden tüm listeyi yolluyoruz da "biri geldi / biri gitti" demiyoruz?
 * Liste küçük (bir panodaki kişiler) ve tam liste her zaman doğrudur — istemcinin
 * kaçırdığı bir olay yüzünden yanlış görünmesi imkânsız. Basit ve sağlam.
 */
public record PresenceState(
        String type,
        List<PresenceUser> users
) {
    public static PresenceState of(List<PresenceUser> users) {
        return new PresenceState("PRESENCE", users);
    }
}
