package com.collabboard.audit.dto;

import com.collabboard.audit.BoardActivity;

import java.time.LocalDateTime;

/**
 * Bir geçmiş kaydının client'a gönderilen hâli.
 */
public record ActivityResponse(
        Long id,
        String actorName,
        String actorAvatarUrl,
        String type,
        String description,
        LocalDateTime createdAt
) {
    public static ActivityResponse fromEntity(BoardActivity activity, String actorAvatarUrl) {
        return new ActivityResponse(
                activity.getId(),
                activity.getActorName(),
                actorAvatarUrl,
                activity.getType(),
                activity.getDescription(),
                activity.getCreatedAt());
    }
}
