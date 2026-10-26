package com.collabboard.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /boards isteğinin gövdesi. Zorunlu olan yalnızca panonun adı.
 * Kolonları sunucu, seçilen şablondan açan kişinin dilinde ekler (ADR 0021).
 */
public record CreateBoardRequest(
        @NotBlank(message = "{validation.boardName.required}")
        @Size(min = 1, max = 200, message = "{validation.boardName.size}")
        String name,

        /**
         * Panonun açılacağı çalışma alanı. Boş bırakılırsa kullanıcının
         * kişisel alanına açılır (ilk kullanımda otomatik oluşturulur).
         */
        Long workspaceId,

        /**
         * Pano şablonu: kanban, software, content, personal, blank
         * (GET /api/boards/templates). Boşsa kanban; tanınmıyorsa 400.
         */
        String templateId
) {}
