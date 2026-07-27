package com.collabboard.user.dto;

import com.collabboard.user.entity.Role;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Kullanıcı bilgisi response DTO'su.
 * Entity'yi asla doğrudan client'a expose etme — her zaman DTO kullan.
 */
public record UserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        Set<String> roles,
        LocalDateTime createdAt
) {
    /**
     * Entity → DTO dönüşümü.
     * MapStruct da kullanılabilir ama basit dönüşümler için factory method yeterli.
     */
    public static UserResponse fromEntity(com.collabboard.user.entity.User user) {
        Set<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                roleNames,
                user.getCreatedAt()
        );
    }
}
