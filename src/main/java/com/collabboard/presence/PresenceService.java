package com.collabboard.presence;

import com.collabboard.realtime.BroadcastService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
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
    private static final String COUNTER_KEY = "presence:counter";

    /** Geçici kimlikler (auth yok) — sırayla dağıtılır. TODO(faz4): gerçek kullanıcı. */
    private static final List<String> NAMES = List.of(
            "Panda", "Kaplan", "Kartal", "Tilki", "Kunduz", "Baykuş", "Ceylan", "Şahin");
    private static final List<String> COLORS = List.of(
            "#e05b49", "#f5871f", "#35b37e", "#0079bf", "#8777d9", "#00b8d9", "#ff8f73", "#6554c0");

    /** sessionId → boardId : SADECE bu sunucuya bağlı oturumlar (yerel kalması doğru). */
    private final Map<String, Long> mySessions = new ConcurrentHashMap<>();

    private final StringRedisTemplate redis;
    private final BroadcastService broadcastService;
    private final ObjectMapper objectMapper;

    public PresenceService(StringRedisTemplate redis, BroadcastService broadcastService,
                           ObjectMapper objectMapper) {
        this.redis = redis;
        this.broadcastService = broadcastService;
        this.objectMapper = objectMapper;
    }

    /** Bir oturum panoya katıldı → kimlik ata, Redis'e yaz, herkese duyur. */
    public void join(Long boardId, String sessionId) {
        // Sayaç da Redis'te: iki sunucu da 0'dan başlasaydı ikisi de "Panda" derdi.
        // INCR atomiktir (aynı anda artıran iki sunucu aynı sayıyı almaz).
        long n = redis.opsForValue().increment(COUNTER_KEY);
        PresenceUser user = new PresenceUser(
                sessionId,
                NAMES.get((int) (n % NAMES.size())),
                COLORS.get((int) (n % COLORS.size())));

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
        List<PresenceUser> users = new ArrayList<>();
        for (Object value : redis.opsForHash().values(key(boardId))) {
            try {
                users.add(objectMapper.readValue(value.toString(), PresenceUser.class));
            } catch (Exception e) {
                log.warn("Presence kaydı okunamadı: {}", value, e);
            }
        }
        broadcastService.broadcast("/topic/board." + boardId + "/presence", PresenceState.of(users));
    }

    private String key(Long boardId) {
        return KEY_PREFIX + boardId;
    }
}
