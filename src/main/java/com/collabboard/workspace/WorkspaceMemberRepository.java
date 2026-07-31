package com.collabboard.workspace;

import com.collabboard.workspace.entity.WorkspaceMember;
import com.collabboard.workspace.entity.WorkspaceRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, Long> {

    /** Etkin rol çözümlemesinin ikinci adımı: kişinin şirketteki rolü. */
    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(Long workspaceId, Long userId);

    List<WorkspaceMember> findByWorkspaceId(Long workspaceId);

    /** "Şirketlerim" — kullanıcının üye olduğu tüm çalışma alanları. */
    List<WorkspaceMember> findByUserIdOrderByWorkspaceIdDesc(Long userId);

    /** Son sahibi çıkarmayı engellemek için. */
    long countByWorkspaceIdAndRole(Long workspaceId, WorkspaceRole role);
}
