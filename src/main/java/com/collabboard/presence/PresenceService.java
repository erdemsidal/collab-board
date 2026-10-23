package com.collabboard.presence;

import com.collabboard.realtime.BroadcastService;
import com.collabboard.user.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Kim hangi panoda çevrimiçi? — presence takibi.
 *
 * Liste Redis'te tutulur (ADR 0004). Sunucunun kendi belleğinde tutulsaydı her
 * kopya yalnızca kendi kullanıcılarını görür ve liste yarım kalırdı:
 *
 *   Redis hash:  presence:board:{boardId}   →   { sessionId : {"id","name","color"} }
 *
 * Bellekte kalan tek şey: "benim oturumlarım hangi panoda" eşlemesi. Bu bilinçli:
 * bir bağlantı koptuğunda olayı ZATEN o bağlantının bağlı olduğu sunucu alır,
 * dolayısıyla o eşlemeyi paylaşmaya gerek yok.
 *
 * Aynı eşleme canlı imleçlerin de kapısı: panoya katılmış (katılırken üyeliği
 * doğrulanmış) oturumun imleci kabul edilir. Saniyede onlarca gelen imleç
 * mesajı için her seferinde veritabanına gidilmez.
 */
@Service
public class PresenceService {

    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);

    private static final String KEY_PREFIX = "presence:board:";

    /**
     * Bir oturumun imleci en fazla bu aralıkla yayınlanır (~30/sn). Tarayıcı zaten
     * 50 ms'de bir gönderiyor; bu sınır kötü niyetli ya da bozuk bir istemcinin
     * Redis'i ve diğer tarayıcıları boğmasına karşı.
     */
    private static final long MIN_CURSOR_INTERVAL_NANOS = 30_000_000L;

    /** Bu sunucuya bağlı ve bir panoya katılmış oturum. */
    private record LocalSession(Long boardId, PresenceUser user, AtomicLong lastCursorAt) {}

    /** sessionId → oturum : SADECE bu sunucuya bağlı oturumlar (yerel kalması doğru). */
    private final Map<String, LocalSession> mySessions = new ConcurrentHashMap<>();

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
        mySessions.put(sessionId, new LocalSession(boardId, user, new AtomicLong()));

        log.info("Presence: '{}' panoya katıldı (boardId={})", user.name(), boardId);
        broadcast(boardId);
    }

    /**
     * Bir oturum kapandı (sekme kapandı / hat koptu) → Redis'ten çıkar, herkese duyur.
     * Bunu WebSocketEventListener, Spring'in "oturum kapandı" olayında çağırır.
     */
    public void leave(String sessionId) {
        LocalSession session = mySessions.remove(sessionId);
        if (session == null) {
            return;   // bu oturum bu sunucuda panoya katılmamıştı
        }
        Long boardId = session.boardId();
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
        mySessions.forEach((sessionId, session) -> redis.opsForHash().delete(key(session.boardId()), sessionId));
        mySessions.clear();
    }

    /**
     * İmleç mesajının sahibi — yalnızca bu panoya katılmış oturum için döner.
     *
     * Çok sık gelen konumlar düşürülür: imleç kaybolmaz, yalnızca ara konumlar
     * atlanır. {@code urgent} (imleç panodan çıktı) her zaman geçer; düşürülseydi
     * karşı tarafta hareketsiz bir imleç asılı kalırdı.
     */
    public Optional<PresenceUser> cursorSender(String sessionId, Long boardId, boolean urgent) {
        LocalSession session = mySessions.get(sessionId);
        if (session == null || !session.boardId().equals(boardId)) {
            return Optional.empty();
        }
        if (!urgent) {
            long now = System.nanoTime();
            long last = session.lastCursorAt().get();
            if (now - last < MIN_CURSOR_INTERVAL_NANOS || !session.lastCursorAt().compareAndSet(last, now)) {
                return Optional.empty();
            }
        }
        return Optional.of(session.user());
    }

    /**
     * Verilen panolarda şu an kimler çevrimiçi — ana ekranın "canlı panolar" ve
     * "ekip" bölümleri için. Yalnızca birinin bulunduğu panolar döner; her panoda
     * kişi başına tek kayıt.
     *
     * Panolar tek tek sorulmaz: okumaların hepsi tek bir Redis turunda (pipeline)
     * gider, pano sayısı kaç olursa olsun bekleme bir gidiş-dönüş kadardır.
     */
    public Map<Long, List<PresenceUser>> onlineByBoard(Collection<Long> boardIds) {
        List<Long> ids = List.copyOf(boardIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Object> replies = redis.executePipelined((RedisCallback<Object>) connection -> {
            ids.forEach(id -> connection.hashCommands().hVals(key(id).getBytes(StandardCharsets.UTF_8)));
            return null;
        });
        Map<Long, List<PresenceUser>> online = new LinkedHashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            List<PresenceUser> users = distinctUsers((Collection<?>) replies.get(i));
            if (!users.isEmpty()) {
                online.put(ids.get(i), users);
            }
        }
        return online;
    }

    /** Redis'teki TAM listeyi oku ve panonun presence kanalına yayınla (tüm sunuculara). */
    private void broadcast(Long boardId) {
        broadcastService.broadcast("/topic/board." + boardId + "/presence",
                PresenceState.of(distinctUsers(redis.opsForHash().values(key(boardId)))));
    }

    /**
     * Kişi bazında TEKİLLEŞTİRME: aynı kullanıcı iki sekme açtıysa Redis'te iki
     * oturum kaydı vardır, ama panoda "iki kişi" göstermek yanlış olur.
     * LinkedHashMap: userId'ye göre tekilleştirirken sırayı korur.
     */
    private List<PresenceUser> distinctUsers(Collection<?> values) {
        Map<Long, PresenceUser> byUser = new LinkedHashMap<>();
        for (Object value : values == null ? List.of() : values) {
            try {
                PresenceUser user = objectMapper.readValue(value.toString(), PresenceUser.class);
                byUser.putIfAbsent(user.userId(), user);
            } catch (Exception e) {
                log.warn("Presence kaydı okunamadı: {}", value, e);
            }
        }
        return new ArrayList<>(byUser.values());
    }

    private String key(Long boardId) {
        return KEY_PREFIX + boardId;
    }
}
