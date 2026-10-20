package com.collabboard.audit;

import com.collabboard.audit.dto.ActivityResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.collabboard.user.AvatarUrls;
import com.collabboard.user.UserRepository;
import com.collabboard.user.UserService;
import com.collabboard.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public ActivityService(BoardActivityRepository repository, UserService userService,
                           UserRepository userRepository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.userService = userService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
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
    public void record(Long boardId, String actorEmail, String type, String description, Object event) {
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
                .payload(serialize(event))
                .createdAt(LocalDateTime.now())
                .build());
    }

    /**
     * Olayı JSON'a çevirir. Çevrilemezse kayıt yine de yazılır — geçmişin insan
     * tarafından okunabilir kısmını, makine kısmı yüzünden kaybetmeyiz.
     */
    private String serialize(Object event) {
        if (event == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.warn("Olay JSON'a çevrilemedi: {}", event.getClass().getSimpleName(), e);
            return null;
        }
    }

    /**
     * Panonun son hareketleri (en yeni önce), yapanın fotoğrafıyla.
     *
     * Fotoğraflar tek sorguda çekilir: satır başına kullanıcı sorgusu atmak, 20
     * satırlık liste için 20 ayrı sorgu demekti.
     */
    public List<ActivityResponse> recentActivity(Long boardId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        List<BoardActivity> rows = repository.findByBoardIdOrderByIdDesc(boardId, PageRequest.of(0, safeLimit));

        List<Long> actorIds = rows.stream().map(BoardActivity::getUserId).filter(Objects::nonNull).distinct().toList();
        Map<Long, User> actors = userRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return rows.stream()
                .map(a -> {
                    User actor = a.getUserId() == null ? null : actors.get(a.getUserId());
                    return ActivityResponse.fromEntity(a, actor == null ? null : AvatarUrls.of(actor.getAvatarKey()));
                })
                .toList();
    }
}
