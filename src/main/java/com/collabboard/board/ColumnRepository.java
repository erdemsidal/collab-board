package com.collabboard.board;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.collabboard.board.entity.BoardColumn;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ColumnRepository extends JpaRepository<BoardColumn, Long> {

    /**
     * Birden çok panonun kolonları, panodaki sırayla — ana ekran özeti için tek sorgu.
     * Kolonun panosu yüklenmez; id'si zaten kolon satırında (yabancı anahtar).
     */
    List<BoardColumn> findByBoardIdInOrderByPositionAsc(Collection<Long> boardIds);

    /** Kolonun panosu: canlı işlemin hedefinin adresteki panoya ait olduğunu doğrulamak için. */
    @Query("SELECT c.board.id FROM BoardColumn c WHERE c.id = :columnId")
    Optional<Long> findBoardIdByColumnId(@Param("columnId") Long columnId);
}
