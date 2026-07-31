package com.collabboard.workspace;

import com.collabboard.user.entity.User;
import com.collabboard.workspace.entity.Workspace;
import com.collabboard.workspace.entity.WorkspaceRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Locale;

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
    private final WorkspaceAccessService accessService;

    public WorkspaceService(WorkspaceRepository workspaceRepository, WorkspaceAccessService accessService) {
        this.workspaceRepository = workspaceRepository;
        this.accessService = accessService;
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

    /**
     * İnsan tarafından okunabilir, benzersiz slug üretir.
     * "Acme Yazılım A.Ş." → "acme-yazilim-as" (gerekirse sonuna sayı eklenir).
     */
    private String uniqueSlug(String name) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")           // Türkçe aksanları sadeleştir
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
