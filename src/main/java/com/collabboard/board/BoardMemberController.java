package com.collabboard.board;

import com.collabboard.board.dto.AddMemberRequest;
import com.collabboard.board.dto.MemberResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    public BoardMemberController(BoardMemberService memberService, BoardAccessService accessService) {
        this.memberService = memberService;
        this.accessService = accessService;
    }

    @GetMapping
    public ResponseEntity<List<MemberResponse>> members(@PathVariable Long boardId,
                                                        @AuthenticationPrincipal UserDetails user) {
        accessService.requireMember(boardId, user.getUsername());
        return ResponseEntity.ok(memberService.members(boardId));
    }

    /** Üye ekle veya mevcut üyenin rolünü değiştir (yalnızca OWNER). */
    @PostMapping
    public ResponseEntity<MemberResponse> addMember(@PathVariable Long boardId,
                                                    @Valid @RequestBody AddMemberRequest request,
                                                    @AuthenticationPrincipal UserDetails user) {
        accessService.requireOwner(boardId, user.getUsername());
        return ResponseEntity.ok(memberService.addOrUpdate(boardId, request.email(), request.role()));
    }

    /** Üyeyi panodan çıkar (yalnızca OWNER). */
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long boardId,
                                             @PathVariable Long userId,
                                             @AuthenticationPrincipal UserDetails user) {
        accessService.requireOwner(boardId, user.getUsername());
        memberService.remove(boardId, userId);
        return ResponseEntity.noContent().build();
    }
}
