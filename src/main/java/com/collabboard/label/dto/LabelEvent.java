package com.collabboard.label.dto;

import com.collabboard.board.operation.BoardEvent;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Pano kanalına (/topic/board.{id}) giden etiket olayı.
 *
 * Kartın sürümünü taşımaz: etiket kartın sürüm kontrolüne girmez (ADR 0022).
 *
 * Kartın etiketleri için "şu an hangileri takılı" değil, "şu takıldı / şu
 * çıkarıldı" gönderilir. İki kişi aynı anda farklı etiketler taktığında iki
 * olay da birbirini ezmeden uygulanır; tam liste gönderilseydi yayınların
 * sırası karışınca biri kaybolabilirdi.
 *
 *   LABEL_CREATED / LABEL_UPDATED  label: etiketin yeni hâli
 *   LABEL_DELETED                  labelId: bütün kartlardan da kalktı
 *   CARD_LABEL_ADDED / _REMOVED    cardId, labelId
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LabelEvent(
        String type,
        LabelResponse label,
        Long labelId,
        Long cardId
) implements BoardEvent {

    public static LabelEvent created(LabelResponse label) {
        return new LabelEvent("LABEL_CREATED", label, label.id(), null);
    }

    public static LabelEvent updated(LabelResponse label) {
        return new LabelEvent("LABEL_UPDATED", label, label.id(), null);
    }

    public static LabelEvent deleted(Long labelId) {
        return new LabelEvent("LABEL_DELETED", null, labelId, null);
    }

    public static LabelEvent addedToCard(Long cardId, Long labelId) {
        return new LabelEvent("CARD_LABEL_ADDED", null, labelId, cardId);
    }

    public static LabelEvent removedFromCard(Long cardId, Long labelId) {
        return new LabelEvent("CARD_LABEL_REMOVED", null, labelId, cardId);
    }
}
