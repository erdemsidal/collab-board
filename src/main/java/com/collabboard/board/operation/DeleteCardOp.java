package com.collabboard.board.operation;

/**
 * "Şu kartı sil."
 */
public record DeleteCardOp(
        Long cardId
) implements CardOperation {
}
