package com.collabboard.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /boards isteğinin gövdesi. İstemciden tek istediğimiz: panonun adı.
 * Kolonları (To Do / Doing / Done) sunucu varsayılan olarak kendisi ekler.
 */
public record CreateBoardRequest(
        @NotBlank(message = "{validation.boardName.required}")
        @Size(min = 1, max = 200, message = "{validation.boardName.size}")
        String name,

        /**
         * Panonun açılacağı çalışma alanı. Boş bırakılırsa kullanıcının
         * kişisel alanına açılır (ilk kullanımda otomatik oluşturulur).
         */
        Long workspaceId
) {}
