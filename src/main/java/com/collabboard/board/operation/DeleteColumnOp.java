package com.collabboard.board.operation;

/**
 * "Şu kolonu sil." İçindeki kartlar da silinir (veritabanında ON DELETE CASCADE).
 *
 * Geri alınamaz bir işlem; arayüz onay ister. Silinen kolon geçmiş kaydında
 * durduğu için zaman yolculuğunda hâlâ görünür (ADR 0006).
 */
public record DeleteColumnOp(
        Long columnId
) implements BoardOperation {
}
