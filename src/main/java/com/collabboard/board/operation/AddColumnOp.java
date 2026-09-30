package com.collabboard.board.operation;

/**
 * "Bu panoya şu isimde yeni bir kolon ekle." Kolon sona eklenir.
 *
 * Diğer kolon operasyonlarından farklı olarak columnId taşımaz — kolon henüz yok.
 * Hangi panoya ekleneceği bu yüzden açıkça belirtilir.
 */
public record AddColumnOp(
        Long boardId,
        String name
) implements BoardOperation {
}
