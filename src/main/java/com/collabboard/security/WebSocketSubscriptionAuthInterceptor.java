package com.collabboard.security;

import com.collabboard.board.BoardAccessService;
import com.collabboard.common.exception.ForbiddenException;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Abonelik yetkilendirmesi: bir panonun yayınını yalnızca ÜYELERİ dinleyebilir.
 *
 * NEDEN AYRI BİR KONTROL GEREKİYOR?
 * Operasyon göndermeyi engellemek (requireEditor) yazma tarafını korur; ama
 * yetkisiz biri "/topic/board.42"ye abone olabilseydi panodaki her değişikliği
 * CANLI İZLERDİ — yazamadan okur, yani sessiz bir veri sızıntısı. Okuma tarafı
 * da kapatılmalı.
 *
 * Kimlik CONNECT sırasında doğrulanmıştı (ADR 0005); burada o kimliğin bu panoya
 * üye olup olmadığına bakıyoruz.
 */
@Component
public class WebSocketSubscriptionAuthInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSubscriptionAuthInterceptor.class);

    /** "/topic/board.42" ve "/topic/board.42/presence" adreslerinden pano id'sini yakalar. */
    private static final Pattern BOARD_DESTINATION = Pattern.compile("^/topic/board\\.(\\d+)(/.*)?$");

    private final BoardAccessService accessService;

    public WebSocketSubscriptionAuthInterceptor(BoardAccessService accessService) {
        this.accessService = accessService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return message;
        }

        String destination = accessor.getDestination();
        if (destination == null) {
            return message;
        }

        Matcher matcher = BOARD_DESTINATION.matcher(destination);
        if (!matcher.matches()) {
            // Pano kanalı değil (ör. "/user/queue/errors" — zaten kişiye özel). Serbest.
            return message;
        }

        Long boardId = Long.valueOf(matcher.group(1));
        Principal user = accessor.getUser();
        if (user == null) {
            throw new ForbiddenException("Kimlik doğrulanmadan abone olunamaz");
        }

        // Üye değilse ForbiddenException fırlar → abonelik gerçekleşmez.
        accessService.requireMember(boardId, user.getName());
        log.debug("Abonelik yetkilendirildi: {} → {}", user.getName(), destination);

        return message;
    }
}
