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
        String color,
        String avatarUrl
) {
    /** Renk paleti — kullanıcı kimliğine göre sabit renk atanır. */
    private static final String[] COLORS = {
            "#e05b49", "#f5871f", "#35b37e", "#0079bf", "#8777d9", "#00b8d9", "#ff8f73", "#6554c0"
    };

    public static PresenceUser from(User user, String sessionId) {
        return of(sessionId, user.getId(), user.getFirstName(), user.getLastName(), user.getAvatarKey());
    }

    /**
     * Kullanıcı kaydı yüklemeden aynı görünüm (ör. toplu sorgudan gelen alanlarla).
     * Ad, baş harf ve renk YALNIZCA burada üretilir: panodaki çevrimiçi listesi ile
     * ana ekrandaki ekip listesi aynı kişiyi aynı renkte göstermeli.
     */
    public static PresenceUser of(String sessionId, Long userId, String firstName, String lastName,
                                  String avatarKey) {
        String name = (firstName + " " + lastName).trim();
        String initials = ("" + first(firstName) + first(lastName)).toUpperCase();
        String color = COLORS[(int) Math.abs(userId % COLORS.length)];
        return new PresenceUser(sessionId, userId, name, initials, color,
                com.collabboard.user.AvatarUrls.of(avatarKey));
    }

    private static char first(String s) {
        return (s == null || s.isBlank()) ? '?' : s.charAt(0);
    }
}
