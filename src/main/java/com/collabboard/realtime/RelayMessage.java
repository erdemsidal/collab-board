package com.collabboard.realtime;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Redis kanalında dolaşan ZARF (ADR 0004).
 *
 * destination: olayın gitmesi gereken STOMP adresi
 *              (ör. "/topic/board.42" veya "/topic/board.42/presence")
 * payload:     olayın JSON hâli
 *
 * Neden payload'u JsonNode (ham JSON ağacı) olarak taşıyoruz?
 * Çünkü olay tipleri farklı (CardAddedEvent, PresenceState, ...). Karşı sunucunun
 * bunları tekrar Java sınıfına çevirmesine GEREK YOK — sadece istemcilere aynen
 * iletecek. Ham JSON taşımak hem tip bilgisi derdini ortadan kaldırır hem de
 * köprüyü tüm olay tipleri için ortak yapar.
 */
public record RelayMessage(
        String destination,
        JsonNode payload
) {
}
