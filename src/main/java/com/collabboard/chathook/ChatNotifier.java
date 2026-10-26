package com.collabboard.chathook;

import com.collabboard.board.BoardRepository;
import com.collabboard.board.CardActivityEvent;
import com.collabboard.board.ColumnRepository;
import com.collabboard.board.entity.Board;
import com.collabboard.board.entity.BoardColumn;
import com.collabboard.user.UserRepository;
import com.collabboard.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Optional;

/**
 * Panoda olan biteni bağlı kanallara duyurur (ADR 0023).
 *
 * İKİ KURAL, ikisi de kullanıcıyı korumak için:
 *
 * 1. İŞLEM KAYDEDİLDİKTEN SONRA (AFTER_COMMIT). Reddedilen ya da geri alınan bir
 *    taşıma kanala "taşıdı" diye yazılmaz; olmamış bir şeyi duyurmayız.
 *
 * 2. ARKA PLANDA (@Async). Kartı taşıyan kişi Slack'in cevabını beklemez; kanal
 *    yavaşsa, düşmüşse ya da hata dönerse kişinin işlemi bundan etkilenmez.
 *    Buradaki hiçbir hata yukarı çıkmaz, yalnızca loglanır.
 *
 * Panonun bağlı kanalı yoksa ilk sorgudan sonra döner; ad çözmek gibi işler
 * yalnızca gerçekten gidecek bir mesaj varsa yapılır.
 */
@Component
public class ChatNotifier {

    private static final Logger log = LoggerFactory.getLogger(ChatNotifier.class);

    private final ChatHookRepository repository;
    private final WebhookUrlCipher cipher;
    private final ChatDelivery delivery;
    private final ChatMessages chatMessages;
    private final BoardRepository boardRepository;
    private final ColumnRepository columnRepository;
    private final UserRepository userRepository;

    public ChatNotifier(ChatHookRepository repository, WebhookUrlCipher cipher, ChatDelivery delivery,
                        ChatMessages chatMessages, BoardRepository boardRepository,
                        ColumnRepository columnRepository, UserRepository userRepository) {
        this.repository = repository;
        this.cipher = cipher;
        this.delivery = delivery;
        this.chatMessages = chatMessages;
        this.boardRepository = boardRepository;
        this.columnRepository = columnRepository;
        this.userRepository = userRepository;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCardActivity(CardActivityEvent event) {
        try {
            announce(event);
        } catch (Exception e) {
            log.warn("Kanal bildirimi gönderilemedi: boardId={}, olay={}", event.boardId(), event.kind(), e);
        }
    }

    void announce(CardActivityEvent event) {
        if (!cipher.isAvailable()) {
            return;
        }
        ChatEvent kind = switch (event.kind()) {
            case CREATED -> ChatEvent.CARD_CREATED;
            case MOVED -> ChatEvent.CARD_DONE;
            case ASSIGNED -> ChatEvent.CARD_ASSIGNED;
            case COMMENTED -> ChatEvent.COMMENT_ADDED;
        };
        List<ChatHook> hooks = repository.findByBoardIdAndDisabledReasonIsNull(event.boardId()).stream()
                .filter(h -> h.listensTo(kind))
                .toList();
        if (hooks.isEmpty()) {
            return;
        }
        if (kind == ChatEvent.CARD_DONE && !enteredLastColumn(event)) {
            return;
        }

        Optional<User> actor = userRepository.findByEmail(event.actorEmail());
        String actorName = actor.map(ChatNotifier::fullName).orElse(event.actorEmail());
        String boardName = boardRepository.findById(event.boardId()).map(Board::getName).orElse("");
        String key = "chat.event." + kind.name();
        String assigneeName = null;
        if (kind == ChatEvent.CARD_ASSIGNED) {
            boolean self = actor.map(u -> u.getId().equals(event.assigneeId())).orElse(false);
            if (self) {
                key = "chat.event.CARD_TAKEN";   // kişi kartı kendisi üstlendi: "X kartını üstlendi"
            } else {
                assigneeName = userRepository.findById(event.assigneeId()).map(ChatNotifier::fullName).orElse(null);
                if (assigneeName == null) {
                    return;   // atanan hesap bu arada silinmiş: söylenecek bir şey kalmadı
                }
            }
        }

        ChatMessages.Parts parts = new ChatMessages.Parts(actorName, event.cardTitle(), event.columnName(),
                boardName, assigneeName);
        for (ChatHook hook : hooks) {
            String json = chatMessages.event(hook.getProvider(), hook.getLocale(), key, parts,
                    event.boardId(), event.cardId());
            delivery.deliver(hook, json);
        }
    }

    /**
     * Kart panonun son kolonuna şimdi mi girdi? Son kolon içinde sıralama değil,
     * başka kolondan geliş. Tek kolonlu panoda son kolon "bitti" değildir (ana
     * ekran ve akış ölçümüyle aynı kural, CardRepository.findOpenAssigned).
     */
    private boolean enteredLastColumn(CardActivityEvent event) {
        if (event.columnId() == null || event.columnId().equals(event.fromColumnId())) {
            return false;
        }
        List<BoardColumn> columns = columnRepository.findByBoardIdInOrderByPositionAsc(List.of(event.boardId()));
        return columns.size() > 1 && columns.get(columns.size() - 1).getId().equals(event.columnId());
    }

    private static String fullName(User user) {
        return (user.getFirstName() + " " + user.getLastName()).trim();
    }
}
