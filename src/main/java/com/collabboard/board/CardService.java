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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kart operasyonlarının iş mantığı (ADD_CARD, MOVE_CARD; sonra EDIT/DELETE).
 * Hepsi YAZMA işlemi → sınıf seviyesinde @Transactional (readOnly değil).
 */
@Service
@Transactional
public class CardService {

    private static final Logger log = LoggerFactory.getLogger(CardService.class);

    private final ColumnRepository columnRepository;
    private final CardRepository cardRepository;
    private final ActivityService activityService;

    public CardService(ColumnRepository columnRepository, CardRepository cardRepository,
                       ActivityService activityService) {
        this.columnRepository = columnRepository;
        this.cardRepository = cardRepository;
        this.activityService = activityService;
    }

    /**
     * Bir kolona yeni kart ekle (kolonun sonuna).
     *
     * @param actor işlemi yapan kullanıcının e-postası — geçmişe (audit) yazmak için.
     */
    public CardAddedEvent addCard(AddCardOp op, String actor) {
        BoardColumn column = columnRepository.findById(op.columnId())
                .orElseThrow(() -> new ResourceNotFoundException("Column", "id", op.columnId()));

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
                "'%s' kartını %s kolonuna ekledi".formatted(saved.getTitle(), column.getName()), event);
        return event;
    }

    /** Bir kartı başka kolona/pozisyona taşı. */
    public CardMovedEvent moveCard(MoveCardOp op, String actor) {
        Card card = cardRepository.findById(op.cardId())
                .orElseThrow(() -> new ResourceNotFoundException("Card", "id", op.cardId()));
        BoardColumn target = columnRepository.findById(op.toColumnId())
                .orElseThrow(() -> new ResourceNotFoundException("Column", "id", op.toColumnId()));

        // ÇAKIŞMA KONTROLÜ (ADR 0003): istemcinin gördüğü sürüm hâlâ güncel mi?
        requireFreshVersion(card, op.baseVersion());

        // NOT: diğer kartların pozisyonlarını yeniden düzenlemek (reindex) sonraki iş.
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
                "'%s' kartını %s kolonuna taşıdı".formatted(saved.getTitle(), target.getName()), event);
        return event;
    }

    /** Bir kartın başlığını değiştir. */
    public CardEditedEvent editCard(EditCardOp op, String actor) {
        Card card = cardRepository.findById(op.cardId())
                .orElseThrow(() -> new ResourceNotFoundException("Card", "id", op.cardId()));

        // ÇAKIŞMA KONTROLÜ (ADR 0003): başlığı sessizce ezmeyelim.
        requireFreshVersion(card, op.baseVersion());

        String oldTitle = card.getTitle();   // geçmişe "neydi → ne oldu" yazabilmek için
        card.setTitle(op.title());
        Card saved = cardRepository.saveAndFlush(card);   // flush → @Version güncel
        log.info("Kart düzenlendi: id={}, v={}", saved.getId(), saved.getVersion());

        CardEditedEvent event = CardEditedEvent.of(saved.getId(), saved.getTitle(), saved.getVersion());
        activityService.record(boardIdOf(card.getColumn()), actor, "EDIT_CARD",
                "'%s' kartını '%s' olarak düzenledi".formatted(oldTitle, saved.getTitle()), event);
        return event;
    }

    /** Bir kartı sil. */
    public CardDeletedEvent deleteCard(DeleteCardOp op, String actor) {
        // Silmeden ÖNCE kartı yükle: başlığını ve panosunu geçmişe yazacağız
        // (silindikten sonra bu bilgilere ulaşamayız).
        Card card = cardRepository.findById(op.cardId())
                .orElseThrow(() -> new ResourceNotFoundException("Card", "id", op.cardId()));
        String title = card.getTitle();
        Long boardId = boardIdOf(card.getColumn());

        cardRepository.delete(card);
        log.info("Kart silindi: id={}", op.cardId());

        CardDeletedEvent event = CardDeletedEvent.of(op.cardId());
        activityService.record(boardId, actor, "DELETE_CARD",
                "'%s' kartını sildi".formatted(title), event);
        return event;
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
