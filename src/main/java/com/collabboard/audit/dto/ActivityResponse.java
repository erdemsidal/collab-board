package com.collabboard.audit.dto;

import com.collabboard.audit.BoardActivity;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/**
 * Bir geçmiş kaydının client'a gönderilen hâli.
 *
 * Arayüz satırı type ve params'tan kendi dilinde kurar ({"card": "Süt al",
 * "column": "Done"}); params yoksa (V19'dan önceki satır) Türkçe description
 * gösterilir (ADR 0020).
 */
public record ActivityResponse(
        Long id,
        String actorName,
        String actorAvatarUrl,
        String type,
        String description,
        JsonNode params,
        LocalDateTime createdAt
) {
    public static ActivityResponse fromEntity(BoardActivity activity, String actorAvatarUrl, JsonNode params) {
        return new ActivityResponse(
                activity.getId(),
                activity.getActorName(),
                actorAvatarUrl,
                activity.getType(),
                activity.getDescription(),
                params,
                activity.getCreatedAt());
    }
}
