package com.collabboard.board.dto;

import com.collabboard.board.entity.BoardRole;

/**
 * Pano üyesi — arayüzdeki üye listesi için.
 *
 * Ad ve soyad AYRI gönderilir: arayüz avatar baş harflerini ("ES") bunlardan
 * üretir ve tam adı ayrıca gösterir. Birleşik tek bir metin göndermek,
 * istemciyi ismi ayrıştırmaya zorlardı.
 */
public record MemberResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        BoardRole role
) {
}
