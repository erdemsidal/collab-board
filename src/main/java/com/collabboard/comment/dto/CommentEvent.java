package com.collabboard.comment.dto;

import com.collabboard.board.operation.BoardEvent;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Pano kanalına (/topic/board.{id}) giden yorum olayı.
 *
 * Kart operasyonlarından (ADD_CARD, MOVE_CARD...) ayrı tutulur: sürüm taşımaz,
 * çakışma kontrolüne girmez, zaman yolculuğunda oynatılmaz. Açık kart penceresi
 * listesini, pano da karttaki yorum sayısını bununla günceller.
 *
 * commentCount: kartın olaydan sonraki yorum sayısı. İstemci kendisi saymaz;
 * kaçırdığı bir olay yüzünden sayı kaymasın.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommentEvent(
        String type,
        Long cardId,
        CommentResponse comment,
        Long commentId,
        long commentCount
) implements BoardEvent {

    public static CommentEvent added(CommentResponse comment, long commentCount) {
        return new CommentEvent("COMMENT_ADDED", comment.cardId(), comment, comment.id(), commentCount);
    }

    public static CommentEvent edited(CommentResponse comment, long commentCount) {
        return new CommentEvent("COMMENT_EDITED", comment.cardId(), comment, comment.id(), commentCount);
    }

    public static CommentEvent deleted(Long cardId, Long commentId, long commentCount) {
        return new CommentEvent("COMMENT_DELETED", cardId, null, commentId, commentCount);
    }
}
