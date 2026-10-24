package com.collabboard.comment.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Bir yorumun istemciye giden hâli.
 *
 * author:   yazanın hesabı silinmişse null; arayüz "Silinmiş kullanıcı" der.
 * mentions: anılan kişiler, adlarıyla. Arayüz metindeki "@Ad Soyad"ı bunlarla
 *           eşleyip vurgular.
 */
public record CommentResponse(
        Long id,
        Long cardId,
        Person author,
        String body,
        List<Mention> mentions,
        LocalDateTime createdAt,
        LocalDateTime editedAt
) {
    public record Person(Long id, String name, String avatarUrl) {
    }

    public record Mention(Long id, String name) {
    }
}
