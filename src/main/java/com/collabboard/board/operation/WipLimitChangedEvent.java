package com.collabboard.board.operation;

/**
 * "Bir kolonun WIP limiti değişti" olayı → /topic/board.{id}'e yayınlanır.
 */
public record WipLimitChangedEvent(
        String type,
        Long columnId,
        Integer wipLimit
) implements BoardEvent {

    public static WipLimitChangedEvent of(Long columnId, Integer wipLimit) {
        return new WipLimitChangedEvent("SET_WIP_LIMIT", columnId, wipLimit);
    }
}
