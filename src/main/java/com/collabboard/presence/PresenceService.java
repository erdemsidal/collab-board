package com.collabboard.presence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Kim hangi panoda çevrimiçi? — presence takibi.
 *
 * ⚠️ BELLEKTE tutuluyor (Map'ler bu sunucunun RAM'inde).
 * Tek sunucuda kusursuz çalışır. AMA Faz 3'te ikinci sunucu açtığımızda ÇÖKER:
 * Sunucu 1, Sunucu 2'ye bağlı kişileri bilmediği için yarım liste gösterir.
 * O zaman bu Map'leri Redis'e (ortak hafızaya) taşıyacağız.
 * Bu, yol haritasının bilerek kurduğu köprü: "basit çözüm ölçekte kırılır" dersi.
 *
 * ConcurrentHashMap: birden çok WebSocket oturumu aynı anda ekleme/silme yapabilir;
 * eşzamanlı erişimde bozulmayan (thread-safe) map budur.
 */
@Service
public class PresenceService {

    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);

    /** Geçici kimlikler (auth yok) — sırayla dağıtılır. TODO(faz4): gerçek kullanıcı. */
    private static final List<String> NAMES = List.of(
            "Panda", "Kaplan", "Kartal", "Tilki", "Kunduz", "Baykuş", "Ceylan", "Şahin");
    private static final List<String> COLORS = List.of(
            "#e05b49", "#f5871f", "#35b37e", "#0079bf", "#8777d9", "#00b8d9", "#ff8f73", "#6554c0");

    /** boardId → (sessionId → kişi) : hangi panoda kimler var. */
    private final Map<Long, Map<String, PresenceUser>> boardUsers = new ConcurrentHashMap<>();

    /** sessionId → boardId : bağlantı koptuğunda kişiyi hangi panodan sileceğimizi bilmek için. */
    private final Map<String, Long> sessionBoard = new ConcurrentHashMap<>();

    private final AtomicInteger counter = new AtomicInteger();

    private final SimpMessagingTemplate messagingTemplate;

    public PresenceService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /** Bir oturum panoya katıldı → kimlik ata, listeye ekle, herkese duyur. */
    public void join(Long boardId, String sessionId) {
        int n = counter.getAndIncrement();
        PresenceUser user = new PresenceUser(
                sessionId,
                NAMES.get(n % NAMES.size()),
                COLORS.get(n % COLORS.size()));

        boardUsers.computeIfAbsent(boardId, id -> new ConcurrentHashMap<>()).put(sessionId, user);
        sessionBoard.put(sessionId, boardId);

        log.info("Presence: '{}' panoya katıldı (boardId={}, toplam={})",
                user.name(), boardId, boardUsers.get(boardId).size());
        broadcast(boardId);
    }

    /**
     * Bir oturum kapandı (sekme kapandı / hat koptu) → listeden çıkar, herkese duyur.
     * Bunu WebSocketEventListener, Spring'in "oturum kapandı" olayında çağırır.
     */
    public void leave(String sessionId) {
        Long boardId = sessionBoard.remove(sessionId);
        if (boardId == null) {
            return;   // bu oturum hiç panoya katılmamıştı
        }
        Map<String, PresenceUser> users = boardUsers.get(boardId);
        if (users != null) {
            PresenceUser gone = users.remove(sessionId);
            if (users.isEmpty()) {
                boardUsers.remove(boardId);   // boş panoyu haritada tutmayalım (sızıntı olmasın)
            }
            if (gone != null) {
                log.info("Presence: '{}' panodan ayrıldı (boardId={}, kalan={})",
                        gone.name(), boardId, users.size());
            }
        }
        broadcast(boardId);
    }

    /** Panodaki güncel çevrimiçi listesini o panonun presence kanalına yayınla. */
    private void broadcast(Long boardId) {
        List<PresenceUser> users = new ArrayList<>(
                boardUsers.getOrDefault(boardId, Map.of()).values());
        messagingTemplate.convertAndSend("/topic/board." + boardId + "/presence",
                PresenceState.of(users));
    }
}
