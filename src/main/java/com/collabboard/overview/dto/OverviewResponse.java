package com.collabboard.overview.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Ana ekran özeti: kişinin erişebildiği TÜM panolardan derlenen tek bakış.
 *
 * Sayılar listelerden bağımsızdır: liste kısaltılsa bile (ör. 20 kart) sayaç
 * gerçek toplamı söyler.
 *
 * @param boardCount         erişilebilen pano sayısı ("Panolarım" ile aynı küme)
 * @param assignedOpenCount  kişiye atanmış, son kolonda olmayan kartlar
 * @param overdueCount       bunlardan son tarihi geçmiş olanlar
 * @param activityLast7Days  bu panolarda son 7 günde yapılan hareket sayısı
 * @param assignedCards      atanmış açık kartlar, son tarihi en yakın önce (kısaltılmış)
 * @param recentActivity     bu panoların son hareketleri, en yeni önce
 */
public record OverviewResponse(
        int boardCount,
        int assignedOpenCount,
        int overdueCount,
        long activityLast7Days,
        List<AssignedCard> assignedCards,
        List<RecentActivity> recentActivity
) {

    /** Kişiye atanmış açık bir kart ve nerede durduğu. */
    public record AssignedCard(
            Long cardId,
            String title,
            Long boardId,
            String boardName,
            Long columnId,
            String columnName,
            LocalDate dueDate
    ) {
    }

    /**
     * Bir pano hareketi — pano geçmişindeki satırın, hangi panoda olduğunu da
     * söyleyen hâli (özet birden çok panoyu karıştırır).
     */
    public record RecentActivity(
            Long id,
            Long boardId,
            String boardName,
            String actorName,
            String actorAvatarUrl,
            String type,
            String description,
            LocalDateTime createdAt
    ) {
    }
}
