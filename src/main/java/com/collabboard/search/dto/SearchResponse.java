package com.collabboard.search.dto;

import com.collabboard.board.entity.BoardRole;

import java.util.List;

/**
 * Genel arama sonucu: panolar ve kartlar ayrı listelerde, en fazla 8'er.
 * Arama çok kısaysa iki liste de boştur (hata değil: kullanıcı yazmaya devam ediyor).
 */
public record SearchResponse(
        List<BoardHit> boards,
        List<CardHit> cards
) {

    public static final SearchResponse EMPTY = new SearchResponse(List.of(), List.of());

    /** Adı eşleşen pano; role kişinin o panodaki etkin rolü ("panolarım" ile aynı). */
    public record BoardHit(Long id, String name, String workspaceName, BoardRole role) {
    }

    /** Başlığı ya da açıklaması eşleşen kart ve nerede durduğu. */
    public record CardHit(Long cardId, String title, Long boardId, String boardName, String columnName) {
    }
}
