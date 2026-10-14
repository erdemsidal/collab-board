package com.collabboard.realtime;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Redis kanalında dolaşan ZARF (ADR 0004).
 *
 * destination: olayın gitmesi gereken STOMP adresi
 *              (ör. "/topic/board.42" veya "/queue/notifications")
 * payload:     olayın JSON hâli
 * user:        doluysa olay YALNIZCA bu kullanıcının oturumlarına gider
 *              (destination o zaman "/queue/..." biçimindedir). Boşsa destination'ı
 *              dinleyen herkese yayınlanır.
 *
 * payload ham JSON olarak taşınır: karşı sunucu onu Java tipine çevirmeden
 * doğrudan istemcilere iletir, böylece tek köprü tüm olay tiplerine hizmet eder.
 */
public record RelayMessage(
        String destination,
        JsonNode payload,
        String user
) {
    public static RelayMessage toAll(String destination, JsonNode payload) {
        return new RelayMessage(destination, payload, null);
    }

    public static RelayMessage toUser(String user, String destination, JsonNode payload) {
        return new RelayMessage(destination, payload, user);
    }
}
