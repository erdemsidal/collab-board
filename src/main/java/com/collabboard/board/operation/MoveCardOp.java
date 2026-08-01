package com.collabboard.board.operation;

/**
 * "Şu kartı, şu kolona, şu pozisyona taşı."
 *
 * baseVersion: istemcinin ekranında gördüğü sürüm. Sunucudaki değer farklıysa
 * araya başkası girmiş demektir ve operasyon reddedilir (ADR 0003).
 */
public record MoveCardOp(
        Long cardId,
        Long toColumnId,
        int position,
        Long baseVersion
) implements BoardOperation {
}
