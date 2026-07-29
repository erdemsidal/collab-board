package com.collabboard.config;

import com.collabboard.security.WebSocketAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket + STOMP altyapısı. Gerçek zamanlı senkronun temeli (ADR 0002).
 *
 * @EnableWebSocketMessageBroker: STOMP-over-WebSocket desteğini açar. Bu anotasyon
 * sayesinde Spring bize hazır broker + @MessageMapping yönlendirmesi verir.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor authInterceptor;

    public WebSocketConfig(WebSocketAuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    /**
     * İstemciden GELEN mesaj borusuna kimlik doğrulama süzgecini tak (ADR 0005).
     * CONNECT frame'indeki JWT burada doğrulanır; kimliksiz bağlantı reddedilir.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }

    /**
     * WebSocket EL SIKIŞMASININ (handshake / HTTP 101 upgrade) yapılacağı kapı.
     * İstemci buraya bağlanır: ws://localhost:8080/ws  → "telefonu açık bırakma" noktası.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                // Faz 1: iki tarayıcı sekmesi / localhost farklı origin'lerden bağlanabilsin.
                // (Prod'da bunu daraltırız.)
                .setAllowedOriginPatterns("*");
    }

    /**
     * BROKER (kanal santralı) ve adres ön eklerinin ayarı.
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Bu ön eklerle başlayan adresleri YERLEŞİK broker yönetir: abonelik defterini
        // o tutar, bir yayın gelince doğru abonelere o dağıtır (fan-out).
        // Bu, tek sunuculuk bellek-içi broker — Faz 3'te Redis relay ile değişecek,
        // ama @MessageMapping/convertAndSend kodumuz aynı kalacak.
        //
        //   /topic → herkese açık yayın (pano olayları)
        //   /queue → KİŞİYE ÖZEL mesajlar. @SendToUser("/queue/errors") adresi arka planda
        //            "/queue/errors-user{sessionId}" hâline gelir; bu ön eki broker'a
        //            tanıtmazsak mesaj sessizce düşer (ADR 0003'teki reddetme bildirimi).
        registry.enableSimpleBroker("/topic", "/queue");

        // İstemci → SUNUCUYA gönderdiği mesajların ön eki.
        // "/app/..." adresine SEND edilen mesajlar bizim @MessageMapping metotlarımıza gider (adım 4).
        registry.setApplicationDestinationPrefixes("/app");
    }
}
