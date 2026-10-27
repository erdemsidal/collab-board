package com.collabboard.checklist.dto;

/**
 * Liste oluşturma ve yeniden adlandırma isteği.
 *
 * title: 1..100 karakter, baştaki ve sondaki boşluk atılır. Varsayılan ad
 *        ("Kontrol listesi" / "Checklist") arayüzden, kişinin dilinde gelir.
 */
public record ChecklistRequest(
        String title
) {
}
