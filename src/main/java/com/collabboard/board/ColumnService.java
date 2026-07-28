package com.collabboard.board;

import com.collabboard.board.entity.BoardColumn;
import com.collabboard.board.operation.ColumnMovedEvent;
import com.collabboard.board.operation.MoveColumnOp;
import com.collabboard.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kolon operasyonlarının iş mantığı (şimdilik MOVE_COLUMN).
 * CardService'in kolon karşılığı — simetrik.
 */
@Service
@Transactional
public class ColumnService {

    private static final Logger log = LoggerFactory.getLogger(ColumnService.class);

    private final ColumnRepository columnRepository;

    public ColumnService(ColumnRepository columnRepository) {
        this.columnRepository = columnRepository;
    }

    /** Bir kolonu yeni pozisyona taşı (kolon sırasını değiştir). */
    public ColumnMovedEvent moveColumn(MoveColumnOp op) {
        BoardColumn column = columnRepository.findById(op.columnId())
                .orElseThrow(() -> new ResourceNotFoundException("Column", "id", op.columnId()));

        // FAZ 1: sadece pozisyonu ata. Diğer kolonların reindex'i Faz 2 işi.
        column.setPosition(op.position());
        BoardColumn saved = columnRepository.save(column);
        log.info("Kolon taşındı: id={}, pos={}", saved.getId(), saved.getPosition());

        return ColumnMovedEvent.of(saved.getId(), saved.getPosition());
    }
}
