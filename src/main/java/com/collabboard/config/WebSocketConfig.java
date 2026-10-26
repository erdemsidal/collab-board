package com.collabboard.config;

import com.collabboard.security.WebSocketAuthInterceptor;
import com.collabboard.security.StompAccessInterceptor;
import com.collabboard.security.StompErrorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    private final WebSocketAuthInterceptor authInterceptor;
    private final StompAccessInterceptor accessInterceptor;
    private final String[] allowedOrigins;

    public WebSocketConfig(WebSocketAuthInterceptor authInterceptor,
                           StompAccessInterceptor accessInterceptor,
                           @Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
        this.authInterceptor = authInterceptor;
        this.accessInterceptor = accessInterceptor;
        this.allowedOrigins = allowedOrigins;
    }

    /**
     * İstemciden GELEN mesaj borusuna süzgeçleri tak. SIRA ÖNEMLİ:
     *  1) authInterceptor   → CONNECT'teki JWT'yi doğrular, kimliği oturuma bağlar (ADR 0005)
     *  2) accessInterceptor → her çerçeveyi izin listesine göre denetler: abonelikte adres
     *                         ve pano üyeliği, gönderimde yalnızca "/app" uçları (ADR 0025)
     * İkincisi birincinin bağladığı kimliğe dayandığı için sonra gelmeli.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor, accessInterceptor);
    }

    /**
     * İstemcinin bağlandığı WebSocket adresi.
     *
     * ORIGIN DENETİMİ NEDEN ÖNEMLİ? El sıkışma sırasında tarayıcı, kullanıcının
     * çerezlerini ve kimliğini taşıyan sıradan bir HTTP isteği gönderir. Liste
     * herkese açık bırakılırsa, kullanıcının ziyaret ettiği HERHANGİ bir kötü
     * niyetli sayfa arka planda bizim sunucumuza bağlantı açabilir.
     *
     * Kimlik doğrulaması bir sonraki adımda (CONNECT çerçevesi, ADR 0005)
     * yapıldığı için tek başına ele geçirme olmaz; ama origin denetimi ilk
     * savunma hattıdır ve bedeli sıfırdır.
     *
     * Arayüz backend ile aynı adresten sunulduğu için varsayılan liste kısa:
     * yalnızca uygulamanın kendi adresi. Canlıda CORS_ALLOWED_ORIGINS ile
     * gerçek alan adı verilir.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        log.info("WebSocket origin listesi: {}", String.join(", ", allowedOrigins));
        // setAllowedOrigins (setAllowedOriginPatterns değil): joker kabul etmez,
        // yanlışlıkla "*" yazılması sessizce geçmesin.
        registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigins);
        // Reddedilen çerçevenin ERROR'una nedenini ("x-reason") yazar; arayüz buna
        // bakıp yeniden bağlanmakla panodan çıkmak arasında seçim yapar.
        registry.setErrorHandler(new StompErrorHandler());
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Bellek içi broker; sunucular arası dağıtımı Redis köprüsü yapar (ADR 0004).
        // /queue'yu da tanıtmak şart: @SendToUser adresleri arka planda
        // "/queue/errors-user{sessionId}" hâline gelir ve tanıtılmazsa sessizce düşer.
        //
        // DİKKAT: bu broker "*", "**" ya da "{..}" içeren abonelik adresini KALIP
        // sayar. İstemcinin nereye abone olup nereye gönderebileceğini
        // StompAccessInterceptor'daki izin listesi belirler (ADR 0025).
        registry.enableSimpleBroker("/topic", "/queue");

        // İstemci → SUNUCUYA gönderdiği mesajların ön eki.
        // "/app/..." adresine SEND edilen mesajlar bizim @MessageMapping metotlarımıza gider (adım 4).
        registry.setApplicationDestinationPrefixes("/app");
    }
}
