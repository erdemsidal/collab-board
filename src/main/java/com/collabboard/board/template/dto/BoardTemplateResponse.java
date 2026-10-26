package com.collabboard.board.template.dto;

import java.util.List;

/**
 * Pano açma penceresindeki bir şablon: adı ve kolonlarının önizlemesi, kişinin dilinde.
 *
 * @param isDefault şablon seçilmezse bu kullanılır (Basit Kanban)
 */
public record BoardTemplateResponse(
        String id,
        String name,
        boolean isDefault,
        List<Column> columns
) {

    /** wipLimit yoksa JSON'a hiç yazılmaz (non_null). */
    public record Column(String name, Integer wipLimit) {
    }
}
