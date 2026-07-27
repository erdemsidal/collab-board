package com.collabboard.auth.entity;

import com.collabboard.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Refresh Token entity — DB'de backup olarak tutulur.
 * Primary storage Redis'te (hız + auto-expiry).
 * DB'deki kayıt audit trail ve Redis down senaryoları için.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expiry_date", nullable = false)
    private Instant expiryDate;

    /**
     * Token süresi dolmuş mu kontrol et.
     */
    public boolean isExpired() {
        return Instant.now().isAfter(this.expiryDate);
    }
}
