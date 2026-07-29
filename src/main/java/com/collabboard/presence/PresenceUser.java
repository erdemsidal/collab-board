package com.collabboard.presence;

/**
 * Panoda ÇEVRİMİÇİ olan bir kişi.
 *
 * Faz 2'de auth yok → kimlikleri sunucu geçici olarak atar ("Mavi Panda" gibi).
 * TODO(faz4): gerçek auth gelince ad/avatar gerçek kullanıcıdan alınacak.
 *
 * id = WebSocket oturum kimliği (session id). Her açık sekme ayrı bir oturumdur;
 * yani aynı kişi iki sekme açarsa iki "kişi" görünür — Faz 2 için kabul.
 */
public record PresenceUser(
        String id,
        String name,
        String color
) {
}
