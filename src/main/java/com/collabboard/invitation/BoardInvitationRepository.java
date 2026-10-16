package com.collabboard.invitation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BoardInvitationRepository extends JpaRepository<BoardInvitation, Long> {

    List<BoardInvitation> findByBoardIdAndStatusOrderByIdDesc(Long boardId, InvitationStatus status);

    List<BoardInvitation> findByEmailAndStatusOrderByIdDesc(String email, InvitationStatus status);

    boolean existsByBoardIdAndEmailAndStatus(Long boardId, String email, InvitationStatus status);
}
