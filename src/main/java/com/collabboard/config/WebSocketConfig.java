package com.collabboard.config;

import com.collabboard.security.WebSocketAuthInterceptor;
import com.collabboard.security.WebSocketSubscriptionAuthInterceptor;
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
    private final WebSocketSubscriptionAuthInterceptor subscriptionAuthInterceptor;

    public WebSocketConfig(WebSocketAuthInterceptor authInterceptor,
                           WebSocketSubscriptionAuthInterceptor subscriptionAuthInterceptor) {
        this.authInterceptor = authInterceptor;
        this.subscriptionAuthInterceptor = subscriptionAuthInterceptor;
    }

    /**
     * İstemciden GELEN mesaj borusuna süzgeçleri tak. SIRA ÖNEMLİ:
     *  1) authInterceptor          → CONNECT'teki JWT'yi doğrular, kimliği oturuma bağlar (ADR 0005)
     *  2) subscriptionAuthInterceptor → SUBSCRIBE'da o kimliğin panoya üye olup olmadığına bakar
     * İkincisi birincinin bağladığı kimliğe dayandığı için sonra gelmeli.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor, subscriptionAuthInterceptor);
    }

    /** İstemcinin bağlandığı WebSocket adresi. */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                // TODO(prod): origin listesi daraltılmalı.
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Bellek içi broker; sunucular arası dağıtımı Redis köprüsü yapar (ADR 0004).
        // /queue'yu da tanıtmak şart: @SendToUser adresleri arka planda
        // "/queue/errors-user{sessionId}" hâline gelir ve tanıtılmazsa sessizce düşer.
        registry.enableSimpleBroker("/topic", "/queue");

        // İstemci → SUNUCUYA gönderdiği mesajların ön eki.
        // "/app/..." adresine SEND edilen mesajlar bizim @MessageMapping metotlarımıza gider (adım 4).
        registry.setApplicationDestinationPrefixes("/app");
    }
}
