package com.collabboard.realtime;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Redis kanalında dolaşan ZARF (ADR 0004).
 *
 * destination: olayın gitmesi gereken STOMP adresi
 *              (ör. "/topic/board.42" veya "/topic/board.42/presence")
 * payload:     olayın JSON hâli
 *
 * payload ham JSON olarak taşınır: karşı sunucu onu Java tipine çevirmeden
 * doğrudan istemcilere iletir, böylece tek köprü tüm olay tiplerine hizmet eder.
 */
public record RelayMessage(
        String destination,
        JsonNode payload
) {
}
