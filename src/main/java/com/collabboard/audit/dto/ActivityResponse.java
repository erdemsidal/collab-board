package com.collabboard.audit.dto;

import com.collabboard.audit.BoardActivity;

import java.time.LocalDateTime;

/**
 * Bir geçmiş kaydının client'a gönderilen hâli.
 */
public record ActivityResponse(
        Long id,
        String actorName,
        String type,
        String description,
        LocalDateTime createdAt
) {
    public static ActivityResponse fromEntity(BoardActivity activity) {
        return new ActivityResponse(
                activity.getId(),
                activity.getActorName(),
                activity.getType(),
                activity.getDescription(),
                activity.getCreatedAt());
    }
}
