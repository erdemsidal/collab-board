package com.collabboard.board.dto;

import java.util.List;

/**
 * Panonun akış sağlığı.
 *
 * Kanban aslında bir akış yönetimi yöntemidir: asıl soru "kaç iş var" değil,
 * "işler nerede tıkanıyor". Bu ölçümler olay kaydından hesaplanır.
 *
 * @param columns              kolon bazında bekleme süreleri
 * @param averageCycleTimeSeconds  kartın eklenmesinden son kolona ulaşmasına kadar
 *                                 geçen ortalama süre (ölçülebilen kart yoksa null)
 * @param bottleneckColumnId   ortalama beklemenin en yüksek olduğu kolon
 * @param measuredTransitions  ölçüme giren tamamlanmış geçiş sayısı — az veriyle
 *                             çıkan ortalamaya güvenilmemesi için açıkça bildirilir
 */
public record FlowResponse(
        List<ColumnFlow> columns,
        Long averageCycleTimeSeconds,
        Long bottleneckColumnId,
        int measuredTransitions
) {
    /**
     * @param cardCount          kolonda ŞU AN duran kart sayısı
     * @param avgDwellSeconds    kolondan geçmiş kartların ortalama bekleme süresi
     * @param oldestCardSeconds  kolonda hâlâ bekleyen en eski kartın yaşı
     */
    public record ColumnFlow(
            Long columnId,
            String name,
            int cardCount,
            Long avgDwellSeconds,
            Long oldestCardSeconds
    ) {
    }
}
