package com.collabboard.realtime;

import com.collabboard.i18n.Messages;
import com.collabboard.user.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Açık canlı bağlantıların kaydı ve sunucu tarafından kapatılmaları (ADR 0025,
 * güvenlik incelemesi O4).
 *
 * SORUN: Kimlik yalnızca CONNECT'te, pano üyeliği yalnızca SUBSCRIBE'da
 * denetleniyordu. Açık bağlantı sonra hiç sorgulanmıyordu: panodan çıkarılan,
 * şifresi değiştirilen ya da jetonunun süresi dolan kişi, sekmesini (ya da bir
 * betiği) açık tuttuğu sürece yayını almaya devam ediyordu.
 *
 * ÇÖZÜM, üç tetik:
 *  1. Panodan çıkarılınca: kişinin O PANOYA abone olan bağlantıları kapanır
 *     ("x-reason: board-access"; arayüz panodan çıkar, yeniden bağlanmaz).
 *  2. Şifre değişince ya da sıfırlanınca: kişinin BÜTÜN bağlantıları kapanır
 *     ("session-revoked"). Eski jetonla yeniden bağlanmayı AccessTokenCutoff engeller.
 *  3. Jetonun süresi dolunca: CONNECT'teki jetonun bitiş anı (artı kısa bir pay)
 *     geçmiş ve istemci yeni jetonunu bildirmemişse bağlantı kapanır
 *     ("token-expired"). Arayüz jetonunu her yenilediğinde bunu canlı bağlantıya
 *     da bildirir (/app/session/renew); meşru bir kullanıcı bu yüzden hiç kopmaz.
 *
 * KAPATMA NASIL? İstemciye bir STOMP ERROR çerçevesi gönderilir; Spring ERROR'dan
 * sonra WebSocket'i kendisi kapatır. Çerçeve, istemcinin giden mesaj borusundan
 * (clientOutboundChannel) oturum kimliğiyle yollanır; arayüz nedeni "x-reason"
 * başlığından okur.
 *
 * BİRDEN ÇOK SUNUCU: Kişinin bağlantısı başka bir sunucuda olabilir. 1 ve 2,
 * yayınlar gibi Redis'ten geçer (ADR 0004): her sunucu mesajı alır ve kendi
 * bağlantılarını kapatır. 3 her sunucuda yereldir.
 */
@Component
public class RealtimeSessions implements MessageListener {

    /** CONNECT'te jetonun bitiş anının yazıldığı oturum özelliği (WebSocketAuthInterceptor). */
    public static final String TOKEN_EXPIRY_ATTRIBUTE = "sturnio.tokenExpiresAt";

    /** Sunucular arası "şu bağlantıları kapat" kanalı. */
    public static final String CHANNEL = "collabboard:session-control";

    private static final Logger log = LoggerFactory.getLogger(RealtimeSessions.class);

    /** Redis'te dolaşan kapatma isteği. boardId boşsa kişinin bütün bağlantıları. */
    record CloseRequest(String user, Long boardId) {
    }

    /** Bu sunucudaki bir bağlantı: kimin, jetonu ne zaman bitiyor. */
    private static final class Tracked {
        final String user;
        volatile Instant expiresAt;

        Tracked(String user, Instant expiresAt) {
            this.user = user;
            this.expiresAt = expiresAt;
        }
    }

    private final Map<String, Tracked> sessions = new ConcurrentHashMap<>();

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final SimpUserRegistry userRegistry;
    private final MessageChannel clientOutboundChannel;
    private final UserService userService;
    private final Messages messages;
    private final Duration expiryGrace;

    /**
     * @param expiryGrace jetonun bitişinden sonra tanınan pay. Arayüz jetonu
     *        ömrünün %80'inde yeniler; pay, arka plandaki sekmede yavaşlayan
     *        zamanlayıcılar ve yoldaki mesaj için.
     */
    public RealtimeSessions(StringRedisTemplate redis, ObjectMapper objectMapper,
                            @Lazy SimpUserRegistry userRegistry,
                            @Lazy @Qualifier("clientOutboundChannel") MessageChannel clientOutboundChannel,
                            UserService userService, Messages messages,
                            @Value("${app.realtime.token-expiry-grace:60s}") Duration expiryGrace) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.userRegistry = userRegistry;
        this.clientOutboundChannel = clientOutboundChannel;
        this.userService = userService;
        this.messages = messages;
        this.expiryGrace = expiryGrace;
    }

    // ── Kayıt ──────────────────────────────────────────────────────────

    @EventListener
    public void onConnect(SessionConnectEvent event) {
        if (event.getUser() == null) {
            return;
        }
        Map<String, Object> attributes = SimpMessageHeaderAccessor.getSessionAttributes(event.getMessage().getHeaders());
        Instant expiresAt = attributes == null ? null : (Instant) attributes.get(TOKEN_EXPIRY_ATTRIBUTE);
        String sessionId = SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders());
        if (sessionId != null) {
            sessions.put(sessionId, new Tracked(event.getUser().getName(), expiresAt));
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        sessions.remove(event.getSessionId());
    }

    /**
     * Arayüz jetonunu yeniledi: bağlantının yeni bitiş anı. Yalnızca aynı kişinin
     * jetonu kabul edilir (SessionRenewalController denetler).
     */
    public boolean renew(String sessionId, String user, Instant expiresAt) {
        Tracked tracked = sessions.get(sessionId);
        if (tracked == null || !tracked.user.equals(user)) {
            return false;
        }
        if (tracked.expiresAt == null || expiresAt.isAfter(tracked.expiresAt)) {
            tracked.expiresAt = expiresAt;
        }
        return true;
    }

    // ── Kapatma istekleri (bütün sunuculara) ───────────────────────────

    /** Kişinin bütün canlı bağlantıları: şifre değişti ya da sıfırlandı. */
    public void closeAllFor(String user) {
        publish(new CloseRequest(user, null));
    }

    /**
     * Kişinin bu panoya abone olan bağlantıları: panodan çıkarıldı. Çağıran,
     * kişinin panoya başka bir yoldan (çalışma alanı) erişimi kalmadığından emin olmalı.
     */
    public void closeBoardAccess(String user, long boardId) {
        publish(new CloseRequest(user, boardId));
    }

    private void publish(CloseRequest request) {
        try {
            redis.convertAndSend(CHANNEL, objectMapper.writeValueAsString(request));
        } catch (Exception e) {
            // Redis yoksa en azından bu sunucudakiler kapansın.
            log.error("Kapatma isteği Redis'e yayınlanamadı; yalnızca bu sunucuda uygulanıyor", e);
            apply(request);
        }
    }

    /** Redis'ten gelen kapatma isteği (her sunucuda). */
    @Override
    public void onMessage(org.springframework.data.redis.connection.Message message, byte[] pattern) {
        try {
            apply(objectMapper.readValue(message.getBody(), CloseRequest.class));
        } catch (Exception e) {
            log.error("Kapatma isteği işlenemedi", e);
        }
    }

    private void apply(CloseRequest request) {
        if (request.user() == null) {
            return;
        }
        if (request.boardId() == null) {
            sessions.entrySet().stream()
                    .filter(e -> e.getValue().user.equals(request.user()))
                    .map(Map.Entry::getKey)
                    .toList()
                    .forEach(id -> close(id, request.user(), CloseReason.SESSION_REVOKED, "error.ws.sessionRevoked"));
            return;
        }
        SimpUser user = userRegistry.getUser(request.user());
        if (user == null) {
            return;
        }
        List<String> subscribed = user.getSessions().stream()
                .filter(s -> subscribesToBoard(s, request.boardId()))
                .map(SimpSession::getId)
                .toList();
        subscribed.forEach(id -> close(id, request.user(), CloseReason.BOARD_ACCESS, "error.ws.boardAccessRevoked"));
    }

    private static boolean subscribesToBoard(SimpSession session, long boardId) {
        String topic = "/topic/board." + boardId;
        return session.getSubscriptions().stream()
                .map(sub -> sub.getDestination())
                .anyMatch(d -> d.equals(topic) || d.startsWith(topic + "/"));
    }

    // ── Süresi dolan jetonlar (her sunucu kendi bağlantıları için) ─────

    @Scheduled(fixedDelayString = "${app.realtime.session-check-ms:30000}",
            initialDelayString = "${app.realtime.session-check-ms:30000}")
    public void closeExpired() {
        Instant limit = Instant.now().minus(expiryGrace);
        sessions.entrySet().stream()
                .filter(e -> e.getValue().expiresAt != null && e.getValue().expiresAt.isBefore(limit))
                .toList()
                .forEach(e -> close(e.getKey(), e.getValue().user, CloseReason.TOKEN_EXPIRED, "error.ws.tokenExpired"));
    }

    // ── Kapatmanın kendisi ─────────────────────────────────────────────

    /**
     * Bağlantıya ERROR gönder; Spring ardından WebSocket'i kapatır. Kayıttan hemen
     * düşülür ki zamanlayıcı aynı bağlantıyı ikinci kez kapatmaya çalışmasın.
     */
    private void close(String sessionId, String user, CloseReason reason, String messageKey) {
        sessions.remove(sessionId);
        Locale locale = userService.localeOf(user);

        StompHeaderAccessor error = StompHeaderAccessor.create(StompCommand.ERROR);
        error.setMessage(messages.get(locale, messageKey));
        error.setNativeHeader(CloseReason.HEADER, reason.code());
        error.setSessionId(sessionId);
        error.setLeaveMutable(true);
        clientOutboundChannel.send(MessageBuilder.createMessage(new byte[0], error.getMessageHeaders()));

        log.info("Canlı bağlantı sunucu tarafından kapatıldı — oturum: {}, neden: {}", sessionId, reason.code());
    }
}
