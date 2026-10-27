package com.collabboard.chathook;

import com.collabboard.chathook.dto.ChatHookListResponse;
import com.collabboard.chathook.dto.ChatHookRequest;
import com.collabboard.chathook.dto.ChatHookResponse;
import com.collabboard.chathook.dto.ChatHookTestRequest;
import com.collabboard.chathook.dto.ChatHookTestResponse;
import com.collabboard.i18n.AppLocale;
import jakarta.servlet.http.HttpServletRequest;
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

/**
 * Panonun kanal bildirimleri (Slack, Teams, Discord). Yetki ve doğrulama
 * ChatHookService'te; deneme uçlarının IP başına sınırı RateLimitPolicy.CHAT_HOOK_TEST.
 */
@RestController
@RequestMapping("/api/boards/{boardId}/chat-hooks")
public class ChatHookController {

    private final ChatHookService service;

    public ChatHookController(ChatHookService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ChatHookListResponse> list(@PathVariable Long boardId,
                                                     @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(service.list(boardId, user.getUsername()));
    }

    @PostMapping
    public ResponseEntity<ChatHookResponse> create(@PathVariable Long boardId, @RequestBody ChatHookRequest request,
                                                   @AuthenticationPrincipal UserDetails user,
                                                   HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(boardId, request, user.getUsername(), AppLocale.tag(AppLocale.of(http))));
    }

    @PatchMapping("/{hookId}")
    public ResponseEntity<ChatHookResponse> update(@PathVariable Long boardId, @PathVariable Long hookId,
                                                   @RequestBody ChatHookRequest request,
                                                   @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(service.update(boardId, hookId, request, user.getUsername()));
    }

    @DeleteMapping("/{hookId}")
    public ResponseEntity<Void> delete(@PathVariable Long boardId, @PathVariable Long hookId,
                                       @AuthenticationPrincipal UserDetails user) {
        service.delete(boardId, hookId, user.getUsername());
        return ResponseEntity.noContent().build();
    }

    /** Kaydetmeden önce formdaki adresi dener. */
    @PostMapping("/test")
    public ResponseEntity<ChatHookTestResponse> testUrl(@PathVariable Long boardId,
                                                        @RequestBody ChatHookTestRequest request,
                                                        @AuthenticationPrincipal UserDetails user,
                                                        HttpServletRequest http) {
        return ResponseEntity.ok(service.testUrl(boardId, request, user.getUsername(),
                AppLocale.tag(AppLocale.of(http))));
    }

    /** Kayıtlı bağlantıyı dener. */
    @PostMapping("/{hookId}/test")
    public ResponseEntity<ChatHookTestResponse> testSaved(@PathVariable Long boardId, @PathVariable Long hookId,
                                                          @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(service.testSaved(boardId, hookId, user.getUsername()));
    }
}
