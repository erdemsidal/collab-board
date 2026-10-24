package com.collabboard.comment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * Kartın yorumları eskiden yeniye, anılanlarla birlikte TEK sorguda.
     * Anılanlar ayrı bir tabloda; birleştirilmeden okunsa yorum başına bir sorgu daha giderdi.
     */
    @Query("""
            SELECT DISTINCT c FROM Comment c LEFT JOIN FETCH c.mentionIds
            WHERE c.cardId = :cardId ORDER BY c.id ASC
            """)
    List<Comment> findByCardIdWithMentions(@Param("cardId") Long cardId);

    Optional<Comment> findByIdAndCardId(Long id, Long cardId);

    long countByCardId(Long cardId);

    /**
     * Panodaki kartların yorum sayıları, tek gruplu sorguda (pano açılışındaki
     * "fotoğraf" için). Yorumu olmayan kart satır olarak gelmez; sayısı 0'dır.
     */
    @Query("""
            SELECT c.cardId AS cardId, COUNT(c) AS comments
            FROM Comment c JOIN Card k ON k.id = c.cardId
            WHERE k.column.board.id = :boardId
            GROUP BY c.cardId
            """)
    List<CardCount> countPerCard(@Param("boardId") Long boardId);

    interface CardCount {
        Long getCardId();

        long getComments();
    }
}
