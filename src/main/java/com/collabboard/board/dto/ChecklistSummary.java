package com.collabboard.board.dto;

/**
 * Kartın üstündeki "3/5" için bir kontrol listesinin özeti (maddeleri değil).
 *
 * Kart başına tek sayı yerine liste başına özet: canlı olay bir listenin tamamını
 * revision ile taşır, arayüz o listenin özetini günceller ve kartın toplamını
 * kendisi toplar. Kart başına tek sayı tutulsaydı, iki farklı listede aynı anda
 * işaretlenen iki madde birbirinin sayısını ezebilirdi.
 */
public record ChecklistSummary(
        Long id,
        long done,
        long total,
        long revision
) {
}
