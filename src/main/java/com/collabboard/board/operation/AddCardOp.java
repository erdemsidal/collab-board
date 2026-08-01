package com.collabboard.board.operation;

/**
 * "Şu kolona şu başlıkta yeni bir kart ekle."
 * Kartın id'sini ve pozisyonunu sunucu belirler.
 */
public record AddCardOp(
        Long columnId,
        String title
) implements BoardOperation {
}
