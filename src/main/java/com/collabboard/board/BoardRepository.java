package com.collabboard.board;

import com.collabboard.board.entity.Board;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardRepository extends JpaRepository<Board, Long> {

    /** Bir çalışma alanının panoları (en yeni önce). */
    java.util.List<Board> findByWorkspaceIdOrderByIdDesc(Long workspaceId);
}
