package com.collabboard.presence;

import com.collabboard.realtime.BroadcastService;
import com.collabboard.user.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kim hangi panoda çevrimiçi? — presence takibi.
 *
 * FAZ 2'DE: liste bu sunucunun BELLEĞİNDE tutuluyordu. Tek sunucuda kusursuzdu,
 * ama ikinci sunucu açıldığında her sunucu sadece kendi kullanıcılarını gördüğü
 * için liste YARIM kalıyordu.
 *
 * FAZ 3'TE (ADR 0004): liste REDIS'e taşındı — ortak hafıza. Artık hangi sunucu
 * sorarsa sorsun aynı tam listeyi görür:
 *
 *   Redis hash:  presence:board:{boardId}   →   { sessionId : {"id","name","color"} }
 *
 * Bellekte kalan tek şey: "benim oturumlarım hangi panoda" eşlemesi. Bu bilinçli:
 * bir bağlantı koptuğunda olayı ZATEN o bağlantının bağlı olduğu sunucu alır,
 * dolayısıyla o eşlemeyi paylaşmaya gerek yok.
 */
@Service
public class PresenceService {

    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);

    private static final String KEY_PREFIX = "presence:board:";

    /** sessionId → boardId : SADECE bu sunucuya bağlı oturumlar (yerel kalması doğru). */
    private final Map<String, Long> mySessions = new ConcurrentHashMap<>();

    private final StringRedisTemplate redis;
    private final BroadcastService broadcastService;
    private final ObjectMapper objectMapper;
    private final UserService userService;

    public PresenceService(StringRedisTemplate redis, BroadcastService broadcastService,
                           ObjectMapper objectMapper, UserService userService) {
        this.redis = redis;
        this.broadcastService = broadcastService;
        this.objectMapper = objectMapper;
        this.userService = userService;
    }

    /**
     * Bir oturum panoya katıldı → gerçek kullanıcıyı bul, Redis'e yaz, herkese duyur.
     *
     * @param email CONNECT sırasında doğrulanan kullanıcının e-postası (ADR 0005)
     */
    public void join(Long boardId, String sessionId, String email) {
        PresenceUser user = PresenceUser.from(userService.getUserByEmail(email), sessionId);

        try {
            redis.opsForHash().put(key(boardId), sessionId, objectMapper.writeValueAsString(user));
        } catch (Exception e) {
            log.error("Presence Redis'e yazılamadı: sessionId={}", sessionId, e);
            return;
        }
        mySessions.put(sessionId, boardId);

        log.info("Presence: '{}' panoya katıldı (boardId={})", user.name(), boardId);
        broadcast(boardId);
    }

    /**
     * Bir oturum kapandı (sekme kapandı / hat koptu) → Redis'ten çıkar, herkese duyur.
     * Bunu WebSocketEventListener, Spring'in "oturum kapandı" olayında çağırır.
     */
    public void leave(String sessionId) {
        Long boardId = mySessions.remove(sessionId);
        if (boardId == null) {
            return;   // bu oturum bu sunucuda panoya katılmamıştı
        }
        redis.opsForHash().delete(key(boardId), sessionId);
        log.info("Presence: bir oturum panodan ayrıldı (boardId={})", boardId);
        broadcast(boardId);
    }

    /**
     * Sunucu düzgün kapanırken kendi oturumlarını Redis'ten temizle.
     * (Sunucu ÇÖKERSE temizlik yapılamaz → "hayalet kullanıcı" kalır; gerçek
     * sistemler bunu TTL + heartbeat ile çözer — ADR 0004'te not edildi.)
     */
    @PreDestroy
    public void cleanupOnShutdown() {
        mySessions.forEach((sessionId, boardId) -> redis.opsForHash().delete(key(boardId), sessionId));
        mySessions.clear();
    }

    /** Redis'teki TAM listeyi oku ve panonun presence kanalına yayınla (tüm sunuculara). */
    private void broadcast(Long boardId) {
        // Kişi bazında TEKİLLEŞTİRME: aynı kullanıcı iki sekme açtıysa Redis'te iki
        // oturum kaydı vardır, ama panoda "iki kişi" göstermek yanlış olur.
        // LinkedHashMap: userId'ye göre tekilleştirirken sırayı korur.
        Map<Long, PresenceUser> byUser = new LinkedHashMap<>();
        for (Object value : redis.opsForHash().values(key(boardId))) {
            try {
                PresenceUser user = objectMapper.readValue(value.toString(), PresenceUser.class);
                byUser.putIfAbsent(user.userId(), user);
            } catch (Exception e) {
                log.warn("Presence kaydı okunamadı: {}", value, e);
            }
        }
        broadcastService.broadcast("/topic/board." + boardId + "/presence",
                PresenceState.of(new ArrayList<>(byUser.values())));
    }

    private String key(Long boardId) {
        return KEY_PREFIX + boardId;
    }
}
