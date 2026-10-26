package com.collabboard.label.dto;

/**
 * Etiket oluşturma ve düzenleme isteği.
 *
 * name:  isteğe bağlı; baştaki ve sondaki boşluk atılır, boşsa etiket adsızdır.
 *        En fazla 40 karakter, panoda aynı ad iki kez olmaz (büyük/küçük harf farkı sayılmaz).
 * color: paletten bir renk: GREEN, YELLOW, ORANGE, RED, PINK, PURPLE, BLUE, SKY.
 *
 * Düzenlemede iki alan da gönderilir (kartın EDIT_CARD'ı gibi): gönderilmeyen ad silinir.
 * Doğrulama servis içinde: hata, alan listesi değil tek bir cümle olsun.
 */
public record LabelRequest(
        String name,
        String color
) {
}
