package com.collabboard.security;

import com.collabboard.board.BoardAccessService;
import com.collabboard.common.exception.LocalizedException;
import com.collabboard.i18n.AppLocale;
import com.collabboard.i18n.Messages;
import com.collabboard.realtime.CloseReason;
import com.collabboard.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * Canlı bağlantının kapı görevlisi: istemciden gelen her çerçeve buradan geçer
 * (ADR 0005, ADR 0025).
 *
 * KURALLAR (hepsi "varsayılan olarak reddet"):
 *  - CONNECT/STOMP: kimliği WebSocketAuthInterceptor doğrular, burası karışmaz.
 *  - SUBSCRIBE: yalnızca izin listesindeki adresler (StompDestinations). Pano
 *    adresinde ayrıca panonun üyesi olmak gerekir: yetkisiz biri
 *    "/topic/board.42"ye abone olabilseydi panodaki her değişikliği CANLI
 *    izlerdi; yazamadan okur, yani sessiz bir veri sızıntısı.
 *  - SEND: yalnızca sunucunun kendi uçları ("/app/..."). Broker adreslerine
 *    doğrudan gönderilen mesaj sunucudan geçmeden abonelere dağıtılırdı; üye
 *    olmayan biri panoya sahte yorum ya da sahte "çevrimiçi" listesi basabilirdi.
 *  - UNSUBSCRIBE: yalnızca kimliği belli oturum (kendi aboneliğini kapatır).
 *  - ACK, NACK, BEGIN, COMMIT, ABORT: uygulama kullanmıyor, reddedilir.
 *  - DISCONNECT ve kalp atışları her zaman geçer.
 *
 * Reddedilen çerçeve ERROR olarak döner ve bağlantı kapanır (STOMP kuralı).
 * Meşru arayüz yalnızca listedeki adresleri kullandığı için bundan etkilenmez;
 * tek istisna panodan çıkarılmış birinin yeniden bağlanması, o da "x-reason:
 * board-access" başlığıyla anlaşılır ve arayüz panodan çıkar (CloseReason).
 */
@Component
public class StompAccessInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompAccessInterceptor.class);

    private final BoardAccessService accessService;
    private final UserService userService;
    private final Messages messages;

    public StompAccessInterceptor(BoardAccessService accessService, UserService userService, Messages messages) {
        this.accessService = accessService;
        this.userService = userService;
        this.messages = messages;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        // Kalp atışının (heartbeat) komutu yoktur; geçsin.
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        switch (command) {
            case CONNECT, STOMP, DISCONNECT -> { }
            case SUBSCRIBE -> authorizeSubscribe(accessor);
            case SEND -> authorizeSend(accessor);
            case UNSUBSCRIBE -> requireUser(accessor);
            default -> {
                Principal user = requireUser(accessor);
                log.warn("Canlı bağlantıda kullanılmayan çerçeve reddedildi: {} ({})", command, user.getName());
                throw forbidden(user);
            }
        }
        return message;
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        Principal user = requireUser(accessor);
        String destination = accessor.getDestination();

        Long boardId = StompDestinations.boardOfSubscription(destination);
        if (boardId != null) {
            // Üye değilse abonelik gerçekleşmez. Ret metni kişinin kayıtlı dilinde (ADR 0020).
            try {
                accessService.requireMember(boardId, user.getName());
            } catch (LocalizedException e) {
                throw new StompRejection(CloseReason.BOARD_ACCESS, messages.get(localeOf(user), e));
            }
            log.debug("Abonelik yetkilendirildi: {} → {}", user.getName(), destination);
            return;
        }

        if (StompDestinations.isPersonalQueue(destination)) {
            return;
        }

        log.warn("İzin listesinde olmayan abonelik reddedildi: {} → {}",
                user.getName(), StompDestinations.forLog(destination));
        throw forbidden(user);
    }

    private void authorizeSend(StompHeaderAccessor accessor) {
        Principal user = requireUser(accessor);
        String destination = accessor.getDestination();
        if (StompDestinations.isAppDestination(destination)) {
            return;   // yetkiyi uçların kendisi denetler (requireEditor, requireMember, katılım)
        }
        log.warn("Sunucu ucu olmayan adrese gönderim reddedildi: {} → {}",
                user.getName(), StompDestinations.forLog(destination));
        throw forbidden(user);
    }

    /**
     * CONNECT dışındaki her çerçeve kimliği belli bir oturumdan gelmeli. Normalde
     * kimliksiz bağlantı CONNECT'te reddedilir; bu, CONNECT'i hiç göndermeden
     * doğrudan SUBSCRIBE yollayan istemciye karşı ikinci kilit.
     */
    private Principal requireUser(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user == null) {
            throw new StompRejection(CloseReason.UNAUTHENTICATED,
                    messages.get(AppLocale.DEFAULT, "error.subscribe.unauthenticated"));
        }
        return user;
    }

    private StompRejection forbidden(Principal user) {
        return new StompRejection(CloseReason.FORBIDDEN, messages.get(localeOf(user), "error.ws.destinationForbidden"));
    }

    private java.util.Locale localeOf(Principal user) {
        return userService.localeOf(user.getName());
    }
}
