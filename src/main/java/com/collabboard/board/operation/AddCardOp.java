package com.collabboard.board.operation;

/**
 * "Şu kolona şu başlıkta yeni bir kart ekle."
 * Kartın id'sini ve pozisyonunu SUNUCU belirler (istemci göndermez).
 *
 * type alanı JSON'da var ama burada ayrı bir alan yok — onu Jackson
 * @JsonTypeInfo üzerinden okuyup doğru sınıfı (bunu) seçmek için kullanır.
 */
public record AddCardOp(
        Long columnId,
        String title
) implements BoardOperation {
}
