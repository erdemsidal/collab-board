package com.collabboard.checklist.dto;

import com.collabboard.board.operation.BoardEvent;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Pano kanalına (/topic/board.{id}) giden kontrol listesi olayı.
 *
 *   CHECKLIST_UPDATED  checklist: listenin değişiklikten sonraki tamamı (revision ile).
 *                      Yeni liste, ad, madde ekleme/işaretleme/sıralama/silme hep bu.
 *   CHECKLIST_DELETED  checklistId
 *
 * Neden her seferinde listenin tamamı? Arayüz ne değiştiğini yorumlamaz, elindekini
 * değiştirir; revision eskiyse atar. Kaçan ya da geç gelen bir olay sırayı bozamaz.
 * Bedeli: liste en fazla 100 madde, olay en kötü ihtimalle ~30 KB.
 *
 * logged: değişiklik "Son hareketler"e de yazıldı (liste eklendi, tamamlandı,
 * silindi); arayüz yalnızca o zaman listeyi yeniden ister. Diğerlerinde alan yok.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChecklistEvent(
        String type,
        Long cardId,
        ChecklistResponse checklist,
        Long checklistId,
        Boolean logged
) implements BoardEvent {

    public static ChecklistEvent updated(ChecklistResponse checklist, boolean logged) {
        return new ChecklistEvent("CHECKLIST_UPDATED", checklist.cardId(), checklist, checklist.id(),
                logged ? Boolean.TRUE : null);
    }

    public static ChecklistEvent deleted(Long cardId, Long checklistId) {
        return new ChecklistEvent("CHECKLIST_DELETED", cardId, null, checklistId, Boolean.TRUE);
    }
}
