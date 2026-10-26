package com.collabboard.notification;

import com.collabboard.board.entity.BoardRole;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Bir kullanıcıya özel bildirim.
 *
 * Auditable'ı miras almaz: bildirim bir olayın kaydıdır ve okundu işareti
 * dışında değişmez (BoardActivity ile aynı yaklaşım).
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Bildirimin sahibi (alıcı). */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    /** Olayı yapan kişi; hesabı silinirse null olur, adı kalır. */
    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_name", length = 200)
    private String actorName;

    @Column(name = "board_id")
    private Long boardId;

    @Column(name = "board_name", length = 200)
    private String boardName;

    @Column(name = "invitation_id")
    private Long invitationId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private BoardRole role;

    /** Yorum bildirimlerinde açılacak kart; kart silinirse null olur, başlığı kalır. */
    @Column(name = "card_id")
    private Long cardId;

    @Column(name = "card_title", length = 500)
    private String cardTitle;

    /** Bahsetme bildiriminin yorumu: aynı yorumda aynı kişiye ikinci bildirim gitmesin (V17). */
    @Column(name = "comment_id")
    private Long commentId;

    /** Türe özel kısa bilgi: kanal bildirimi durunca kanalın adı (V22). */
    @Column(length = 200)
    private String detail;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
