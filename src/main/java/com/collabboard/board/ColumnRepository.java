package com.collabboard.board;

import org.springframework.data.jpa.repository.JpaRepository;

import com.collabboard.board.entity.BoardColumn;

public interface ColumnRepository extends JpaRepository<BoardColumn, Long> {

}
