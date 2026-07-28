package com.collabboard.board;

import com.collabboard.board.dto.CardResponse;
import com.collabboard.board.entity.BoardColumn;
import com.collabboard.board.entity.Card;
import com.collabboard.board.operation.AddCardOp;
import com.collabboard.board.operation.CardAddedEvent;
import com.collabboard.board.operation.CardMovedEvent;
import com.collabboard.board.operation.MoveCardOp;
import com.collabboard.common.exception.ResourceNotFoundException;
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

    public CardService(ColumnRepository columnRepository, CardRepository cardRepository) {
        this.columnRepository = columnRepository;
        this.cardRepository = cardRepository;
    }

    /** Bir kolona yeni kart ekle (kolonun sonuna). */
    public CardAddedEvent addCard(AddCardOp op) {
        BoardColumn column = columnRepository.findById(op.columnId())
                .orElseThrow(() -> new ResourceNotFoundException("Column", "id", op.columnId()));

        int position = column.getCards().size();   // sona ekle: mevcut kart sayısı = yeni index
        Card card = Card.builder()
                .title(op.title())
                .position(position)
                .build();
        column.addCard(card);                       // senin helper'ın: iki yönü de bağlar

        Card saved = cardRepository.save(card);     // INSERT → id ve version=0 üretilir
        log.info("Kart eklendi: id={}, columnId={}, pos={}", saved.getId(), column.getId(), position);

        return CardAddedEvent.of(column.getId(), CardResponse.fromEntity(saved));
    }

    /** Bir kartı başka kolona/pozisyona taşı. */
    public CardMovedEvent moveCard(MoveCardOp op) {
        Card card = cardRepository.findById(op.cardId())
                .orElseThrow(() -> new ResourceNotFoundException("Card", "id", op.cardId()));
        BoardColumn target = columnRepository.findById(op.toColumnId())
                .orElseThrow(() -> new ResourceNotFoundException("Column", "id", op.toColumnId()));

        // FAZ 1: sadece taşı. FAZ 2'de op.baseVersion ile çakışma kontrolü eklenecek.
        // NOT: diğer kartların pozisyonlarını yeniden düzenlemek (reindex) da Faz 2 işi.
        card.setColumn(target);
        card.setPosition(op.position());

        // saveAndFlush: UPDATE'i HEMEN flush et → @Version şimdi +1 artsın ki
        // aşağıda yayınlayacağımız event GÜNCEL sürümü taşısın (flush zamanlaması).
        Card saved = cardRepository.saveAndFlush(card);
        log.info("Kart taşındı: id={}, toColumnId={}, pos={}, v={}",
                saved.getId(), target.getId(), saved.getPosition(), saved.getVersion());

        return CardMovedEvent.of(saved.getId(), target.getId(), saved.getPosition(), saved.getVersion());
    }
}
