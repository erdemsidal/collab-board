package com.collabboard.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RFC 7807 Problem Details uyumlu hata response'u.
 * Tüm API hataları bu formatta döner — tutarlı client experience.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> fieldErrors
) {
    /**
     * Validation hatalarında her field için detay.
     */
    public record FieldError(
            String field,
            String message,
            Object rejectedValue
    ) {}

    // ── Factory Methods ──────────────────────────────

    public static ApiErrorResponse of(int status, String error, String message, String path) {
        return new ApiErrorResponse(
                LocalDateTime.now(), status, error, message, path, null
        );
    }

    public static ApiErrorResponse withFieldErrors(int status, String error, String message,
                                                    String path, List<FieldError> fieldErrors) {
        return new ApiErrorResponse(
                LocalDateTime.now(), status, error, message, path, fieldErrors
        );
    }
}
