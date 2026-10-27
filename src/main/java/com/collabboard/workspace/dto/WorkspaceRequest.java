package com.collabboard.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Çalışma alanı oluşturma ve yeniden adlandırma isteği.
 */
public record WorkspaceRequest(
        @NotBlank(message = "{validation.workspaceName.required}")
        @Size(min = 1, max = 200, message = "{validation.workspaceName.size}")
        String name
) {}
