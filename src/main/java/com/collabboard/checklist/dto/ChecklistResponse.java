package com.collabboard.checklist.dto;

import com.collabboard.checklist.Checklist;
import com.collabboard.checklist.ChecklistItem;

import java.util.List;

/**
 * Bir kontrol listesinin tamamı: adı, maddeleri (sırasıyla) ve revision.
 *
 * Listeye dokunan her istek bunu döner, canlı olay da bunu taşır. Arayüz
 * elindeki listeyi bununla değiştirir; revision'ı elindekinden küçükse atar.
 */
public record ChecklistResponse(
        Long id,
        Long cardId,
        String title,
        long revision,
        List<Item> items
) {
    public record Item(Long id, String text, boolean done, int position) {
    }

    public static ChecklistResponse of(Checklist checklist, List<ChecklistItem> items) {
        return new ChecklistResponse(checklist.getId(), checklist.getCardId(), checklist.getTitle(),
                checklist.getRevision(),
                items.stream().map(i -> new Item(i.getId(), i.getText(), i.isDone(), i.getPosition())).toList());
    }
}
