package com.collabboard.invitation;

import com.collabboard.board.entity.BoardRole;
import com.collabboard.common.audit.Auditable;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Bir panoya davet.
 *
 * Kullanıcıya değil e-postaya bağlı: henüz hesabı olmayan biri de davet
 * edilebilir. O adresle kayıt olup e-postasını doğrulayan kişi daveti görür
 * ve yanıtlayabilir (bkz. V13).
 */
@Entity
@Table(name = "board_invitations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoardInvitation extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "board_id", nullable = false)
    private Long boardId;

    /** Davet edilen adres, küçük harfe çevrilmiş. */
    @Column(nullable = false, length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BoardRole role;

    @Column(name = "invited_by")
    private Long invitedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private InvitationStatus status = InvitationStatus.PENDING;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    public boolean isPending() {
        return status == InvitationStatus.PENDING;
    }
}
