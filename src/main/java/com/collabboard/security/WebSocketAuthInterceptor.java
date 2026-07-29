package com.collabboard.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * WebSocket kimlik doğrulaması (ADR 0005).
 *
 * NEDEN AYRI BİR MEKANİZMA? Tarayıcının WebSocket API'si el sıkışmaya (handshake)
 * özel HTTP başlığı eklemeye izin vermez — yani REST'te kullandığımız
 * "Authorization" başlığını oraya koyamayız. Çözüm: token'ı bir üst katmanda,
 * STOMP'un CONNECT frame'inde taşımak. Burası onu yakalayıp doğruluyor.
 *
 * ChannelInterceptor: istemciden gelen TÜM mesajların geçtiği borudaki süzgeç
 * (REST'teki filter'ın mesajlaşma karşılığı).
 */
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;

    public WebSocketAuthInterceptor(JwtTokenProvider tokenProvider,
                                    CustomUserDetailsService userDetailsService) {
        this.tokenProvider = tokenProvider;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // Sadece CONNECT'i doğruluyoruz: bağlantı bir kez kurulur, kimlik oturuma
        // yazılır ve sonraki tüm mesajlarda (SEND/SUBSCRIBE) elimizde olur.
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String token = extractToken(accessor);
        if (token == null || !tokenProvider.validateToken(token)) {
            log.warn("WebSocket CONNECT reddedildi: geçersiz veya eksik token");
            // Exception fırlatmak bağlantıyı reddeder — kimliksiz kimse bağlanamaz.
            throw new IllegalArgumentException("WebSocket bağlantısı için geçerli bir token gerekli");
        }

        String email = tokenProvider.getUsernameFromToken(token);
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);

        // Kimliği OTURUMA bağla. Artık bu bağlantının her mesajında
        // accessor.getUser() ile "bunu kim gönderdi" sorusunu cevaplayabiliriz.
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities()));

        log.debug("WebSocket CONNECT doğrulandı: {}", email);
        return message;
    }

    /** CONNECT frame'inin "Authorization: Bearer <token>" başlığını oku. */
    private String extractToken(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(AUTH_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
