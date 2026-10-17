package com.collabboard.invitation;

import com.collabboard.board.BoardAccessService;
import com.collabboard.invitation.dto.AcceptInvitationResponse;
import com.collabboard.invitation.dto.InvitationResponse;
import com.collabboard.invitation.dto.InviteRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Pano davetleri.
 *
 * Gönderme, listeleme ve geri çekme panonun yöneticisine aittir; yanıt vermek
 * ise yalnızca davet edilen kişiye (kontrolü servis yapar).
 */
@RestController
public class InvitationController {

    private final InvitationService invitationService;
    private final BoardAccessService accessService;

    public InvitationController(InvitationService invitationService, BoardAccessService accessService) {
        this.invitationService = invitationService;
        this.accessService = accessService;
    }

    @PostMapping("/api/boards/{boardId}/invitations")
    public ResponseEntity<InvitationResponse> invite(@PathVariable Long boardId,
                                                     @Valid @RequestBody InviteRequest request,
                                                     @AuthenticationPrincipal UserDetails user) {
        accessService.requireOwner(boardId, user.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(invitationService.invite(boardId, user.getUsername(), request.email(), request.role()));
    }

    /** Bekleyen davetler. E-posta adreslerini içerdiği için yalnızca yönetici görür. */
    @GetMapping("/api/boards/{boardId}/invitations")
    public ResponseEntity<List<InvitationResponse>> pending(@PathVariable Long boardId,
                                                            @AuthenticationPrincipal UserDetails user) {
        accessService.requireOwner(boardId, user.getUsername());
        return ResponseEntity.ok(invitationService.pendingForBoard(boardId));
    }

    @DeleteMapping("/api/boards/{boardId}/invitations/{invitationId}")
    public ResponseEntity<Void> cancel(@PathVariable Long boardId, @PathVariable Long invitationId,
                                       @AuthenticationPrincipal UserDetails user) {
        accessService.requireOwner(boardId, user.getUsername());
        invitationService.cancel(boardId, invitationId, user.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/invitations/{invitationId}/accept")
    public ResponseEntity<AcceptInvitationResponse> accept(@PathVariable Long invitationId,
                                                           @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(invitationService.accept(invitationId, user.getUsername()));
    }

    @PostMapping("/api/invitations/{invitationId}/decline")
    public ResponseEntity<Void> decline(@PathVariable Long invitationId,
                                        @AuthenticationPrincipal UserDetails user) {
        invitationService.decline(invitationId, user.getUsername());
        return ResponseEntity.noContent().build();
    }
}
