package com.collabboard.board.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/**
 * Zaman çizelgesindeki bir an — arayüzdeki kaydırıcının her durağı.
 *
 * id, o ana kadarki olayları uygulamak için kullanılır: "id'si buna eşit veya
 * küçük olan her şeyi uygula" denildiğinde pano o anki hâline döner.
 *
 * Arayüz anın açıklamasını type ve params'tan kendi dilinde kurar; params yoksa
 * Türkçe description gösterilir (ADR 0020).
 */
public record TimelineEntry(
        Long id,
        String actorName,
        String type,
        String description,
        JsonNode params,
        LocalDateTime createdAt
) {
}
