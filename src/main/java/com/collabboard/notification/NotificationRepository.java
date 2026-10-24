package com.collabboard.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(Long userId);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    Optional<Notification> findByUserIdAndInvitationIdAndType(Long userId, Long invitationId, NotificationType type);

    boolean existsByUserIdAndInvitationIdAndType(Long userId, Long invitationId, NotificationType type);

    /** Bu yorumda bu kişi daha önce anıldı mı? (düzenlemede ikinci bildirim ve posta gitmesin) */
    boolean existsByUserIdAndCommentIdAndType(Long userId, Long commentId, NotificationType type);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.userId = :userId AND n.readAt IS NULL")
    int markAllRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
