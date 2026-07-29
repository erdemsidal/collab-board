package com.collabboard.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BoardActivityRepository extends JpaRepository<BoardActivity, Long> {

    /**
     * Bir panonun hareketleri, EN YENİ ÖNCE.
     *
     * Metot adı sorguyu tanımlar (Spring Data "query derivation"):
     *   findBy + BoardId          → WHERE board_id = ?
     *   OrderByIdDesc             → ORDER BY id DESC
     *   Pageable parametresi      → LIMIT/OFFSET
     * Tek satır SQL yazmadan çalışır.
     */
    List<BoardActivity> findByBoardIdOrderByIdDesc(Long boardId, Pageable pageable);
}
