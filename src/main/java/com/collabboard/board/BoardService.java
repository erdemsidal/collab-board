package com.collabboard.board;

import com.collabboard.board.dto.BoardResponse;
import com.collabboard.board.dto.BoardSummaryResponse;
import com.collabboard.board.entity.Board;
import com.collabboard.board.entity.BoardColumn;
import com.collabboard.common.exception.ResourceNotFoundException;
import com.collabboard.board.entity.BoardMember;
import com.collabboard.board.entity.BoardRole;
import com.collabboard.user.UserService;
import com.collabboard.user.entity.User;
import com.collabboard.workspace.WorkspaceAccessService;
import com.collabboard.workspace.WorkspaceMemberRepository;
import com.collabboard.workspace.WorkspaceService;
import com.collabboard.workspace.entity.WorkspaceMember;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private final CardRepository cardRepository;
    private final BoardAccessService accessService;
    private final UserService userService;
    private final WorkspaceService workspaceService;
    private final WorkspaceAccessService workspaceAccessService;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    public BoardService(BoardRepository boardRepository, BoardMemberRepository memberRepository,
                        BoardAccessService accessService, UserService userService,
                        WorkspaceService workspaceService, WorkspaceAccessService workspaceAccessService,
                        WorkspaceMemberRepository workspaceMemberRepository,
                        CardRepository cardRepository) {
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.cardRepository = cardRepository;
        this.boardRepository = boardRepository;
        this.memberRepository = memberRepository;
        this.accessService = accessService;
        this.userService = userService;
        this.workspaceService = workspaceService;
        this.workspaceAccessService = workspaceAccessService;
    }

    /**
     * Yeni pano oluştur — 3 varsayılan kolonla birlikte.
     * Oluşturan kişi otomatik olarak panonun OWNER'ı olur.
     * @Transactional (readOnly=false): bu bir YAZMA işlemi.
     */
    @Transactional
    public BoardResponse createBoard(String name, Long workspaceId, String actorEmail) {
        User actor = userService.getUserByEmail(actorEmail);

        // Çalışma alanı belirtilmediyse kullanıcının kişisel alanına açılır.
        // Belirtildiyse orada pano açma yetkisi var mı kontrol edilir (misafir açamaz).
        Long targetWorkspaceId;
        if (workspaceId == null) {
            targetWorkspaceId = workspaceService.personalWorkspaceFor(actor).getId();
        } else {
            workspaceAccessService.requireBoardCreator(workspaceId, actorEmail);
            targetWorkspaceId = workspaceId;
        }

        Board board = Board.builder().name(name).workspaceId(targetWorkspaceId).build();

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

        // Oluşturan kişi panonun sahibi olur — pano bazlı bir İSTİSNA kaydı olarak.
        // Neden gerekli? Şirkette rolü MEMBER olan biri, kendi açtığı panoda yalnızca
        // EDITOR sayılırdı ve kendi panosunun üyelerini yönetemezdi. Bu kayıt ona
        // kendi panosunda sahiplik verir (istisna, şirket rolünü ezer).
        Long userId = actor.getId();
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
     * Kullanıcının erişebildiği panolar ("panolarım"), kendi rolüyle birlikte.
     *
     * İKİ KAYNAKTAN beslenir — etkin rol mantığının liste hâli:
     *   1. Üyesi olduğu ŞİRKETLERİN panoları (rol şirket rolünden türetilir)
     *   2. Kişiye özel pano davetleri — bunlar birinciyi EZER
     * Aynı pano iki kaynaktan da gelirse, istisna kazanır (Map'e sonra yazılır).
     */
    public List<BoardSummaryResponse> myBoards(String actorEmail) {
        Long userId = userService.getUserByEmail(actorEmail).getId();

        // LinkedHashMap: hem tekilleştirir hem de eklenme sırasını korur.
        Map<Long, BoardSummaryResponse> byBoardId = new LinkedHashMap<>();

        // 1) Şirket üyeliklerinden gelen panolar
        for (WorkspaceMember membership : workspaceMemberRepository.findByUserIdOrderByWorkspaceIdDesc(userId)) {
            accessService.toBoardRole(membership.getRole()).ifPresent(role ->
                    boardRepository.findByWorkspaceIdOrderByIdDesc(membership.getWorkspaceId())
                            .forEach(board -> byBoardId.put(board.getId(), summarize(board, role))));
        }

        // 2) Pano bazlı istisnalar — şirketten gelen rolün üstüne yazar
        for (BoardMember member : memberRepository.findByUserIdOrderByBoardIdDesc(userId)) {
            boardRepository.findById(member.getBoardId()).ifPresent(board ->
                    byBoardId.put(board.getId(), summarize(board, member.getRole())));
        }

        return List.copyOf(byBoardId.values());
    }

    /** Pano kartında gösterilen özet (kart ve üye sayılarıyla). */
    private BoardSummaryResponse summarize(Board board, BoardRole role) {
        return BoardSummaryResponse.of(board, role,
                cardRepository.countByBoardId(board.getId()),
                memberRepository.countByBoardId(board.getId()));
    }
}
