package com.collabboard.presence;

import com.collabboard.board.BoardAccessService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Presence mesaj controller'ı.
 *
 * İstemci panoya bağlanınca "/app/board/{id}/presence/join" adresine bir mesaj
 * yollar; biz onu çevrimiçi listesine ekleyip herkese duyururuz.
 */
@Controller
public class PresenceController {

    private final PresenceService presenceService;
    private final BoardAccessService accessService;

    public PresenceController(PresenceService presenceService, BoardAccessService accessService) {
        this.presenceService = presenceService;
        this.accessService = accessService;
    }

    /**
     * SimpMessageHeaderAccessor: mesajın "zarfı".
     *  - getSessionId() → bu bağlantının kimliği (her sekme ayrı oturum)
     *  - getUser()      → CONNECT sırasında doğruladığımız KULLANICI (ADR 0005);
     *                     adı = e-posta. Gerçek isim buradan bulunur.
     */
    @MessageMapping("/board/{boardId}/presence/join")
    public void join(@DestinationVariable Long boardId, SimpMessageHeaderAccessor headers) {
        Principal principal = headers.getUser();
        if (principal == null) {
            return;   // kimliksiz bağlantı CONNECT'te reddedilir; buraya düşmemeli
        }
        // Üye olmayan panonun presence listesinde görünemez (VIEWER dahil herkes görünebilir).
        accessService.requireMember(boardId, principal.getName());

        presenceService.join(boardId, headers.getSessionId(), principal.getName());
    }
}
