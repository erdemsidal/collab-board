package com.collabboard.security;

import com.collabboard.i18n.AppLocale;
import com.collabboard.i18n.Messages;
import com.collabboard.realtime.CloseReason;
import com.collabboard.realtime.RealtimeSessions;
import io.jsonwebtoken.Claims;
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

import java.util.Locale;
import java.util.Map;

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
    private static final String LANGUAGE_HEADER = "accept-language";

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final Messages messages;
    private final AccessTokenCutoff cutoff;

    public WebSocketAuthInterceptor(JwtTokenProvider tokenProvider,
                                    CustomUserDetailsService userDetailsService,
                                    Messages messages,
                                    AccessTokenCutoff cutoff) {
        this.tokenProvider = tokenProvider;
        this.userDetailsService = userDetailsService;
        this.messages = messages;
        this.cutoff = cutoff;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // Sadece bağlanmayı doğruluyoruz: bağlantı bir kez kurulur, kimlik oturuma
        // yazılır ve sonraki tüm mesajlarda (SEND/SUBSCRIBE) elimizde olur.
        //
        // STOMP 1.2'de bağlanmanın İKİ adı var: CONNECT ve STOMP. Spring ikisini de
        // bağlantı sayar. Yalnızca CONNECT denetlenseydi, "STOMP" çerçevesiyle
        // bağlanan biri jetonsuz içeri girerdi (ADR 0025).
        if (accessor == null || !isConnect(accessor.getCommand())) {
            return message;
        }

        Claims claims = tokenProvider.parseValid(extractToken(accessor)).orElse(null);
        // Şifre değiştiyse ya da sıfırlandıysa ondan önce verilmiş jetonla yeniden
        // bağlanılamaz; aksi hâlde koparılan bağlantı 15 dakika boyunca geri gelirdi.
        if (claims == null || cutoff.isCutOff(claims.getSubject(), claims.getIssuedAt())) {
            log.warn("WebSocket CONNECT reddedildi: geçersiz, eksik ya da iptal edilmiş token");
            // Exception fırlatmak bağlantıyı reddeder — kimliksiz kimse bağlanamaz.
            // Kişi henüz belli değil; dil, istemcinin CONNECT'e koyduğu başlıktan (ADR 0020).
            Locale locale = AppLocale.fromHeader(accessor.getFirstNativeHeader(LANGUAGE_HEADER));
            throw new StompRejection(CloseReason.UNAUTHENTICATED, messages.get(locale, "error.ws.tokenRequired"));
        }

        String email = claims.getSubject();
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);

        // Kimliği OTURUMA bağla. Artık bu bağlantının her mesajında
        // accessor.getUser() ile "bunu kim gönderdi" sorusunu cevaplayabiliriz.
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities()));

        // Jetonun bitiş anı oturuma yazılır: süresi geçen ve yenilenmeyen bağlantı
        // kapatılır (RealtimeSessions).
        Map<String, Object> attributes = accessor.getSessionAttributes();
        if (attributes != null) {
            attributes.put(RealtimeSessions.TOKEN_EXPIRY_ATTRIBUTE, claims.getExpiration().toInstant());
        }

        log.debug("WebSocket CONNECT doğrulandı: {}", email);
        return message;
    }

    private static boolean isConnect(StompCommand command) {
        return StompCommand.CONNECT.equals(command) || StompCommand.STOMP.equals(command);
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
