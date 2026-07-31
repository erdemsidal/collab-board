package com.collabboard.board;

import com.collabboard.board.dto.BoardResponse;
import com.collabboard.board.dto.BoardSummaryResponse;
import com.collabboard.board.entity.Board;
import com.collabboard.board.entity.BoardColumn;
import com.collabboard.common.exception.ResourceNotFoundException;
import com.collabboard.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

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
    private final BoardMemberRepository memberRepository;
    private final BoardAccessService accessService;
    private final UserService userService;

    public BoardService(BoardRepository boardRepository, BoardMemberRepository memberRepository,
                        BoardAccessService accessService, UserService userService) {
        this.boardRepository = boardRepository;
        this.memberRepository = memberRepository;
        this.accessService = accessService;
        this.userService = userService;
    }

    /**
     * Yeni pano oluştur — 3 varsayılan kolonla birlikte.
     * Oluşturan kişi otomatik olarak panonun OWNER'ı olur.
     * @Transactional (readOnly=false): bu bir YAZMA işlemi.
     */
    @Transactional
    public BoardResponse createBoard(String name, String actorEmail) {
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

        // Oluşturan kişi panonun sahibi olur — aynı işlem içinde, yani pano
        // kaydedildiyse sahibi de mutlaka kaydedilmiştir (sahipsiz pano oluşamaz).
        Long userId = userService.getUserByEmail(actorEmail).getId();
        accessService.addOwner(saved.getId(), userId);

        log.info("Pano oluşturuldu: id={}, name='{}', sahibi={}", saved.getId(), saved.getName(), actorEmail);

        // DTO dönüşümü İŞLEM İÇİNDE (open-in-view=false → dışarıda lazy patlar).
        return BoardResponse.fromEntity(saved);
    }

    /**
     * Panonun tam hâlini getir ("fotoğraf").
     * Üye olmayan erişemez (403); pano yoksa 404.
     */
    public BoardResponse getBoard(Long id, String actorEmail) {
        accessService.requireMember(id, actorEmail);

        Board board = boardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Board", "id", id));

        // Lazy kolonlar/kartlar tam burada, işlem hâlâ açıkken yüklenir.
        return BoardResponse.fromEntity(board);
    }

    /**
     * Kullanıcının üye olduğu panolar ("panolarım").
     * Kendi rolüyle birlikte döner ki arayüz neyi yapabileceğini bilsin.
     */
    public List<BoardSummaryResponse> myBoards(String actorEmail) {
        Long userId = userService.getUserByEmail(actorEmail).getId();

        return memberRepository.findByUserIdOrderByBoardIdDesc(userId).stream()
                .map(member -> boardRepository.findById(member.getBoardId())
                        .map(board -> BoardSummaryResponse.of(board, member.getRole()))
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }
}
