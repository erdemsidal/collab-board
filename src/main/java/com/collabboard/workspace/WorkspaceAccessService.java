package com.collabboard.workspace;

import com.collabboard.common.exception.BadRequestException;
import com.collabboard.common.exception.ForbiddenException;
import com.collabboard.common.exception.ResourceNotFoundException;
import com.collabboard.user.UserService;
import com.collabboard.workspace.entity.WorkspaceMember;
import com.collabboard.workspace.entity.WorkspaceRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Çalışma alanı yetkilendirmesinin tek kapısı.
 *
 * BoardAccessService'in şirket seviyesindeki karşılığı; aynı deseni izler:
 * kontroller tek yerde toplanır, roller kendi yetkilerini bilir.
 */
@Service
@Transactional(readOnly = true)
public class WorkspaceAccessService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceAccessService.class);

    private final WorkspaceMemberRepository memberRepository;
    private final UserService userService;

    public WorkspaceAccessService(WorkspaceMemberRepository memberRepository, UserService userService) {
        this.memberRepository = memberRepository;
        this.userService = userService;
    }

    /**
     * Kullanıcının şirketteki rolü — YOKSA boş döner (exception fırlatmaz).
     *
     * Bu metot pano yetkilendirmesinden de çağrılır: orada "şirket üyesi değilse
     * pano bazlı istisnaya bak" gibi bir akış var, yani üye olmamak tek başına
     * hata değildir. Hata fırlatan sürüm aşağıdaki requireMember'dır.
     */
    public Optional<WorkspaceRole> roleOf(Long workspaceId, Long userId) {
        return memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .map(WorkspaceMember::getRole);
    }

    /** Şirket üyesi olmayı zorunlu kılar. */
    public WorkspaceRole requireMember(Long workspaceId, String email) {
        Long userId = userService.getUserByEmail(email).getId();
        return roleOf(workspaceId, userId)
                .orElseThrow(() -> {
                    log.warn("Yetkisiz çalışma alanı erişimi: workspaceId={}, kullanıcı={}", workspaceId, email);
                    return new ForbiddenException("Bu çalışma alanına erişim yetkiniz yok");
                });
    }

    /** Üye yönetimi için OWNER veya ADMIN olmayı zorunlu kılar. */
    public WorkspaceRole requireAdmin(Long workspaceId, String email) {
        WorkspaceRole role = requireMember(workspaceId, email);
        if (!role.canManageMembers()) {
            throw new ForbiddenException("Bu işlem için çalışma alanı yöneticisi olmalısınız (rolünüz: " + role + ")");
        }
        return role;
    }

    /** Pano açabilmek için GUEST olmamayı zorunlu kılar. */
    public WorkspaceRole requireBoardCreator(Long workspaceId, String email) {
        WorkspaceRole role = requireMember(workspaceId, email);
        if (!role.canCreateBoards()) {
            throw new ForbiddenException("Misafirler bu çalışma alanında pano oluşturamaz");
        }
        return role;
    }

    /** Üye ekler; kullanıcı zaten üyeyse rolünü günceller. */
    @Transactional
    public WorkspaceMember addMember(Long workspaceId, Long userId, WorkspaceRole role) {
        WorkspaceMember member = memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseGet(() -> WorkspaceMember.builder()
                        .workspaceId(workspaceId)
                        .userId(userId)
                        .createdAt(LocalDateTime.now())
                        .build());
        member.setRole(role);
        return memberRepository.save(member);
    }

    /**
     * Üyeyi şirketten çıkarır — şirketin TÜM panolarına erişimi tek işlemde biter.
     * Özelliğin asıl kazancı budur (panoları tek tek dolaşmaya gerek yok).
     *
     * Son OWNER çıkarılamaz: aksi hâlde şirket yönetilemez hâle gelirdi
     * (panoda uyguladığımız kuralın şirket seviyesindeki karşılığı).
     */
    @Transactional
    public void removeMember(Long workspaceId, Long userId) {
        WorkspaceMember member = memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Çalışma alanı üyesi", "userId", userId));

        if (member.getRole() == WorkspaceRole.OWNER
                && memberRepository.countByWorkspaceIdAndRole(workspaceId, WorkspaceRole.OWNER) <= 1) {
            throw new BadRequestException(
                    "Çalışma alanının son sahibini çıkaramazsınız. Önce başka bir sahip atayın.");
        }
        memberRepository.delete(member);
        log.info("Çalışma alanından üye çıkarıldı: workspaceId={}, userId={}", workspaceId, userId);
    }
}
