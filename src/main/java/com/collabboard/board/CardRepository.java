package com.collabboard.board;

import org.springframework.data.jpa.repository.JpaRepository;

import com.collabboard.board.entity.Card;

public interface CardRepository extends JpaRepository<Card, Long> {

    /**
     * Bir panodaki toplam kart sayısı (pano listesindeki "N kart" bilgisi).
     * Kart → kolon → pano zinciri üzerinden sayar; ara listeyi belleğe çekmez.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT COUNT(c) FROM Card c WHERE c.column.board.id = :boardId")
    long countByBoardId(@org.springframework.data.repository.query.Param("boardId") Long boardId);
}
