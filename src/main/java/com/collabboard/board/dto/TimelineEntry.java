package com.collabboard.board.dto;

import java.time.LocalDateTime;

/**
 * Zaman çizelgesindeki bir an — arayüzdeki kaydırıcının her durağı.
 *
 * id, o ana kadarki olayları uygulamak için kullanılır: "id'si buna eşit veya
 * küçük olan her şeyi uygula" denildiğinde pano o anki hâline döner.
 */
public record TimelineEntry(
        Long id,
        String actorName,
        String type,
        String description,
        LocalDateTime createdAt
) {
}
