package com.collabboard.board.operation;

/** "Bir kolonun adı değişti" olayı → /topic/board.{id}'e yayınlanır. */
public record ColumnRenamedEvent(
        String type,
        Long columnId,
        String name
) implements BoardEvent {

    public static ColumnRenamedEvent of(Long columnId, String name) {
        return new ColumnRenamedEvent("RENAME_COLUMN", columnId, name);
    }
}
