package com.collabboard.overview;

import com.collabboard.common.exception.BadRequestException;
import com.collabboard.overview.dto.OverviewResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Ana ekran özeti. Kimlik ister (SecurityConfig: açıkça izin verilmeyen her uç);
 * yanıt yalnızca isteği yapanın erişebildiği panolardan derlenir.
 */
@RestController
public class OverviewController {

    private final OverviewService overviewService;

    public OverviewController(OverviewService overviewService) {
        this.overviewService = overviewService;
    }

    /**
     * GET /api/me/overview?today=2026-10-23
     * today isteğe bağlı: istemcinin takvim günü (gecikme ona göre sayılır).
     */
    @GetMapping("/api/me/overview")
    public ResponseEntity<OverviewResponse> overview(@RequestParam(required = false) String today,
                                                     @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(overviewService.overview(user.getUsername(), parseDay(today)));
    }

    /** Bozuk tarih 400 almalı; Spring'in dönüştürme hatası genel yakalayıcıda 500'e düşerdi. */
    private static LocalDate parseDay(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new BadRequestException("today YYYY-AA-GG biçiminde bir tarih olmalı");
        }
    }
}
