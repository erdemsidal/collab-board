package com.collabboard.board.operation;

/**
 * "Şu kartın başlığını değiştir." (ADR 0001: serbest alan → Faz 2'de LWW)
 */
public record EditCardOp(
        Long cardId,
        String title
) implements BoardOperation {
}
