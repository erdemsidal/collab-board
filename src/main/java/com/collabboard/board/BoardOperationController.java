package com.collabboard.board;

import com.collabboard.board.operation.AddCardOp;
import com.collabboard.board.operation.BoardEvent;
import com.collabboard.board.operation.BoardOperation;
import com.collabboard.board.operation.DeleteCardOp;
import com.collabboard.board.operation.EditCardOp;
import com.collabboard.board.operation.MoveCardOp;
import com.collabboard.board.operation.MoveColumnOp;
import com.collabboard.board.operation.OperationRejectedEvent;
import com.collabboard.common.exception.ResourceNotFoundException;
import com.collabboard.common.exception.StaleVersionException;
import com.collabboard.observability.RealtimeMetrics;
import com.collabboard.realtime.BroadcastService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
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
    private final ColumnService columnService;
    private final BroadcastService broadcastService;
    private final RealtimeMetrics metrics;

    public BoardOperationController(CardService cardService, ColumnService columnService,
                                    BroadcastService broadcastService, RealtimeMetrics metrics) {
        this.cardService = cardService;
        this.columnService = columnService;
        this.broadcastService = broadcastService;
        this.metrics = metrics;
    }

    /**
     * @MessageMapping: REST'teki @PostMapping'in STOMP karşılığı.
     *   İstemci SEND /app/board/42/ops  {"type":"MOVE_CARD",...}  → burası çalışır.
     * @DestinationVariable: adresteki {boardId}'yi yakalar (REST'teki @PathVariable gibi).
     * BoardOperation op: mesaj gövdesi (JSON) → Jackson "type"a bakıp doğru alt tipe çevirir.
     */
    @MessageMapping("/board/{boardId}/ops")
    public void handleOperation(@DestinationVariable Long boardId, BoardOperation op) {
        // EXHAUSTIVE SWITCH — sealed BoardOperation'ın TÜM tiplerini ele almak ZORUNLU.
        // EDIT_CARD/DELETE_CARD eklersen, buraya case koymadan kod DERLENMEZ. Güvenlik ağı.
        BoardEvent event = switch (op) {
            case AddCardOp add       -> cardService.addCard(add);
            case MoveCardOp move     -> cardService.moveCard(move);
            case EditCardOp edit     -> cardService.editCard(edit);
            case DeleteCardOp del    -> cardService.deleteCard(del);
            case MoveColumnOp moveCol -> columnService.moveColumn(moveCol);
        };

        // Buraya gelindiyse operasyon kabul edildi (reddedilseydi exception fırlardı
        // ve aşağıdaki @MessageExceptionHandler devreye girerdi).
        //
        // Yayın artık REDIS ÜZERİNDEN (ADR 0004): olay tüm sunuculara gider, her
        // sunucu kendi istemcilerine push eder. Böylece hangi sunucuya bağlı olursa
        // olsun o panodaki herkes görür.
        broadcastService.broadcast("/topic/board." + boardId, event);
        metrics.operationApplied(event.type());
    }

    // ═══════════════════════════════════════════════════════════════════
    // ÇAKIŞMA / HATA YOLU (ADR 0003)
    //
    // @MessageExceptionHandler: yukarıdaki @MessageMapping metodundan bir exception
    // çıkarsa burası çalışır (REST'teki @ExceptionHandler'ın STOMP karşılığı).
    // @SendToUser: dönen değeri /topic'e DEĞİL, sadece operasyonu gönderen kişiye
    // yollar (istemci "/user/queue/errors" adresine abone olur). Diğer kullanıcılar
    // bu reddetme gürültüsünü görmez.
    //
    // broadcast = false ÖNEMLİ: varsayılan (true) "bu KULLANICININ tüm oturumlarına
    // gönder" demektir ve kullanıcıyı isimden bulmayı gerektirir. Faz 1/2'de auth
    // olmadığı için kayıtlı kullanıcı adı yok → mesaj teslim edilemez. false ile
    // Spring mesajı doğrudan İSTEĞİ GÖNDEREN OTURUMA yollar. (Faz 4'te gerçek auth
    // gelince true da çalışır hale gelecek.)
    // ═══════════════════════════════════════════════════════════════════

    /** Bayat sürümle gelen operasyon → reddedildi, gönderen resync yapsın. */
    @MessageExceptionHandler(StaleVersionException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public OperationRejectedEvent handleStaleVersion(StaleVersionException ex) {
        metrics.operationRejected("STALE_VERSION");
        return OperationRejectedEvent.staleVersion(ex.getCardId());
    }

    /** Olmayan kayda operasyon (ör. kart bu arada silinmiş) → gönderen resync yapsın. */
    @MessageExceptionHandler(ResourceNotFoundException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public OperationRejectedEvent handleNotFound(ResourceNotFoundException ex) {
        metrics.operationRejected("NOT_FOUND");
        return OperationRejectedEvent.notFound(ex.getMessage());
    }
}
