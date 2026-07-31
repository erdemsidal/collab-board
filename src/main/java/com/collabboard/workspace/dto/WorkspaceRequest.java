package com.collabboard.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Çalışma alanı oluşturma ve yeniden adlandırma isteği.
 */
public record WorkspaceRequest(
        @NotBlank(message = "Çalışma alanı adı boş olamaz")
        @Size(min = 1, max = 200, message = "Ad en fazla 200 karakter olabilir")
        String name
) {}
