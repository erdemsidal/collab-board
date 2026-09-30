package com.collabboard.board;

import com.collabboard.audit.ActivityService;
import com.collabboard.board.dto.ColumnResponse;
import com.collabboard.board.entity.Board;
import com.collabboard.board.entity.BoardColumn;
import com.collabboard.board.operation.AddColumnOp;
import com.collabboard.board.operation.ColumnAddedEvent;
import com.collabboard.board.operation.ColumnDeletedEvent;
import com.collabboard.board.operation.ColumnMovedEvent;
import com.collabboard.board.operation.ColumnRenamedEvent;
import com.collabboard.board.operation.DeleteColumnOp;
import com.collabboard.board.operation.MoveColumnOp;
import com.collabboard.board.operation.RenameColumnOp;
import com.collabboard.board.operation.SetWipLimitOp;
import com.collabboard.board.operation.WipLimitChangedEvent;
import com.collabboard.common.exception.BadRequestException;
import com.collabboard.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Kolon operasyonlarının iş mantığı: ekleme, adlandırma, silme, taşıma, WIP limiti.
 * CardService'in kolon karşılığı — simetrik.
 */
@Service
@Transactional
public class ColumnService {

    /** Kolon adı için üst sınır; board_columns.name VARCHAR(100). */
    private static final int MAX_NAME_LENGTH = 100;

    private static final Logger log = LoggerFactory.getLogger(ColumnService.class);

    private final BoardRepository boardRepository;
    private final ColumnRepository columnRepository;
    private final ActivityService activityService;

    public ColumnService(BoardRepository boardRepository, ColumnRepository columnRepository,
                         ActivityService activityService) {
        this.boardRepository = boardRepository;
        this.columnRepository = columnRepository;
        this.activityService = activityService;
    }

    /** Panoya yeni kolon ekle (en sona). */
    public ColumnAddedEvent addColumn(AddColumnOp op, String actor) {
        Board board = boardRepository.findById(op.boardId())
                .orElseThrow(() -> new ResourceNotFoundException("Board", "id", op.boardId()));

        String name = requireName(op.name());

        BoardColumn column = BoardColumn.builder()
                .name(name)
                .position(board.getColumns().size())   // sona ekle
                .build();
        board.addColumn(column);                        // ilişkinin iki ucunu da bağlar

        BoardColumn saved = columnRepository.save(column);
        log.info("Kolon eklendi: id={}, boardId={}, pos={}", saved.getId(), board.getId(), saved.getPosition());

        ColumnAddedEvent event = ColumnAddedEvent.of(ColumnResponse.fromEntity(saved));
        activityService.record(board.getId(), actor, "ADD_COLUMN",
                "%s kolonunu ekledi".formatted(saved.getName()), event);
        return event;
    }

    /** Bir kolonun adını değiştir. */
    public ColumnRenamedEvent renameColumn(RenameColumnOp op, String actor) {
        BoardColumn column = columnRepository.findById(op.columnId())
                .orElseThrow(() -> new ResourceNotFoundException("Column", "id", op.columnId()));

        String oldName = column.getName();
        column.setName(requireName(op.name()));
        BoardColumn saved = columnRepository.save(column);
        log.info("Kolon adı değişti: id={}, ad={}", saved.getId(), saved.getName());

        ColumnRenamedEvent event = ColumnRenamedEvent.of(saved.getId(), saved.getName());
        activityService.record(saved.getBoard().getId(), actor, "RENAME_COLUMN",
                "%s kolonunu %s olarak adlandırdı".formatted(oldName, saved.getName()), event);
        return event;
    }

    /**
     * Bir kolonu ve içindeki kartları sil.
     *
     * Kalan kolonların sırası yeniden numaralandırılır; aksi hâlde 0,1,3 gibi
     * boşluklu bir dizi kalır ve sonraki taşımalar yanlış konuma düşer.
     */
    public ColumnDeletedEvent deleteColumn(DeleteColumnOp op, String actor) {
        BoardColumn column = columnRepository.findById(op.columnId())
                .orElseThrow(() -> new ResourceNotFoundException("Column", "id", op.columnId()));

        Board board = column.getBoard();
        // Silmeden ÖNCE oku: geçmiş kaydına ve yayına bunlar girecek.
        String name = column.getName();
        int position = column.getPosition();
        Long boardId = board.getId();

        board.removeColumn(column);           // orphanRemoval → kolon ve kartları silinir
        boardRepository.saveAndFlush(board);  // flush: aşağıdaki yeniden numaralandırma güncel liste üzerinde çalışsın

        reindex(board.getColumns());
        log.info("Kolon silindi: id={}, boardId={}", op.columnId(), boardId);

        ColumnDeletedEvent event = ColumnDeletedEvent.of(op.columnId(), name, position);
        activityService.record(boardId, actor, "DELETE_COLUMN",
                "%s kolonunu sildi".formatted(name), event);
        return event;
    }

    /** Bir kolonu yeni pozisyona taşı (kolon sırasını değiştir). */
    public ColumnMovedEvent moveColumn(MoveColumnOp op, String actor) {
        BoardColumn column = columnRepository.findById(op.columnId())
                .orElseThrow(() -> new ResourceNotFoundException("Column", "id", op.columnId()));

        // NOT: diğer kolonların pozisyonlarını yeniden düzenlemek (reindex) sonraki iş.
        column.setPosition(op.position());
        BoardColumn saved = columnRepository.save(column);
        log.info("Kolon taşındı: id={}, pos={}", saved.getId(), saved.getPosition());

        ColumnMovedEvent event = ColumnMovedEvent.of(saved.getId(), saved.getPosition());
        activityService.record(column.getBoard().getId(), actor, "MOVE_COLUMN",
                "%s kolonunu %d. sıraya taşıdı".formatted(saved.getName(), saved.getPosition() + 1), event);
        return event;
    }

    /**
     * Bir kolonun WIP limitini ayarla; null → sınır kaldırılır.
     *
     * Mevcut kart sayısı yeni limitin ÜSTÜNDEYSE limit yine de kabul edilir. Sebep:
     * limit koymanın amacı fazlalığı silmek değil, yeni giriş yapılmasını durdurmaktır.
     * Kolon "aşırı dolu" görünür ve boşalana kadar kart kabul etmez — Kanban'da
     * beklenen davranış budur.
     */
    public WipLimitChangedEvent setWipLimit(SetWipLimitOp op, String actor) {
        BoardColumn column = columnRepository.findById(op.columnId())
                .orElseThrow(() -> new ResourceNotFoundException("Column", "id", op.columnId()));

        if (op.limit() != null && op.limit() < 1) {
            throw new BadRequestException("WIP limiti en az 1 olmalı; sınırı kaldırmak için boş bırak.");
        }

        column.setWipLimit(op.limit());
        BoardColumn saved = columnRepository.save(column);
        log.info("WIP limiti ayarlandı: columnId={}, limit={}", saved.getId(), saved.getWipLimit());

        WipLimitChangedEvent event = WipLimitChangedEvent.of(saved.getId(), saved.getWipLimit());
        String aciklama = saved.getWipLimit() == null
                ? "%s kolonunun WIP limitini kaldırdı".formatted(saved.getName())
                : "%s kolonunun WIP limitini %d yaptı".formatted(saved.getName(), saved.getWipLimit());
        activityService.record(saved.getBoard().getId(), actor, "SET_WIP_LIMIT", aciklama, event);
        return event;
    }

    /** Kolon sırasını 0'dan başlayarak boşluksuz yeniden numaralandırır. */
    private void reindex(List<BoardColumn> columns) {
        List<BoardColumn> ordered = columns.stream()
                .sorted(Comparator.comparingInt(BoardColumn::getPosition))
                .toList();
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).setPosition(i);
        }
    }

    private String requireName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw new BadRequestException("Kolon adı boş olamaz.");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new BadRequestException("Kolon adı en fazla " + MAX_NAME_LENGTH + " karakter olabilir.");
        }
        return name;
    }
}
