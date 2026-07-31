package com.collabboard.board;

import com.collabboard.board.entity.Board;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Board veri erişim katmanı.
 *
 * JpaRepository<Board, Long>:
 *   - Board  → bu repo'nun yönettiği entity
 *   - Long   → Board'un @Id alanının tipi (primary key)
 *
 * Gövdesini biz yazmıyoruz — Spring Data JPA çalışma anında üretir.
 * save / findById / findAll / deleteById ... hepsi hazır gelir.
 */
public interface BoardRepository extends JpaRepository<Board, Long> {

    /** Bir çalışma alanının panoları (en yeni önce). */
    java.util.List<Board> findByWorkspaceIdOrderByIdDesc(Long workspaceId);
}
