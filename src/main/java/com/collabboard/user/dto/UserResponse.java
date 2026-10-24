package com.collabboard.user.dto;

import com.collabboard.user.entity.Role;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Kullanıcı bilgisi response DTO'su.
 * Entity'yi asla doğrudan client'a expose etme — her zaman DTO kullan.
 *
 * hasPassword ve providers profil ekranı için: Google ile açılmış bir hesapta
 * "mevcut şifre" alanı gösterilmez, bağlı hesaplar listelenir. Bağlı hesaplar
 * ayrı tabloda durduğu için bu kayıt UserResponseMapper ile kurulur.
 */
public record UserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String avatarUrl,
        Set<String> roles,
        LocalDateTime createdAt,
        boolean hasPassword,
        List<String> providers
) {
    /**
     * Entity → DTO dönüşümü.
     *
     * @param providers kişiye bağlı dış hesapların sağlayıcıları ("google")
     */
    public static UserResponse fromEntity(com.collabboard.user.entity.User user, List<String> providers) {
        Set<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                com.collabboard.user.AvatarUrls.of(user.getAvatarKey()),
                roleNames,
                user.getCreatedAt(),
                user.hasPassword(),
                List.copyOf(providers)
        );
    }
}
