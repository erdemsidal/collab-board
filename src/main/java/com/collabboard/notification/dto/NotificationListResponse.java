package com.collabboard.notification.dto;

import java.util.List;

/** Zil menüsü: son bildirimler + okunmamış sayısı (rozet için). */
public record NotificationListResponse(
        long unreadCount,
        List<NotificationResponse> items
) {
}
