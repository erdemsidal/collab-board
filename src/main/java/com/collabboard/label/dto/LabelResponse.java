package com.collabboard.label.dto;

import com.collabboard.label.Label;
import com.collabboard.label.LabelColor;

/** Bir etiketin istemciye giden hâli. Adsız etikette name null'dır. */
public record LabelResponse(
        Long id,
        String name,
        LabelColor color
) {
    public static LabelResponse of(Label label) {
        return new LabelResponse(label.getId(), label.getName(), label.getColor());
    }
}
