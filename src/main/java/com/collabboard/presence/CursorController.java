package com.collabboard.presence;

import com.collabboard.realtime.BroadcastService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

/**
 * Canlı imleçler: herkes panodaki diğer kişilerin farelerini görür.
 *
 * Operasyonlardan farkı: hiçbir şey kaydedilmez, geçmişe yazılmaz, sürüm
 * kontrolü yoktur. Kaybolan bir imleç mesajının bedeli, bir sonrakinde düzelen
 * birkaç piksellik atlamadır.
 *
 * Yayın Redis köprüsünden geçer (ADR 0004): iki kişi farklı sunuculara bağlı
 * olsa da birbirinin imlecini görür.
 */
@Controller
public class CursorController {

    private final PresenceService presenceService;
    private final BroadcastService broadcastService;

    public CursorController(PresenceService presenceService, BroadcastService broadcastService) {
        this.presenceService = presenceService;
        this.broadcastService = broadcastService;
    }

    /**
     * Yetki: oturum bu panoya katılmış olmalı. Katılma (presence/join) üyeliği
     * zaten doğruluyor; her imleç mesajında veritabanına tekrar sormuyoruz.
     * Katılmamış bir oturumun mesajı sessizce düşer — hata göndermek, saniyede
     * yirmi mesaj atan bir istemciye saniyede yirmi hata döndürmek olurdu.
     */
    @MessageMapping("/board/{boardId}/cursor")
    public void move(@DestinationVariable Long boardId, @Payload CursorMove move,
                     SimpMessageHeaderAccessor headers) {
        if (!move.valid()) {
            return;
        }
        presenceService.cursorSender(headers.getSessionId(), boardId, move.gone())
                .ifPresent(user -> broadcastService.broadcast(
                        "/topic/board." + boardId + "/cursors", CursorEvent.of(user, move)));
    }
}
