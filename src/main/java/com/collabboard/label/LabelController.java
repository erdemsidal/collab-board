package com.collabboard.label;

import com.collabboard.label.dto.LabelRequest;
import com.collabboard.label.dto.LabelResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Panonun etiketleri ve kartlara takılmaları (ADR 0022).
 *
 * Etiketlerin listesi ayrı bir uçtan değil, panonun fotoğrafından gelir
 * (GET /api/boards/{id} → labels, her kartta labelIds). Değişiklikler REST'ten
 * yazılır, diğer ekranlara yayını sunucu yapar.
 */
@RestController
@RequestMapping("/api/boards/{boardId}")
public class LabelController {

    private final LabelService labelService;

    public LabelController(LabelService labelService) {
        this.labelService = labelService;
    }

    @PostMapping("/labels")
    public ResponseEntity<LabelResponse> create(@PathVariable Long boardId, @RequestBody LabelRequest request,
                                                @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(labelService.create(boardId, request, user.getUsername()));
    }

    @PatchMapping("/labels/{labelId}")
    public ResponseEntity<LabelResponse> update(@PathVariable Long boardId, @PathVariable Long labelId,
                                                @RequestBody LabelRequest request,
                                                @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(labelService.update(boardId, labelId, request, user.getUsername()));
    }

    @DeleteMapping("/labels/{labelId}")
    public ResponseEntity<Void> delete(@PathVariable Long boardId, @PathVariable Long labelId,
                                       @AuthenticationPrincipal UserDetails user) {
        labelService.delete(boardId, labelId, user.getUsername());
        return ResponseEntity.noContent().build();
    }

    /** Etiketi karta takar. Tekrar çağırmak zararsız (zaten takılıysa yine 204). */
    @PutMapping("/cards/{cardId}/labels/{labelId}")
    public ResponseEntity<Void> addToCard(@PathVariable Long boardId, @PathVariable Long cardId,
                                          @PathVariable Long labelId, @AuthenticationPrincipal UserDetails user) {
        labelService.addToCard(boardId, cardId, labelId, user.getUsername());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/cards/{cardId}/labels/{labelId}")
    public ResponseEntity<Void> removeFromCard(@PathVariable Long boardId, @PathVariable Long cardId,
                                               @PathVariable Long labelId,
                                               @AuthenticationPrincipal UserDetails user) {
        labelService.removeFromCard(boardId, cardId, labelId, user.getUsername());
        return ResponseEntity.noContent().build();
    }
}
