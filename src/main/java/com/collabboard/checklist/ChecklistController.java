package com.collabboard.checklist;

import com.collabboard.checklist.dto.ChecklistItemRequest;
import com.collabboard.checklist.dto.ChecklistRequest;
import com.collabboard.checklist.dto.ChecklistResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Kartın kontrol listeleri (ADR 0022). Pano ve kart adreste: yetki panodan gelir,
 * kartın o panoda, listenin o kartta, maddenin o listede olduğu ayrıca denetlenir.
 *
 * Listeye dokunan her istek listenin yeni hâlini döner (madde silme dahil);
 * aynı hâl panodaki herkese canlı gider.
 */
@RestController
@RequestMapping("/api/boards/{boardId}/cards/{cardId}/checklists")
public class ChecklistController {

    private final ChecklistService checklistService;

    public ChecklistController(ChecklistService checklistService) {
        this.checklistService = checklistService;
    }

    @GetMapping
    public ResponseEntity<List<ChecklistResponse>> list(@PathVariable Long boardId, @PathVariable Long cardId,
                                                        @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(checklistService.list(boardId, cardId, user.getUsername()));
    }

    @PostMapping
    public ResponseEntity<ChecklistResponse> create(@PathVariable Long boardId, @PathVariable Long cardId,
                                                    @RequestBody ChecklistRequest request,
                                                    @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(checklistService.create(boardId, cardId, request, user.getUsername()));
    }

    @PatchMapping("/{checklistId}")
    public ResponseEntity<ChecklistResponse> rename(@PathVariable Long boardId, @PathVariable Long cardId,
                                                    @PathVariable Long checklistId,
                                                    @RequestBody ChecklistRequest request,
                                                    @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(checklistService.rename(boardId, cardId, checklistId, request, user.getUsername()));
    }

    @DeleteMapping("/{checklistId}")
    public ResponseEntity<Void> delete(@PathVariable Long boardId, @PathVariable Long cardId,
                                       @PathVariable Long checklistId, @AuthenticationPrincipal UserDetails user) {
        checklistService.delete(boardId, cardId, checklistId, user.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{checklistId}/items")
    public ResponseEntity<ChecklistResponse> addItem(@PathVariable Long boardId, @PathVariable Long cardId,
                                                     @PathVariable Long checklistId,
                                                     @RequestBody ChecklistItemRequest request,
                                                     @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(checklistService.addItem(boardId, cardId, checklistId, request, user.getUsername()));
    }

    /** Metin, yapıldı ve/veya yer: { "done": true }, { "position": 0 }, { "text": "..." }. */
    @PatchMapping("/{checklistId}/items/{itemId}")
    public ResponseEntity<ChecklistResponse> updateItem(@PathVariable Long boardId, @PathVariable Long cardId,
                                                        @PathVariable Long checklistId, @PathVariable Long itemId,
                                                        @RequestBody ChecklistItemRequest request,
                                                        @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(checklistService.updateItem(boardId, cardId, checklistId, itemId, request,
                user.getUsername()));
    }

    @DeleteMapping("/{checklistId}/items/{itemId}")
    public ResponseEntity<ChecklistResponse> deleteItem(@PathVariable Long boardId, @PathVariable Long cardId,
                                                        @PathVariable Long checklistId, @PathVariable Long itemId,
                                                        @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(checklistService.deleteItem(boardId, cardId, checklistId, itemId,
                user.getUsername()));
    }
}
