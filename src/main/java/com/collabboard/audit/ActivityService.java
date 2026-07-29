package com.collabboard.audit;

import com.collabboard.audit.dto.ActivityResponse;
import com.collabboard.user.UserService;
import com.collabboard.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Pano geçmişi (audit) — kaydetme ve okuma.
 */
@Service
@Transactional(readOnly = true)
public class ActivityService {

    private static final Logger log = LoggerFactory.getLogger(ActivityService.class);
    private static final int MAX_LIMIT = 100;

    private final BoardActivityRepository repository;
    private final UserService userService;

    public ActivityService(BoardActivityRepository repository, UserService userService) {
        this.repository = repository;
        this.userService = userService;
    }

    /**
     * Bir hareketi geçmişe yaz.
     *
     * DİKKAT: Bu metot, operasyonu uygulayan işlemin (transaction) İÇİNDE çağrılır.
     * Yani operasyon reddedilir/geri alınırsa (ADR 0003) geçmiş kaydı da yazılmaz —
     * "olmayan bir olayı" geçmişe yazmayız. Bu, doğruluk açısından önemlidir.
     *
     * @param actorEmail işlemi yapan kullanıcının e-postası (WebSocket kimliğinden)
     */
    @Transactional
    public void record(Long boardId, String actorEmail, String type, String description) {
        Long userId = null;
        String actorName = actorEmail;   // kullanıcı bulunamazsa en azından e-posta yazılsın
        try {
            User user = userService.getUserByEmail(actorEmail);
            userId = user.getId();
            actorName = (user.getFirstName() + " " + user.getLastName()).trim();
        } catch (Exception e) {
            log.warn("Audit: kullanıcı bulunamadı, e-posta ile kaydediliyor: {}", actorEmail);
        }

        repository.save(BoardActivity.builder()
                .boardId(boardId)
                .userId(userId)
                .actorName(actorName)
                .type(type)
                .description(description)
                .createdAt(LocalDateTime.now())
                .build());
    }

    /** Panonun son hareketleri (en yeni önce). */
    public List<ActivityResponse> recentActivity(Long boardId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        return repository.findByBoardIdOrderByIdDesc(boardId, PageRequest.of(0, safeLimit))
                .stream()
                .map(ActivityResponse::fromEntity)
                .toList();
    }
}
