package com.collabboard.board;

import com.collabboard.board.dto.BoardResponse;
import com.collabboard.board.dto.FlowResponse;
import com.collabboard.board.dto.TimelineEntry;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Panonun geçmişi ve akış sağlığı — ikisi de olay kaydından hesaplanır.
 *
 * Yetki kontrolü servislerin içinde yapılır: bu uçlar panonun tüm geçmişini
 * açtığı için üyelik şartı atlanmamalı.
 */
@RestController
@RequestMapping("/api/boards/{boardId}")
public class BoardInsightController {

    private final BoardHistoryService historyService;
    private final FlowMetricsService flowMetricsService;

    public BoardInsightController(BoardHistoryService historyService, FlowMetricsService flowMetricsService) {
        this.historyService = historyService;
        this.flowMetricsService = flowMetricsService;
    }

    /** Geri sarılabilecek anların listesi (eskiden yeniye). */
    @GetMapping("/timeline")
    public ResponseEntity<List<TimelineEntry>> timeline(@PathVariable Long boardId,
                                                        @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(historyService.timeline(boardId, user.getUsername()));
    }

    /**
     * Panonun geçmişteki hâli.
     * upTo: bu ana kadarki olaylar uygulanır (0 = panonun boş başlangıcı).
     */
    @GetMapping("/history")
    public ResponseEntity<BoardResponse> history(@PathVariable Long boardId,
                                                 @RequestParam(defaultValue = "0") Long upTo,
                                                 @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(historyService.stateAt(boardId, upTo, user.getUsername()));
    }

    /** Akış sağlığı: kolon bekleme süreleri, çevrim süresi, darboğaz. */
    @GetMapping("/flow")
    public ResponseEntity<FlowResponse> flow(@PathVariable Long boardId,
                                             @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(flowMetricsService.analyze(boardId, user.getUsername()));
    }
}
