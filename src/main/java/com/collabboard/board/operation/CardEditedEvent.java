package com.collabboard.board.operation;

import java.time.LocalDate;

/**
 * "Bir kartın alanları değişti" olayı → /topic/board.{id}'e yayınlanır.
 *
 * Alanların tamamını taşır: hem istemcilerin kartı tazeleyebilmesi için, hem de
 * geçmiş kaydından geçmiş yeniden kurulurken (ADR 0006) kartın o andaki hâlinin
 * eksiksiz bilinmesi için.
 */
public record CardEditedEvent(
        String type,
        Long cardId,
        String title,
        String description,
        Long assigneeId,
        LocalDate dueDate,
        Long version
) implements BoardEvent {

    public static CardEditedEvent of(Long cardId, String title, String description,
                                     Long assigneeId, LocalDate dueDate, Long version) {
        return new CardEditedEvent("EDIT_CARD", cardId, title, description,
                assigneeId, dueDate, version);
    }
}
