package com.collabboard.board;

import com.collabboard.board.dto.CardKeyResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kart anahtarını çözer: paylaşılan /?card=STR-42 bağlantısı ve dış araçlar için.
 * Arayüz, panoyu ve kartı mevcut derin bağlantıyla açar (openBoard + openCard).
 */
@RestController
public class CardKeyController {

    private final CardKeyService cardKeyService;

    public CardKeyController(CardKeyService cardKeyService) {
        this.cardKeyService = cardKeyService;
    }

    /** GET /api/cards/by-key/STR-42 → kart ve panosu; erişim yoksa da 404, biçim bozuksa 400. */
    @GetMapping("/api/cards/by-key/{key}")
    public ResponseEntity<CardKeyResponse> resolve(@PathVariable String key,
                                                   @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(cardKeyService.resolve(key, user.getUsername()));
    }
}
