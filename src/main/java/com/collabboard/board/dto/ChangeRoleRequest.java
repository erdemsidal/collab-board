package com.collabboard.board.dto;

import com.collabboard.board.entity.BoardRole;
import jakarta.validation.constraints.NotNull;

/** Mevcut bir üyenin yeni rolü. */
public record ChangeRoleRequest(
        @NotNull(message = "Rol belirtilmeli (OWNER, EDITOR, VIEWER)")
        BoardRole role
) {
}
