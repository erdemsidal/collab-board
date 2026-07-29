package com.collabboard.presence;

import com.collabboard.user.entity.User;

/**
 * Panoda ÇEVRİMİÇİ olan bir kişi (ADR 0005: artık gerçek kullanıcı).
 *
 * userId: aynı kişinin iki sekmesini tek kişi olarak saymak için (tekilleştirme).
 * color:  kullanıcı kimliğinden türetilir → aynı kişi her girişte AYNI renk.
 */
public record PresenceUser(
        String sessionId,
        Long userId,
        String name,
        String initials,
        String color
) {
    /** Renk paleti — kullanıcı kimliğine göre sabit renk atanır. */
    private static final String[] COLORS = {
            "#e05b49", "#f5871f", "#35b37e", "#0079bf", "#8777d9", "#00b8d9", "#ff8f73", "#6554c0"
    };

    public static PresenceUser from(User user, String sessionId) {
        String name = (user.getFirstName() + " " + user.getLastName()).trim();
        String initials = ("" + first(user.getFirstName()) + first(user.getLastName())).toUpperCase();
        String color = COLORS[(int) Math.abs(user.getId() % COLORS.length)];
        return new PresenceUser(sessionId, user.getId(), name, initials, color);
    }

    private static char first(String s) {
        return (s == null || s.isBlank()) ? '?' : s.charAt(0);
    }
}
