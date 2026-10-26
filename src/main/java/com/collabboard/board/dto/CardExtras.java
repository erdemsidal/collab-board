package com.collabboard.board.dto;

import java.util.List;
import java.util.Map;

/**
 * Kartın kendisinde olmayan, panonun fotoğrafına eklenen bilgiler. Her biri
 * pano başına TEK sorguyla gelir (kart başına sorgu yok):
 *
 * commentCounts: kart id → yorum sayısı (ADR 0019)
 * labelIds:      kart id → takılı etiketler (ADR 0022)
 * checklists:    kart id → kontrol listelerinin özeti (ADR 0022)
 *
 * Haritada olmayan kartın yorumu, etiketi, listesi yoktur.
 */
public record CardExtras(
        Map<Long, Long> commentCounts,
        Map<Long, List<Long>> labelIds,
        Map<Long, List<ChecklistSummary>> checklists
) {
    /** Yeni kart olayı ve yeni pano: hiçbiri yok. */
    public static final CardExtras NONE = new CardExtras(Map.of(), Map.of(), Map.of());
}
