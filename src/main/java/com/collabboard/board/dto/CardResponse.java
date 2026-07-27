package com.collabboard.board.dto;

import com.collabboard.board.entity.Card;

/**
 * Bir kartın client'a gönderilen hâli.
 *
 * version'ı NEDEN döndürüyoruz? Çünkü Faz 2'de istemci bir kartı düzenlerken
 * "elimdeki sürüm buydu" (baseVersion) diyecek. İstemcinin güncel version'ı
 * bilmesi için onu şimdiden fotoğrafa koyuyoruz.
 */
public record CardResponse(
        Long id,
        String title,
        int position,
        Long version
) {
    public static CardResponse fromEntity(Card card) {
        return new CardResponse(
                card.getId(),
                card.getTitle(),
                card.getPosition(),
                card.getVersion()
        );
    }
}
