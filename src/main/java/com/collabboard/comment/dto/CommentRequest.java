package com.collabboard.comment.dto;

import java.util.List;

/**
 * Yorum yazma ve düzenleme isteği.
 *
 * body:     yorum metni; baştaki ve sondaki boşluk atılır, 1..5000 karakter.
 * mentions: anılan kişilerin id'leri. Panoya erişemeyen ve yazanın kendisi
 *           sessizce düşülür; en fazla 20.
 *
 * Doğrulama servis içinde: hata mesajı alan listesi değil, tek bir Türkçe cümle olsun.
 */
public record CommentRequest(
        String body,
        List<Long> mentions
) {
}
