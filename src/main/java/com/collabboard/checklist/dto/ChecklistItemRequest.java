package com.collabboard.checklist.dto;

/**
 * Madde ekleme ve değiştirme isteği. Değiştirmede yalnızca gönderilen alanlar değişir;
 * eklemede yalnızca text okunur (yeni madde yapılmamış olarak başlar).
 *
 * text:     1..300 karakter (eklemede zorunlu).
 * done:     yapıldı mı. Değiştirme değil ATAMA: "işaretle" iki kez gelirse madde
 *           yine işaretli kalır. Aynı maddeye aynı anda iki kişi tıklasa da sonuç bozulmaz.
 * position: maddenin listedeki yeni yeri (0'dan). Diğer maddeler kayar; listenin
 *           dışını gösteren değer başa ya da sona yuvarlanır.
 */
public record ChecklistItemRequest(
        String text,
        Boolean done,
        Integer position
) {
}
