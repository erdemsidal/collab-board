package com.collabboard.board.operation;

import com.collabboard.board.dto.ColumnResponse;

/** "Panoya yeni kolon eklendi" olayı → /topic/board.{id}'e yayınlanır. */
public record ColumnAddedEvent(
        String type,
        ColumnResponse column
) implements BoardEvent {

    public static ColumnAddedEvent of(ColumnResponse column) {
        return new ColumnAddedEvent("ADD_COLUMN", column);
    }
}
