package com.collabboard.observability;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Açık WebSocket bağlantısı sayısını Spring'in oturum olaylarından takip eder.
 *
 * Presence'taki WebSocketEventListener ile aynı olayları dinliyoruz ama ayrı bir
 * sınıfta: presence bir ÜRÜN özelliği, metrik ise İZLEME. İki farklı sorumluluk,
 * iki farklı sınıf (biri değişince diğeri etkilenmesin).
 *
 * SessionConnectedEvent: STOMP CONNECT başarıyla tamamlandığında (yani kimlik
 * doğrulandıktan sonra) tetiklenir — reddedilen bağlantılar sayılmaz.
 */
@Component
public class WebSocketMetricsListener {

    private final RealtimeMetrics metrics;

    public WebSocketMetricsListener(RealtimeMetrics metrics) {
        this.metrics = metrics;
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        metrics.connectionOpened();
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        metrics.connectionClosed();
    }
}
