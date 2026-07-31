package com.collabboard.workspace.dto;

import com.collabboard.workspace.entity.Workspace;
import com.collabboard.workspace.entity.WorkspaceRole;

/**
 * Çalışma alanı — arayüzün kenar çubuğunda gösterilen hâli.
 *
 * role: İSTEYEN KULLANICININ o alandaki rolü. Arayüz buna göre düzenleme/silme
 * düğmelerini gösterir (yetkiyi yine sunucu uygular).
 */
public record WorkspaceResponse(
        Long id,
        String name,
        String slug,
        WorkspaceRole role
) {
    public static WorkspaceResponse of(Workspace workspace, WorkspaceRole role) {
        return new WorkspaceResponse(workspace.getId(), workspace.getName(), workspace.getSlug(), role);
    }
}
