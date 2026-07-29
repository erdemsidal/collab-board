package com.collabboard.audit;

import com.collabboard.audit.dto.ActivityResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Pano geçmişi REST ucu.
 * /api/boards/** altında olduğu için kimlik doğrulaması ister (ADR 0005).
 */
@RestController
@RequestMapping("/api/boards")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    /** Panonun son hareketleri: GET /api/boards/{id}/activity?limit=20 */
    @GetMapping("/{boardId}/activity")
    public ResponseEntity<List<ActivityResponse>> activity(
            @PathVariable Long boardId,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(activityService.recentActivity(boardId, limit));
    }
}
