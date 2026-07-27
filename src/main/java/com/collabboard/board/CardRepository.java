package com.collabboard.board;

import org.springframework.data.jpa.repository.JpaRepository;

import com.collabboard.board.entity.Card;

public interface CardRepository extends JpaRepository<Card, Long> {

}
