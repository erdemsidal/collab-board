package com.collabboard.user.dto;

import com.collabboard.user.entity.Role;
import com.fasterxml.jackson.annotation.JsonInclude;

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
 *
 * locale ("tr" | "en") arayüzün dili: kayıtlı tercih, tarayıcının dilinden önce gelir (ADR 0020).
 *
 * platformOwner yalnızca site sahibinde (APP_ADMIN_EMAILS) true ve yalnızca onda
 * yanıtta bulunur; arayüz hesap menüsüne "Yönetim"i buna göre koyar (ADR 0024).
 * Diğer herkesin yanıtında alan hiç yok: panelin varlığı oradan da okunmasın.
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
        List<String> providers,
        String locale,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean platformOwner
) {
    /**
     * Entity → DTO dönüşümü.
     *
     * @param providers     kişiye bağlı dış hesapların sağlayıcıları ("google")
     * @param platformOwner kişi site sahibi mi (yönetim paneli)
     */
    public static UserResponse fromEntity(com.collabboard.user.entity.User user, List<String> providers,
                                          boolean platformOwner) {
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
                List.copyOf(providers),
                user.getLocale(),
                platformOwner ? Boolean.TRUE : null
        );
    }
}
