package com.collabboard.auth.entity;

import com.collabboard.common.audit.Auditable;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Şifre sıfırlama bağlantısındaki tek kullanımlık jeton.
 *
 * Jetonun kendisi değil, SHA-256 özeti saklanır (bkz. V12). Gelen jeton aynı
 * şekilde özetlenip aranır; veritabanını okuyan biri bağlantıyı yeniden
 * üretemez.
 */
@Entity
@Table(name = "password_reset_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }
}
