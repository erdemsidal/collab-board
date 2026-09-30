package com.collabboard.board.operation;

/** "Şu kolonun adını değiştir." */
public record RenameColumnOp(
        Long columnId,
        String name
) implements BoardOperation {
}
