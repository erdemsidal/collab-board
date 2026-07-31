package com.collabboard.board;

import com.collabboard.board.entity.BoardMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BoardMemberRepository extends JpaRepository<BoardMember, Long> {

    /** Yetki kontrolünün kalbi: bu kullanıcının bu panodaki rolü (yoksa boş). */
    Optional<BoardMember> findByBoardIdAndUserId(Long boardId, Long userId);

    /** Panonun üye listesi. */
    List<BoardMember> findByBoardId(Long boardId);

    /** "Benim panolarım" — kullanıcının üye olduğu tüm panolar, en yeni önce. */
    List<BoardMember> findByUserIdOrderByBoardIdDesc(Long userId);

    /** Son OWNER'ı çıkarıp panoyu sahipsiz bırakmamak için sayım. */
    long countByBoardIdAndRole(Long boardId, com.collabboard.board.entity.BoardRole role);

    void deleteByBoardIdAndUserId(Long boardId, Long userId);
}
