package com.collabboard.board;

import com.collabboard.board.dto.ChangeRoleRequest;
import com.collabboard.board.operation.BoardSignal;
import com.collabboard.realtime.BroadcastService;
import com.collabboard.board.dto.MemberResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Pano üyelik yönetimi.
 *
 * Yetki kuralı burada açıkça görünür:
 *  - Üyeleri GÖRMEK için panonun üyesi olmak yeter.
 *  - Üye EKLEMEK/ÇIKARMAK için OWNER olmak gerekir.
 */
@RestController
@RequestMapping("/api/boards/{boardId}/members")
public class BoardMemberController {

    private final BoardMemberService memberService;
    private final BoardAccessService accessService;
    private final BroadcastService broadcastService;

    public BoardMemberController(BoardMemberService memberService, BoardAccessService accessService,
                                 BroadcastService broadcastService) {
        this.memberService = memberService;
        this.accessService = accessService;
        this.broadcastService = broadcastService;
    }

    @GetMapping
    public ResponseEntity<List<MemberResponse>> members(@PathVariable Long boardId,
                                                        @AuthenticationPrincipal UserDetails user) {
        accessService.requireMember(boardId, user.getUsername());
        return ResponseEntity.ok(memberService.members(boardId));
    }

    /**
     * Mevcut üyenin rolünü değiştir (yalnızca OWNER).
     *
     * Yeni üye eklemek için değil — panoya katılım davetle ve davet edilenin
     * onayıyla olur (POST /api/boards/{id}/invitations).
     */
    @PatchMapping("/{userId}")
    public ResponseEntity<MemberResponse> changeRole(@PathVariable Long boardId,
                                                     @PathVariable Long userId,
                                                     @Valid @RequestBody ChangeRoleRequest request,
                                                     @AuthenticationPrincipal UserDetails user) {
        accessService.requireOwner(boardId, user.getUsername());
        MemberResponse changed = memberService.changeRole(boardId, userId, request.role());
        broadcastService.broadcast("/topic/board." + boardId, BoardSignal.membersChanged());
        return ResponseEntity.ok(changed);
    }

    /** Üyeyi panodan çıkar (yalnızca OWNER). */
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long boardId,
                                             @PathVariable Long userId,
                                             @AuthenticationPrincipal UserDetails user) {
        accessService.requireOwner(boardId, user.getUsername());
        memberService.remove(boardId, userId);
        broadcastService.broadcast("/topic/board." + boardId, BoardSignal.membersChanged());
        return ResponseEntity.noContent().build();
    }
}
