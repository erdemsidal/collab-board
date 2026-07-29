package com.collabboard.presence;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * WebSocket yaşam döngüsü olaylarını dinler.
 *
 * BURASI PRESENCE'IN SIRRI: kullanıcının gittiğini nasıl anlıyoruz?
 * WebSocket bağlantısı SÜREKLİ AÇIK olduğu için, kapandığı an Spring bir olay
 * (SessionDisconnectEvent) fırlatır — sekme kapandığında, sayfa yenilendiğinde
 * veya internet koptuğunda. Yani "çıkış yaptı mı?" diye sormamıza gerek yok;
 * varlık/yokluk doğrudan açık bağlantıdan anlaşılır. Polling yok.
 *
 * @EventListener: Spring içinde yayılan olayları yakalamanın yolu.
 */
@Component
public class WebSocketEventListener {

    private final PresenceService presenceService;

    public WebSocketEventListener(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        presenceService.leave(event.getSessionId());
    }
}
