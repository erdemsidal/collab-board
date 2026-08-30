package com.collabboard.board.operation;

/**
 * "Bir kolon silindi" olayı → /topic/board.{id}'e yayınlanır.
 *
 * Adını ve konumunu da taşır. İstemcinin bunlara ihtiyacı yok; geçmiş yeniden
 * kurulurken gerekiyorlar: silinmiş bir kolon artık panoda bulunmadığı için,
 * geçmişteki hâlini ancak bu kayıttan geri getirebiliriz (ADR 0006).
 */
public record ColumnDeletedEvent(
        String type,
        Long columnId,
        String name,
        int position
) implements BoardEvent {

    public static ColumnDeletedEvent of(Long columnId, String name, int position) {
        return new ColumnDeletedEvent("DELETE_COLUMN", columnId, name, position);
    }
}
