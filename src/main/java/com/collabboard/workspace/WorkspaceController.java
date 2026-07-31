package com.collabboard.workspace;

import com.collabboard.user.UserService;
import com.collabboard.workspace.dto.WorkspaceRequest;
import com.collabboard.workspace.dto.WorkspaceResponse;
import com.collabboard.workspace.entity.Workspace;
import com.collabboard.workspace.entity.WorkspaceRole;
import jakarta.validation.Valid;
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
 * Çalışma alanı (şirket) uçları.
 *
 * Yetki kuralı:
 *  - Listeleme: kendi üyeliklerin
 *  - Oluşturma: giriş yapmış herkes (kuran kişi OWNER olur)
 *  - Yeniden adlandırma / silme: yalnızca OWNER veya ADMIN
 */
@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final WorkspaceAccessService accessService;
    private final UserService userService;

    public WorkspaceController(WorkspaceService workspaceService, WorkspaceAccessService accessService,
                               UserService userService) {
        this.workspaceService = workspaceService;
        this.accessService = accessService;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<WorkspaceResponse>> myWorkspaces(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(workspaceService.myWorkspaces(user.getUsername()));
    }

    @PostMapping
    public ResponseEntity<WorkspaceResponse> create(@Valid @RequestBody WorkspaceRequest request,
                                                    @AuthenticationPrincipal UserDetails user) {
        Workspace workspace = workspaceService.create(request.name(),
                userService.getUserByEmail(user.getUsername()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(WorkspaceResponse.of(workspace, WorkspaceRole.OWNER));
    }

    @PatchMapping("/{workspaceId}")
    public ResponseEntity<WorkspaceResponse> rename(@PathVariable Long workspaceId,
                                                    @Valid @RequestBody WorkspaceRequest request,
                                                    @AuthenticationPrincipal UserDetails user) {
        WorkspaceRole role = accessService.requireAdmin(workspaceId, user.getUsername());
        Workspace workspace = workspaceService.rename(workspaceId, request.name());
        return ResponseEntity.ok(WorkspaceResponse.of(workspace, role));
    }

    /** Alanı ve içindeki TÜM panoları siler (geri alınamaz). */
    @DeleteMapping("/{workspaceId}")
    public ResponseEntity<Void> delete(@PathVariable Long workspaceId,
                                       @AuthenticationPrincipal UserDetails user) {
        accessService.requireAdmin(workspaceId, user.getUsername());
        workspaceService.delete(workspaceId);
        return ResponseEntity.noContent().build();
    }
}
