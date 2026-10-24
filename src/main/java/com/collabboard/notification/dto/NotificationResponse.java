package com.collabboard.notification.dto;

import com.collabboard.board.entity.BoardRole;
import com.collabboard.invitation.InvitationStatus;
import com.collabboard.notification.Notification;
import com.collabboard.notification.NotificationType;

import java.time.LocalDateTime;

/**
 * Bir bildirimin istemciye giden hâli.
 *
 * invitationStatus yalnızca davet bildirimlerinde dolu ve bildirimin değil
 * DAVETİN güncel durumudur: kişi daveti başka bir sekmede kabul ettiyse ya da
 * gönderen geri çektiyse, zil menüsü artık "Kabul et" düğmesi göstermemeli.
 *
 * cardId / cardTitle yalnızca yorum bildirimlerinde dolu: tıklanınca kart açılır.
 */
public record NotificationResponse(
        Long id,
        NotificationType type,
        Long actorId,
        String actorName,
        String actorAvatarUrl,
        Long boardId,
        String boardName,
        BoardRole role,
        Long invitationId,
        InvitationStatus invitationStatus,
        Long cardId,
        String cardTitle,
        boolean read,
        LocalDateTime createdAt
) {
    public static NotificationResponse of(Notification n, String actorAvatarUrl, InvitationStatus invitationStatus) {
        return new NotificationResponse(n.getId(), n.getType(), n.getActorId(), n.getActorName(), actorAvatarUrl,
                n.getBoardId(), n.getBoardName(), n.getRole(), n.getInvitationId(), invitationStatus,
                n.getCardId(), n.getCardTitle(), n.getReadAt() != null, n.getCreatedAt());
    }
}
