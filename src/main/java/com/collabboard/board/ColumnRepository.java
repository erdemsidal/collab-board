package com.collabboard.board;

import org.springframework.data.jpa.repository.JpaRepository;

import com.collabboard.board.entity.BoardColumn;

import java.util.Collection;
import java.util.List;

public interface ColumnRepository extends JpaRepository<BoardColumn, Long> {

    /**
     * Birden çok panonun kolonları, panodaki sırayla — ana ekran özeti için tek sorgu.
     * Kolonun panosu yüklenmez; id'si zaten kolon satırında (yabancı anahtar).
     */
    List<BoardColumn> findByBoardIdInOrderByPositionAsc(Collection<Long> boardIds);
}
