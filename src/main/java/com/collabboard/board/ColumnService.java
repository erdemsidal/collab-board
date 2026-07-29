package com.collabboard.board;

import com.collabboard.audit.ActivityService;
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
    private final ActivityService activityService;

    public ColumnService(ColumnRepository columnRepository, ActivityService activityService) {
        this.columnRepository = columnRepository;
        this.activityService = activityService;
    }

    /** Bir kolonu yeni pozisyona taşı (kolon sırasını değiştir). */
    public ColumnMovedEvent moveColumn(MoveColumnOp op, String actor) {
        BoardColumn column = columnRepository.findById(op.columnId())
                .orElseThrow(() -> new ResourceNotFoundException("Column", "id", op.columnId()));

        // NOT: diğer kolonların pozisyonlarını yeniden düzenlemek (reindex) sonraki iş.
        column.setPosition(op.position());
        BoardColumn saved = columnRepository.save(column);
        log.info("Kolon taşındı: id={}, pos={}", saved.getId(), saved.getPosition());

        activityService.record(column.getBoard().getId(), actor, "MOVE_COLUMN",
                "%s kolonunu %d. sıraya taşıdı".formatted(saved.getName(), saved.getPosition() + 1));

        return ColumnMovedEvent.of(saved.getId(), saved.getPosition());
    }
}
