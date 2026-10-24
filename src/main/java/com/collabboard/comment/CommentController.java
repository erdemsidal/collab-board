package com.collabboard.comment;

import com.collabboard.comment.dto.CommentRequest;
import com.collabboard.comment.dto.CommentResponse;
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
 * Kart yorumları. Pano altında: yetki panodan gelir, kartın o panoda olduğu
 * ayrıca denetlenir (CommentService).
 *
 * Yorumlar REST'ten yazılır, WebSocket operasyonu değildir: kartın sürümüne ve
 * çakışma kontrolüne girmezler. Diğer ekranlara yayını sunucu yapar.
 */
@RestController
@RequestMapping("/api/boards/{boardId}/cards/{cardId}/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping
    public ResponseEntity<List<CommentResponse>> list(@PathVariable Long boardId, @PathVariable Long cardId,
                                                      @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(commentService.list(boardId, cardId, user.getUsername()));
    }

    @PostMapping
    public ResponseEntity<CommentResponse> create(@PathVariable Long boardId, @PathVariable Long cardId,
                                                  @RequestBody CommentRequest request,
                                                  @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.create(boardId, cardId, request, user.getUsername()));
    }

    @PatchMapping("/{commentId}")
    public ResponseEntity<CommentResponse> update(@PathVariable Long boardId, @PathVariable Long cardId,
                                                  @PathVariable Long commentId,
                                                  @RequestBody CommentRequest request,
                                                  @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(commentService.update(boardId, cardId, commentId, request, user.getUsername()));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> delete(@PathVariable Long boardId, @PathVariable Long cardId,
                                       @PathVariable Long commentId,
                                       @AuthenticationPrincipal UserDetails user) {
        commentService.delete(boardId, cardId, commentId, user.getUsername());
        return ResponseEntity.noContent().build();
    }
}
