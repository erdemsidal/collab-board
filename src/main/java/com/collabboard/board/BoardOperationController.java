package com.collabboard.board;

import com.collabboard.board.operation.AddCardOp;
import com.collabboard.board.operation.BoardEvent;
import com.collabboard.board.operation.BoardOperation;
import com.collabboard.board.operation.DeleteCardOp;
import com.collabboard.board.operation.EditCardOp;
import com.collabboard.board.operation.MoveCardOp;
import com.collabboard.board.operation.MoveColumnOp;
import com.collabboard.board.operation.OperationRejectedEvent;
import com.collabboard.common.exception.ForbiddenException;
import com.collabboard.common.exception.ResourceNotFoundException;
import com.collabboard.common.exception.StaleVersionException;
import com.collabboard.observability.RealtimeMetrics;
import com.collabboard.realtime.BroadcastService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Gerçek zamanlı senkronun kalbi — WebSocket/STOMP mesaj controller'ı.
 *
 * İstemci "/app/board/{id}/ops" adresine bir operasyon gönderir; burada uygulanır
 * ve sonucu "/topic/board.{id}" üzerinden panodaki herkese yayınlanır.
 */
@Controller
public class BoardOperationController {

    private final CardService cardService;
    private final ColumnService columnService;
    private final BroadcastService broadcastService;
    private final RealtimeMetrics metrics;
    private final BoardAccessService accessService;

    public BoardOperationController(CardService cardService, ColumnService columnService,
                                    BroadcastService broadcastService, RealtimeMetrics metrics,
                                    BoardAccessService accessService) {
        this.cardService = cardService;
        this.columnService = columnService;
        this.broadcastService = broadcastService;
        this.metrics = metrics;
        this.accessService = accessService;
    }

    @MessageMapping("/board/{boardId}/ops")
    public void handleOperation(@DestinationVariable Long boardId, BoardOperation op,
                                SimpMessageHeaderAccessor headers) {
        // Kimlik CONNECT sırasında oturuma bağlanır (ADR 0005); yetki kontrolü ve
        // geçmiş kaydı için buradan okunur.
        Principal principal = headers.getUser();
        String actor = principal != null ? principal.getName() : "bilinmeyen";

        // Operasyonlar bu kanaldan geldiği için yetki REST'ten bağımsız olarak
        // burada da uygulanmalı.
        accessService.requireEditor(boardId, actor);

        // sealed BoardOperation: yeni bir tip eklendiğinde bu switch güncellenmezse
        // kod derlenmez.
        BoardEvent event = switch (op) {
            case AddCardOp add        -> cardService.addCard(add, actor);
            case MoveCardOp move      -> cardService.moveCard(move, actor);
            case EditCardOp edit      -> cardService.editCard(edit, actor);
            case DeleteCardOp del     -> cardService.deleteCard(del, actor);
            case MoveColumnOp moveCol -> columnService.moveColumn(moveCol, actor);
        };

        // Yayın Redis üzerinden dolaşır (ADR 0004), böylece istemcinin hangi sunucuya
        // bağlı olduğu fark etmez.
        broadcastService.broadcast("/topic/board." + boardId, event);
        metrics.operationApplied(event.type());
    }

    // Reddetmeler yalnızca isteği gönderen oturuma gider; diğer kullanıcılar bu
    // gürültüyü görmez. broadcast = false şart: varsayılan davranış kullanıcıyı
    // isminden çözmeye çalışır ve mesaj teslim edilemeden düşer.

    /** Bayat sürümle gelen operasyon → reddedildi, gönderen resync yapsın. */
    @MessageExceptionHandler(StaleVersionException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public OperationRejectedEvent handleStaleVersion(StaleVersionException ex) {
        metrics.operationRejected("STALE_VERSION");
        return OperationRejectedEvent.staleVersion(ex.getCardId());
    }

    /** Yetkisiz operasyon (üye değil veya VIEWER) → gönderene bildir, yayına sızdırma. */
    @MessageExceptionHandler(ForbiddenException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public OperationRejectedEvent handleForbidden(ForbiddenException ex) {
        metrics.operationRejected("FORBIDDEN");
        return OperationRejectedEvent.forbidden(ex.getMessage());
    }

    /** Olmayan kayda operasyon (ör. kart bu arada silinmiş) → gönderen resync yapsın. */
    @MessageExceptionHandler(ResourceNotFoundException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public OperationRejectedEvent handleNotFound(ResourceNotFoundException ex) {
        metrics.operationRejected("NOT_FOUND");
        return OperationRejectedEvent.notFound(ex.getMessage());
    }
}
