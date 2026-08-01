package com.collabboard.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BoardActivityRepository extends JpaRepository<BoardActivity, Long> {

    /** Bir panonun hareketleri, en yeni önce. */
    List<BoardActivity> findByBoardIdOrderByIdDesc(Long boardId, Pageable pageable);
}
