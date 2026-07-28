package com.collabboard.board.operation;

/**
 * "Bir kolon taşındı" olayı → /topic/board.{id}'e yayınlanır.
 * Aboneler kolonu yeni pozisyona (soldan sağa) yeniden dizer.
 */
public record ColumnMovedEvent(
        String type,
        Long columnId,
        int position
) implements BoardEvent {

    public static ColumnMovedEvent of(Long columnId, int position) {
        return new ColumnMovedEvent("MOVE_COLUMN", columnId, position);
    }
}
