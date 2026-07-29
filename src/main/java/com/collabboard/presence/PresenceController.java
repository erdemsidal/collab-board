package com.collabboard.presence;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

/**
 * Presence mesaj controller'ı.
 *
 * İstemci panoya bağlanınca "/app/board/{id}/presence/join" adresine bir mesaj
 * yollar; biz onu çevrimiçi listesine ekleyip herkese duyururuz.
 */
@Controller
public class PresenceController {

    private final PresenceService presenceService;

    public PresenceController(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    /**
     * SimpMessageHeaderAccessor: mesajın "zarfı" — içinden oturum kimliğini (sessionId)
     * okuyoruz. Bu kimlik her açık WebSocket bağlantısına Spring tarafından verilir;
     * kişiyi bununla takip ediyoruz (auth olmadığı için elimizdeki tek kimlik bu).
     */
    @MessageMapping("/board/{boardId}/presence/join")
    public void join(@DestinationVariable Long boardId, SimpMessageHeaderAccessor headers) {
        presenceService.join(boardId, headers.getSessionId());
    }
}
