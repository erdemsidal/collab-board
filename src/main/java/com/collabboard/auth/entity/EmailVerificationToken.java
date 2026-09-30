package com.collabboard.auth.entity;

import com.collabboard.common.audit.Auditable;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * E-posta doğrulama bağlantısındaki tek kullanımlık jeton.
 *
 * Refresh token'lar Redis'te tutuluyor ama bu jeton Postgres'te: doğrulama
 * bağlantısı e-postada günlerce bekleyebilir ve Redis yeniden başlatılırsa
 * uçardı. Kalıcı olması gereken bir şey kalıcı bir yerde durmalı.
 */
@Entity
@Table(name = "email_verification_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailVerificationToken extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Bağlantıdaki rastgele dize. Tahmin edilemez olması güvenliğin tamamı. */
    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * Kullanıldığı an; NULL ise henüz kullanılmadı.
     *
     * Satırı silmek yerine işaretliyoruz. "Bu bağlantı zaten kullanılmış" ile
     * "böyle bir bağlantı yok" kullanıcı için farklı durumlar; ilkinde kişi
     * doğru yoldadır, ikincisinde bir yanlışlık vardır.
     */
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }
}
