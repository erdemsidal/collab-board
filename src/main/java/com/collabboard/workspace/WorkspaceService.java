package com.collabboard.workspace;

import com.collabboard.common.exception.ResourceNotFoundException;
import com.collabboard.user.UserService;
import com.collabboard.user.entity.User;
import com.collabboard.workspace.dto.WorkspaceResponse;
import com.collabboard.workspace.entity.Workspace;
import com.collabboard.workspace.entity.WorkspaceRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Çalışma alanı oluşturma ve "kişisel alan" yönetimi.
 */
@Service
@Transactional(readOnly = true)
public class WorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceService.class);

    /** Kişisel alanların slug öneki — V7 migration'ındaki kuralla aynı olmalı. */
    private static final String PERSONAL_SLUG_PREFIX = "kisisel-";

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final WorkspaceAccessService accessService;
    private final UserService userService;

    public WorkspaceService(WorkspaceRepository workspaceRepository,
                            WorkspaceMemberRepository memberRepository,
                            WorkspaceAccessService accessService,
                            UserService userService) {
        this.workspaceRepository = workspaceRepository;
        this.memberRepository = memberRepository;
        this.accessService = accessService;
        this.userService = userService;
    }

    /** Yeni çalışma alanı kur; kuran kişi OWNER olur. */
    @Transactional
    public Workspace create(String name, User owner) {
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .name(name)
                .slug(uniqueSlug(name))
                .createdAt(LocalDateTime.now())
                .build());

        accessService.addMember(workspace.getId(), owner.getId(), WorkspaceRole.OWNER);
        log.info("Çalışma alanı kuruldu: id={}, name='{}', sahibi={}",
                workspace.getId(), name, owner.getEmail());
        return workspace;
    }

    /**
     * Kullanıcının kişisel alanı — yoksa oluşturur ("get or create").
     *
     * NEDEN TEMBEL (lazy) OLUŞTURUYORUZ? V7 migration'ı, o an KAYITLI olan
     * kullanıcılara kişisel alan açtı. Sonradan kayıt olanların da bir alanı
     * olmalı; kayıt akışına dokunmak yerine ilk ihtiyaç anında oluşturuyoruz.
     * Slug deseni migration ile aynı olduğu için iki yol asla çakışmaz.
     */
    @Transactional
    public Workspace personalWorkspaceFor(User user) {
        String slug = PERSONAL_SLUG_PREFIX + user.getId();
        return workspaceRepository.findBySlug(slug).orElseGet(() -> {
            String displayName = (user.getFirstName() + " " + user.getLastName()).trim();
            if (displayName.isBlank()) {
                displayName = user.getEmail();
            }
            Workspace workspace = workspaceRepository.save(Workspace.builder()
                    .name(displayName + " — Kişisel Alan")
                    .slug(slug)
                    .createdAt(LocalDateTime.now())
                    .build());
            accessService.addMember(workspace.getId(), user.getId(), WorkspaceRole.OWNER);
            log.info("Kişisel alan oluşturuldu: userId={}, workspaceId={}", user.getId(), workspace.getId());
            return workspace;
        });
    }

    /** Kullanıcının üyesi olduğu çalışma alanları, kendi rolüyle. */
    public List<WorkspaceResponse> myWorkspaces(String email) {
        Long userId = userService.getUserByEmail(email).getId();

        return memberRepository.findByUserIdOrderByWorkspaceIdDesc(userId).stream()
                .map(membership -> workspaceRepository.findById(membership.getWorkspaceId())
                        .map(workspace -> WorkspaceResponse.of(workspace, membership.getRole()))
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    /** Adı değiştir (yalnızca yönetici). Slug DEĞİŞMEZ — varsa dış bağlantılar kırılmasın. */
    @Transactional
    public Workspace rename(Long workspaceId, String newName) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Çalışma alanı", "id", workspaceId));
        workspace.setName(newName);
        log.info("Çalışma alanı yeniden adlandırıldı: id={}, yeni ad='{}'", workspaceId, newName);
        return workspaceRepository.save(workspace);
    }

    /**
     * Çalışma alanını sil — içindeki panolar, kartlar ve geçmiş de silinir.
     * Silme zinciri veritabanında tanımlıdır (V8: ON DELETE CASCADE), bu yüzden
     * burada tek tek dolaşmıyoruz.
     */
    @Transactional
    public void delete(Long workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Çalışma alanı", "id", workspaceId));
        workspaceRepository.delete(workspace);
        log.info("Çalışma alanı silindi: id={}, name='{}'", workspaceId, workspace.getName());
    }

    /**
     * İnsan tarafından okunabilir, benzersiz slug üretir.
     * "Acme Yazılım A.Ş." → "acme-yazilim-as" (gerekirse sonuna sayı eklenir).
     */
    private String uniqueSlug(String name) {
        // ÖNCE Türkçe harfleri elle karşılıklarına çevir.
        // Neden gerekli? Unicode sadeleştirmesi (NFD) ş/ğ/ü/ö/ç gibi harfleri
        // "taban harf + işaret" olarak çözebilir, ama NOKTASIZ "ı" ayrı bir
        // harftir ve çözümü yoktur — sadeleştirmeye bırakılırsa eleniyor ve
        // "Yazılım" → "yaz-l-m" gibi bozuk bir slug çıkıyor.
        String base = name
                .replace('ı', 'i').replace('İ', 'I')
                .replace('ş', 's').replace('Ş', 'S')
                .replace('ğ', 'g').replace('Ğ', 'G')
                .replace('ü', 'u').replace('Ü', 'U')
                .replace('ö', 'o').replace('Ö', 'O')
                .replace('ç', 'c').replace('Ç', 'C');

        base = Normalizer.normalize(base, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")           // kalan aksanları at
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")      // harf/rakam dışını tireye çevir
                .replaceAll("(^-|-$)", "");         // baştaki/sondaki tireleri at
        if (base.isBlank()) {
            base = "calisma-alani";
        }
        if (base.length() > 60) {
            base = base.substring(0, 60);
        }

        String slug = base;
        int suffix = 2;
        while (workspaceRepository.existsBySlug(slug)) {
            slug = base + "-" + suffix++;
        }
        return slug;
    }
}
