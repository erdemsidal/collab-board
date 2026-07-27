package com.collabboard.board;

import com.collabboard.board.dto.BoardResponse;
import com.collabboard.board.entity.Board;
import com.collabboard.board.entity.BoardColumn;
import com.collabboard.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Pano iş mantığı.
 *
 * @Transactional(readOnly = true) sınıf seviyesinde: metotların çoğu okuma.
 * Yazma yapan metotta bunu @Transactional ile ezeceğiz.
 */
@Service
@Transactional(readOnly = true)
public class BoardService {

    private static final Logger log = LoggerFactory.getLogger(BoardService.class);

    /** Yeni panolara otomatik eklenen varsayılan kolonlar (soldan sağa). */
    private static final List<String> DEFAULT_COLUMNS = List.of("To Do", "In Progress", "Done");

    private final BoardRepository boardRepository;

    public BoardService(BoardRepository boardRepository) {
        this.boardRepository = boardRepository;
    }

    /**
     * Yeni pano oluştur — 3 varsayılan kolonla birlikte.
     * @Transactional (readOnly=false): bu bir YAZMA işlemi.
     */
    @Transactional
    public BoardResponse createBoard(String name) {
        Board board = Board.builder().name(name).build();

        int position = 0;
        for (String columnName : DEFAULT_COLUMNS) {
            BoardColumn column = BoardColumn.builder()
                    .name(columnName)
                    .position(position++)   // 0, 1, 2 — kolonların sırası
                    .build();
            board.addColumn(column);        // senin helper'ın: iki yönü de bağlar
        }

        // save + cascade ALL → board'la birlikte kolonları da INSERT eder.
        Board saved = boardRepository.save(board);
        log.info("Pano oluşturuldu: id={}, name='{}'", saved.getId(), saved.getName());

        // DTO dönüşümü İŞLEM İÇİNDE (open-in-view=false → dışarıda lazy patlar).
        return BoardResponse.fromEntity(saved);
    }

    /**
     * Panonun tam hâlini getir ("fotoğraf"). Bulunamazsa 404 fırlatır.
     */
    public BoardResponse getBoard(Long id) {
        Board board = boardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Board", "id", id));

        // Lazy kolonlar/kartlar tam burada, işlem hâlâ açıkken yüklenir.
        return BoardResponse.fromEntity(board);
    }
}
