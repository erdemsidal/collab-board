package com.collabboard.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Profil fotoğrafının baytları (bkz. V15).
 *
 * User tablosundan ayrı: kullanıcı satırı her girişte, her yetki kontrolünde
 * okunuyor; birkaç on kilobaytlık görseli her seferinde belleğe taşımanın
 * anlamı yok. Görsel yalnızca adresine istek geldiğinde okunur.
 */
@Entity
@Table(name = "user_avatars")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAvatar {

    @Id
    @Column(name = "avatar_key", length = 36)
    private String avatarKey;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "content_type", nullable = false, length = 30)
    private String contentType;

    @Column(nullable = false)
    private byte[] data;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
