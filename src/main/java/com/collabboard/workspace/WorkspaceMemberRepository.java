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

    /**
     * Alanların panolarını gören üyeler (kişi başına bir kez) — ana ekrandaki "ekip" için.
     * Misafir dışarıda bırakılır: alan rolü ona hiçbir pano açmaz (BoardAccessService.toBoardRole);
     * davet edildiği pano varsa o panonun üyesi olarak zaten gelir.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT DISTINCT m.userId FROM WorkspaceMember m
            WHERE m.workspaceId IN :workspaceIds AND m.role <> :excluded
            """)
    List<Long> findUserIdsByWorkspaceIdInAndRoleNot(
            @org.springframework.data.repository.query.Param("workspaceIds") java.util.Collection<Long> workspaceIds,
            @org.springframework.data.repository.query.Param("excluded") WorkspaceRole excluded);
}
