package com.collabboard.board.dto;

/**
 * Anahtarı çözülmüş kart: nerede durduğu ve kısa adları (ADR 0021).
 * Paylaşılan /?card=STR-42 bağlantısı bununla doğru panoyu ve kartı açar.
 *
 * @param key    "STR-42"
 * @param branch git dalı adı önerisi: "str-42-odeme-sayfasi"
 */
public record CardKeyResponse(
        String key,
        Long cardId,
        Long boardId,
        String boardName,
        String columnName,
        String title,
        String branch
) {}
