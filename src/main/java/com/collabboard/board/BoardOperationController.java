package com.collabboard.board;

import com.collabboard.board.operation.AddCardOp;
import com.collabboard.board.operation.AddColumnOp;
import com.collabboard.board.operation.BoardEvent;
import com.collabboard.board.operation.BoardOperation;
import com.collabboard.board.operation.DeleteCardOp;
import com.collabboard.board.operation.DeleteColumnOp;
import com.collabboard.board.operation.EditCardOp;
import com.collabboard.board.operation.MoveCardOp;
import com.collabboard.board.operation.MoveColumnOp;
import com.collabboard.board.operation.OperationRejectedEvent;
import com.collabboard.board.operation.RenameColumnOp;
import com.collabboard.board.operation.SetWipLimitOp;
import com.collabboard.common.exception.BadRequestException;
import com.collabboard.common.exception.ForbiddenException;
import com.collabboard.common.exception.LocalizedException;
import com.collabboard.common.exception.ResourceNotFoundException;
import com.collabboard.common.exception.StaleVersionException;
import com.collabboard.common.exception.WipLimitExceededException;
import com.collabboard.i18n.AppLocale;
import com.collabboard.i18n.Messages;
import com.collabboard.observability.RealtimeMetrics;
import com.collabboard.realtime.BroadcastService;
import com.collabboard.user.UserService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Locale;

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
    private final UserService userService;
    private final Messages messages;

    public BoardOperationController(CardService cardService, ColumnService columnService,
                                    BroadcastService broadcastService, RealtimeMetrics metrics,
                                    BoardAccessService accessService, UserService userService,
                                    Messages messages) {
        this.cardService = cardService;
        this.columnService = columnService;
        this.broadcastService = broadcastService;
        this.metrics = metrics;
        this.accessService = accessService;
        this.userService = userService;
        this.messages = messages;
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
            case SetWipLimitOp wip    -> columnService.setWipLimit(wip, actor);
            case AddColumnOp addCol   -> columnService.addColumn(addCol, actor);
            case RenameColumnOp rnCol -> columnService.renameColumn(rnCol, actor);
            case DeleteColumnOp dlCol -> columnService.deleteColumn(dlCol, actor);
        };

        // Yayın Redis üzerinden dolaşır (ADR 0004), böylece istemcinin hangi sunucuya
        // bağlı olduğu fark etmez.
        broadcastService.broadcast("/topic/board." + boardId, event);
        metrics.operationApplied(event.type());
    }

    // Reddetmeler yalnızca isteği gönderen oturuma gider; diğer kullanıcılar bu
    // gürültüyü görmez. broadcast = false şart: varsayılan davranış kullanıcıyı
    // isminden çözmeye çalışır ve mesaj teslim edilemeden düşer.
    //
    // Canlı kanalda Accept-Language yok: mesaj gönderenin kayıtlı dilinde (ADR 0020).

    /** Bayat sürümle gelen operasyon → reddedildi, gönderen resync yapsın. */
    @MessageExceptionHandler(StaleVersionException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public OperationRejectedEvent handleStaleVersion(StaleVersionException ex, Principal principal) {
        metrics.operationRejected("STALE_VERSION");
        return OperationRejectedEvent.staleVersion(ex.getCardId(), text(ex, principal));
    }

    /** Yetkisiz operasyon (üye değil veya VIEWER) → gönderene bildir, yayına sızdırma. */
    @MessageExceptionHandler(ForbiddenException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public OperationRejectedEvent handleForbidden(ForbiddenException ex, Principal principal) {
        metrics.operationRejected("FORBIDDEN");
        return OperationRejectedEvent.forbidden(text(ex, principal));
    }

    /** Olmayan kayda operasyon (ör. kart bu arada silinmiş) → gönderen resync yapsın. */
    @MessageExceptionHandler(ResourceNotFoundException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public OperationRejectedEvent handleNotFound(ResourceNotFoundException ex, Principal principal) {
        metrics.operationRejected("NOT_FOUND");
        return OperationRejectedEvent.notFound(text(ex, principal));
    }

    /**
     * Kolonun WIP limiti dolu → kart kabul edilmedi.
     *
     * Diğer reddetmelerden farklı: bu bir çakışma ya da hata değil, kuralın kasten
     * çalışmasıdır. Gönderene açıklayıcı mesaj gider, resync gerekmez — panonun
     * durumu zaten doğru.
     */
    @MessageExceptionHandler(WipLimitExceededException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public OperationRejectedEvent handleWipLimit(WipLimitExceededException ex, Principal principal) {
        metrics.operationRejected("WIP_LIMIT");
        return OperationRejectedEvent.wipLimit(text(ex, principal));
    }

    /**
     * Geçersiz girdi (boş kolon adı, 1'den küçük WIP limiti).
     *
     * REST tarafında bunu GlobalExceptionHandler 400'e çevirir; WebSocket'te HTTP
     * durum kodu yok, o yüzden burada da ayrıca ele alınması gerekiyor.
     */
    @MessageExceptionHandler(BadRequestException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public OperationRejectedEvent handleBadRequest(BadRequestException ex, Principal principal) {
        metrics.operationRejected("INVALID");
        return OperationRejectedEvent.invalid(text(ex, principal));
    }

    private String text(LocalizedException ex, Principal principal) {
        Locale locale = principal == null ? AppLocale.DEFAULT : userService.localeOf(principal.getName());
        return messages.get(locale, ex);
    }
}
