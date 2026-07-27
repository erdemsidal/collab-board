package com.collabboard.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /boards isteğinin gövdesi. İstemciden tek istediğimiz: panonun adı.
 * Kolonları (To Do / Doing / Done) sunucu varsayılan olarak kendisi ekler.
 */
public record CreateBoardRequest(
        @NotBlank(message = "Pano adı boş olamaz")
        @Size(min = 1, max = 200, message = "Pano adı en fazla 200 karakter olabilir")
        String name
) {}
