package com.collabboard.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Bir hesaba bağlı dış kimlik: "bu Google hesabı, şu CollabBoard hesabıdır".
 *
 * Eşleştirme (provider, subject) ile yapılır. subject sağlayıcının kişiye
 * verdiği kalıcı kimliktir (Google'da "sub"); e-posta değişse de aynı kalır.
 */
@Entity
@Table(name = "user_identities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Sağlayıcının kısa adı: "google". */
    @Column(nullable = false, length = 30)
    private String provider;

    @Column(nullable = false, length = 255)
    private String subject;

    /** Bağlandığı andaki adres; yalnızca iz için tutulur. */
    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
