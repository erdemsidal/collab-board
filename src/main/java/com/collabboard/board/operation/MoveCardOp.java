package com.collabboard.board.operation;

/**
 * "Şu kartı, şu kolona, şu pozisyona taşı."
 *
 * baseVersion: istemcinin elindeki kartın versiyonu (ADR 0001 optimistic lock).
 * Faz 1'de sadece taşıyacağız; Faz 2'de bu alanla çakışma kontrolü yapacağız
 * (sunucudaki güncel version farklıysa operasyonu reddet).
 */
public record MoveCardOp(
        Long cardId,
        Long toColumnId,
        int position,
        Long baseVersion
) implements CardOperation {
}
