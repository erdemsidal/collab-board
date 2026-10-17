package com.collabboard.notification.dto;

import com.collabboard.invitation.InvitationStatus;

/**
 * Kullanıcının kişisel kanalına (/user/queue/notifications) giden olay.
 *
 * NOTIFICATION:        yeni bir bildirim — zil rozeti artar, davetse sağ altta
 *                      kabul/ret kutusu açılır.
 * INVITATION_RESOLVED: kişi bir daveti yanıtladı. Aynı kişinin öteki sekmeleri
 *                      de "Kabul et" düğmesini kaldırsın diye.
 */
public record InboxEvent(
        String type,
        NotificationResponse notification,
        Long invitationId,
        InvitationStatus status
) {
    public static InboxEvent notification(NotificationResponse n) {
        return new InboxEvent("NOTIFICATION", n, null, null);
    }

    public static InboxEvent invitationResolved(Long invitationId, InvitationStatus status) {
        return new InboxEvent("INVITATION_RESOLVED", null, invitationId, status);
    }
}
