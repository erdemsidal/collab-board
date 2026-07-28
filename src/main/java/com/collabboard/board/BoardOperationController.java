package com.collabboard.board;

import com.collabboard.board.operation.AddCardOp;
import com.collabboard.board.operation.BoardEvent;
import com.collabboard.board.operation.CardOperation;
import com.collabboard.board.operation.DeleteCardOp;
import com.collabboard.board.operation.EditCardOp;
import com.collabboard.board.operation.MoveCardOp;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * Gerçek zamanlı senkronun kalbi — WebSocket/STOMP mesaj controller'ı.
 *
 * REST controller'dan farkı: HTTP değil, STOMP mesajlarını dinler.
 * İstemci "/app/board/{id}/ops" adresine bir operasyon SEND eder, buraya düşer.
 * (Hatırla: /app ön eki = "sunucunun koduna"; broker /topic'i yönetiyordu.)
 */
@Controller
public class BoardOperationController {

    private final CardService cardService;
    private final SimpMessagingTemplate messagingTemplate;

    public BoardOperationController(CardService cardService, SimpMessagingTemplate messagingTemplate) {
        this.cardService = cardService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * @MessageMapping: REST'teki @PostMapping'in STOMP karşılığı.
     *   İstemci SEND /app/board/42/ops  {"type":"MOVE_CARD",...}  → burası çalışır.
     * @DestinationVariable: adresteki {boardId}'yi yakalar (REST'teki @PathVariable gibi).
     * CardOperation op: mesaj gövdesi (JSON) → Jackson "type"a bakıp doğru alt tipe çevirir.
     */
    @MessageMapping("/board/{boardId}/ops")
    public void handleOperation(@DestinationVariable Long boardId, CardOperation op) {
        // EXHAUSTIVE SWITCH — sealed CardOperation'ın TÜM tiplerini ele almak ZORUNLU.
        // EDIT_CARD/DELETE_CARD eklersen, buraya case koymadan kod DERLENMEZ. Güvenlik ağı.
        BoardEvent event = switch (op) {
            case AddCardOp add     -> cardService.addCard(add);
            case MoveCardOp move   -> cardService.moveCard(move);
            case EditCardOp edit   -> cardService.editCard(edit);
            case DeleteCardOp del  -> cardService.deleteCard(del);
        };

        // Uygulandı → o panoyu dinleyen HERKESE yayınla (fan-out'u broker yapar).
        messagingTemplate.convertAndSend("/topic/board." + boardId, event);
    }
}
