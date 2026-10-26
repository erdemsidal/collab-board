package com.collabboard.board;

import com.collabboard.audit.ActivityService;
import com.collabboard.board.dto.CardResponse;
import com.collabboard.board.entity.BoardColumn;
import com.collabboard.board.entity.Card;
import com.collabboard.board.operation.AddCardOp;
import com.collabboard.board.operation.CardAddedEvent;
import com.collabboard.board.operation.CardDeletedEvent;
import com.collabboard.board.operation.CardEditedEvent;
import com.collabboard.board.operation.CardMovedEvent;
import com.collabboard.board.operation.DeleteCardOp;
import com.collabboard.board.operation.EditCardOp;
import com.collabboard.board.operation.MoveCardOp;
import com.collabboard.common.exception.ResourceNotFoundException;
import com.collabboard.common.exception.StaleVersionException;
import com.collabboard.common.exception.WipLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Kart operasyonlarının iş mantığı (ADD_CARD, MOVE_CARD, EDIT_CARD, DELETE_CARD).
 * Hepsi YAZMA işlemi → sınıf seviyesinde @Transactional (readOnly değil).
 */
@Service
@Transactional
public class CardService {

    private static final Logger log = LoggerFactory.getLogger(CardService.class);

    private final ColumnRepository columnRepository;
    private final CardRepository cardRepository;
    private final ActivityService activityService;
    private final ApplicationEventPublisher events;

    public CardService(ColumnRepository columnRepository, CardRepository cardRepository,
                       ActivityService activityService, ApplicationEventPublisher events) {
        this.columnRepository = columnRepository;
        this.cardRepository = cardRepository;
        this.activityService = activityService;
        this.events = events;
    }

    /**
     * Bir kolona yeni kart ekle (kolonun sonuna).
     *
     * @param actor işlemi yapan kullanıcının e-postası — geçmişe (audit) yazmak için.
     */
    public CardAddedEvent addCard(AddCardOp op, String actor) {
        BoardColumn column = columnRepository.findById(op.columnId())
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound.column", op.columnId()));

        requireWipCapacity(column, null);

        int position = column.getCards().size();   // sona ekle: mevcut kart sayısı = yeni index
        Card card = Card.builder()
                .title(op.title())
                .position(position)
                .build();
        column.addCard(card);                       // ilişkinin iki ucunu da bağlar

        Card saved = cardRepository.save(card);     // INSERT → id ve version=0 üretilir
        log.info("Kart eklendi: id={}, columnId={}, pos={}", saved.getId(), column.getId(), position);

        CardAddedEvent event = CardAddedEvent.of(column.getId(), CardResponse.fromEntity(saved));
        activityService.record(boardIdOf(column), actor, "ADD_CARD",
                "%s kartını %s kolonuna ekledi".formatted(quoted(saved.getTitle()), column.getName()), event,
                Map.of("card", saved.getTitle(), "column", column.getName()));
        // Kanal bildirimleri (ADR 0023): işlem kaydedilince arka planda dinlenir.
        events.publishEvent(CardActivityEvent.created(boardIdOf(column), actor, saved.getId(), saved.getTitle(),
                column.getId(), column.getName()));
        return event;
    }

    /** Bir kartı başka kolona/pozisyona taşı. */
    public CardMovedEvent moveCard(MoveCardOp op, String actor) {
        Card card = cardRepository.findById(op.cardId())
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound.card", op.cardId()));
        BoardColumn target = columnRepository.findById(op.toColumnId())
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound.column", op.toColumnId()));

        // ÇAKIŞMA KONTROLÜ (ADR 0003): istemcinin gördüğü sürüm hâlâ güncel mi?
        requireFreshVersion(card, op.baseVersion());

        // Sürüm kontrolünden SONRA: bayat bir operasyonu limit gerekçesiyle reddetmek
        // kullanıcıya yanlış sebebi gösterirdi.
        requireWipCapacity(target, card);

        // NOT: diğer kartların pozisyonlarını yeniden düzenlemek (reindex) sonraki iş.
        Long fromColumnId = card.getColumn().getId();
        card.setColumn(target);
        card.setPosition(op.position());

        // saveAndFlush: UPDATE'i HEMEN flush et → @Version şimdi +1 artsın ki
        // aşağıda yayınlayacağımız event GÜNCEL sürümü taşısın (flush zamanlaması).
        Card saved = cardRepository.saveAndFlush(card);
        log.info("Kart taşındı: id={}, toColumnId={}, pos={}, v={}",
                saved.getId(), target.getId(), saved.getPosition(), saved.getVersion());

        CardMovedEvent event = CardMovedEvent.of(saved.getId(), target.getId(),
                saved.getPosition(), saved.getVersion());
        activityService.record(boardIdOf(target), actor, "MOVE_CARD",
                "%s kartını %s kolonuna taşıdı".formatted(quoted(saved.getTitle()), target.getName()), event,
                Map.of("card", saved.getTitle(), "column", target.getName()));
        // "Son kolona girdi mi?" sorusunu dinleyici sorar; taşıyan kişi o sorguyu beklemesin.
        events.publishEvent(CardActivityEvent.moved(boardIdOf(target), actor, saved.getId(), saved.getTitle(),
                fromColumnId, target.getId(), target.getName()));
        return event;
    }

    /** Bir kartın düzenlenebilir alanlarını güncelle (başlık, açıklama, atanan, son tarih). */
    public CardEditedEvent editCard(EditCardOp op, String actor) {
        Card card = cardRepository.findById(op.cardId())
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound.card", op.cardId()));

        // ÇAKIŞMA KONTROLÜ (ADR 0003): başkasının değişikliğini sessizce ezmeyelim.
        requireFreshVersion(card, op.baseVersion());

        String oldTitle = card.getTitle();   // geçmişe "neydi → ne oldu" yazabilmek için
        Long oldAssignee = card.getAssigneeId();
        card.setTitle(op.title());
        card.setDescription(op.description());
        card.setAssigneeId(op.assigneeId());
        card.setDueDate(op.dueDate());

        Card saved = cardRepository.saveAndFlush(card);   // flush → @Version güncel
        log.info("Kart düzenlendi: id={}, v={}", saved.getId(), saved.getVersion());

        CardEditedEvent event = CardEditedEvent.of(saved.getId(), saved.getTitle(),
                saved.getDescription(), saved.getAssigneeId(), saved.getDueDate(), saved.getVersion());
        // Başlık değiştiyse eski başlık da gider: "'Eski' kartını 'Yeni' olarak düzenledi".
        Map<String, String> params = oldTitle.equals(saved.getTitle())
                ? Map.of("card", saved.getTitle())
                : Map.of("card", saved.getTitle(), "from", oldTitle);
        activityService.record(boardIdOf(card.getColumn()), actor, "EDIT_CARD",
                describeEdit(oldTitle, saved), event, params);
        // Yalnızca atanan DEĞİŞTİYSE ve biri atandıysa: başlık düzeltmek kanala "atadı" yazdırmasın.
        if (saved.getAssigneeId() != null && !saved.getAssigneeId().equals(oldAssignee)) {
            BoardColumn column = card.getColumn();
            events.publishEvent(CardActivityEvent.assigned(boardIdOf(column), actor, saved.getId(), saved.getTitle(),
                    column.getId(), column.getName(), saved.getAssigneeId()));
        }
        return event;
    }

    /** Bir kartı sil. */
    public CardDeletedEvent deleteCard(DeleteCardOp op, String actor) {
        // Silmeden ÖNCE kartı yükle: başlığını ve panosunu geçmişe yazacağız
        // (silindikten sonra bu bilgilere ulaşamayız).
        Card card = cardRepository.findById(op.cardId())
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound.card", op.cardId()));
        String title = card.getTitle();
        Long boardId = boardIdOf(card.getColumn());

        cardRepository.delete(card);
        log.info("Kart silindi: id={}", op.cardId());

        CardDeletedEvent event = CardDeletedEvent.of(op.cardId());
        activityService.record(boardId, actor, "DELETE_CARD",
                "%s kartını sildi".formatted(quoted(title)), event, Map.of("card", title));
        return event;
    }

    /**
     * Geçmiş satırının metni. Başlık değiştiyse "şu → bu", değişmediyse detay
     * güncellemesi olduğunu söyler; ikisini ayırmazsak akış kaydı "X kartını X olarak
     * düzenledi" gibi anlamsız satırlarla dolar.
     */
    private String describeEdit(String oldTitle, Card saved) {
        if (!oldTitle.equals(saved.getTitle())) {
            return "%s kartını %s olarak düzenledi"
                    .formatted(quoted(oldTitle), quoted(saved.getTitle()));
        }
        return "%s kartının detaylarını güncelledi".formatted(quoted(saved.getTitle()));
    }

    /** Geçmiş metinlerinde kart başlığını tırnak içine alır. */
    private String quoted(String value) {
        return "'" + value + "'";
    }

    /**
     * Kolonun WIP limiti kartı almaya yetiyor mu? (Kanban çekirdek kuralı.)
     *
     * @param movingCard taşınan kart; yeni kart eklenirken null. Kart ZATEN hedef
     *                   kolondaysa (kolon içi sıralama) kendi yerini işgal ediyor
     *                   sayılmaz — yoksa dolu bir kolonda sıralama imkânsız olurdu.
     */
    private void requireWipCapacity(BoardColumn target, Card movingCard) {
        Integer limit = target.getWipLimit();
        if (limit == null) {
            return;   // sınırsız kolon
        }

        long occupied = cardRepository.countByColumnId(target.getId());
        if (movingCard != null && target.getId().equals(movingCard.getColumn().getId())) {
            occupied--;
        }

        if (occupied >= limit) {
            log.info("Operasyon reddedildi (WIP limiti): columnId={}, dolu={}, limit={}",
                    target.getId(), occupied, limit);
            throw new WipLimitExceededException(target.getId(), target.getName(), limit);
        }
    }

    /**
     * Kolonun panosunun id'si.
     *
     * NEDEN istemcinin gönderdiği boardId'yi kullanmıyoruz? Çünkü geçmiş kaydı
     * güvenilir olmalı: doğru pano, verinin kendisinden (kolon → pano) türetilir.
     * Kolon LAZY yüklenir ama biz işlem (transaction) içindeyiz, sorun olmaz.
     */
    private Long boardIdOf(BoardColumn column) {
        return column.getBoard().getId();
    }

    /**
     * Optimistic çakışma kontrolü (ADR 0003).
     *
     * İstemcinin ekranında gördüğü sürüm (baseVersion), sunucudaki güncel sürümle
     * aynı mı? Değilse arada başkası değiştirmiş demektir → operasyonu REDDET.
     * Exception RuntimeException olduğu için @Transactional işlemi geri alır:
     * DB'ye hiçbir şey yazılmaz.
     *
     * baseVersion null gelirse kontrol atlanır (istemci "umursamıyorum" demiş olur).
     */
    private void requireFreshVersion(Card card, Long baseVersion) {
        if (baseVersion == null) {
            return;
        }
        if (!baseVersion.equals(card.getVersion())) {
            log.info("Operasyon reddedildi (bayat sürüm): cardId={}, gönderilen={}, güncel={}",
                    card.getId(), baseVersion, card.getVersion());
            throw new StaleVersionException(card.getId(), baseVersion, card.getVersion());
        }
    }
}
