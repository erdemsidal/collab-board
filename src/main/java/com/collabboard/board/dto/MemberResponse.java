package com.collabboard.board.dto;

import com.collabboard.board.entity.BoardRole;

/**
 * Pano üyesi — arayüzde üye listesi için.
 */
public record MemberResponse(
        Long userId,
        String name,
        String email,
        BoardRole role
) {
}
