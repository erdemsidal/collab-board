package com.collabboard.board;

import com.collabboard.board.dto.BoardResponse;
import com.collabboard.board.dto.BoardSummaryResponse;
import com.collabboard.board.entity.Board;
import com.collabboard.board.entity.BoardColumn;
import com.collabboard.common.exception.ResourceNotFoundException;
import com.collabboard.board.entity.BoardMember;
import com.collabboard.board.entity.BoardRole;
import com.collabboard.board.template.BoardTemplate;
import com.collabboard.board.template.BoardTemplateService;
import com.collabboard.board.template.dto.BoardTemplateResponse;
import com.collabboard.comment.CommentRepository;
import com.collabboard.i18n.AppLocale;
import com.collabboard.user.UserService;
import com.collabboard.user.entity.User;
import com.collabboard.workspace.WorkspaceAccessService;
import com.collabboard.workspace.WorkspaceMemberRepository;
import com.collabboard.workspace.WorkspaceRepository;
import com.collabboard.workspace.WorkspaceService;
import com.collabboard.workspace.entity.WorkspaceMember;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    private final BoardRepository boardRepository;
    private final BoardMemberRepository memberRepository;
    private final CardRepository cardRepository;
    private final CommentRepository commentRepository;
    private final BoardAccessService accessService;
    private final UserService userService;
    private final WorkspaceService workspaceService;
    private final WorkspaceAccessService workspaceAccessService;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceRepository workspaceRepository;
    private final BoardTemplateService templateService;

    public BoardService(BoardRepository boardRepository, BoardMemberRepository memberRepository,
                        BoardAccessService accessService, UserService userService,
                        WorkspaceService workspaceService, WorkspaceAccessService workspaceAccessService,
                        WorkspaceMemberRepository workspaceMemberRepository,
                        WorkspaceRepository workspaceRepository,
                        CardRepository cardRepository, CommentRepository commentRepository,
                        BoardTemplateService templateService) {
        this.templateService = templateService;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspaceRepository = workspaceRepository;
        this.cardRepository = cardRepository;
        this.commentRepository = commentRepository;
        this.boardRepository = boardRepository;
        this.memberRepository = memberRepository;
        this.accessService = accessService;
        this.userService = userService;
        this.workspaceService = workspaceService;
        this.workspaceAccessService = workspaceAccessService;
    }

    /**
     * Yeni pano oluştur — seçilen şablonun kolonlarıyla (ADR 0021).
     * Oluşturan kişi otomatik olarak panonun OWNER'ı olur.
     * @Transactional (readOnly=false): bu bir YAZMA işlemi.
     *
     * @param templateId boşsa Basit Kanban; tanınmıyorsa 400. Kolon adları açan
     *                   kişinin kayıtlı dilinde yazılır: Türkçe kullanıcıya "Yapılacak".
     */
    @Transactional
    public BoardResponse createBoard(String name, Long workspaceId, String templateId, String actorEmail) {
        // Şablon her şeyden önce: geçersizse kişisel alan bile açılmadan 400 dönsün.
        BoardTemplate template = templateService.resolve(templateId);
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
        for (BoardTemplateResponse.Column spec : templateService.columns(template, AppLocale.of(actor))) {
            BoardColumn column = BoardColumn.builder()
                    .name(spec.name())
                    .position(position++)   // 0, 1, 2 — kolonların sırası
                    .wipLimit(spec.wipLimit())
                    .build();
            board.addColumn(column);        // ilişkinin iki ucunu da bağlar
        }

        // save + cascade ALL → board'la birlikte kolonları da INSERT eder.
        Board saved = boardRepository.save(board);

        // Oluşturan kişi panonun sahibi olur — pano bazlı bir İSTİSNA kaydı olarak.
        // Neden gerekli? Şirkette rolü MEMBER olan biri, kendi açtığı panoda yalnızca
        // EDITOR sayılırdı ve kendi panosunun üyelerini yönetemezdi. Bu kayıt ona
        // kendi panosunda sahiplik verir (istisna, şirket rolünü ezer).
        Long userId = actor.getId();
        accessService.addOwner(saved.getId(), userId);

        log.info("Pano oluşturuldu: id={}, name='{}', şablon={}, sahibi={}",
                saved.getId(), saved.getName(), template.id(), actorEmail);

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
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound.board", id));

        // Kartların yorum sayıları tek gruplu sorguda; kart başına sayım N+1 olurdu.
        Map<Long, Long> commentCounts = commentRepository.countPerCard(id).stream()
                .collect(Collectors.toMap(CommentRepository.CardCount::getCardId,
                        CommentRepository.CardCount::getComments));

        // Lazy kolonlar/kartlar tam burada, işlem hâlâ açıkken yüklenir.
        return BoardResponse.fromEntity(board, commentCounts);
    }

    /** Kullanıcının erişebildiği panolar ("panolarım"), kendi rolüyle birlikte. */
    public List<BoardSummaryResponse> myBoards(String actorEmail) {
        Long userId = userService.getUserByEmail(actorEmail).getId();
        return accessibleBoards(userId).stream()
                .map(a -> summarize(a.board(), a.role()))
                .toList();
    }

    /**
     * Kullanıcının erişebildiği panolar, etkin rolüyle — "panolarım" listesinin
     * ve ana ekran özetinin ORTAK kaynağı.
     *
     * İKİ KAYNAKTAN beslenir — etkin rol mantığının liste hâli:
     *   1. Üyesi olduğu ŞİRKETLERİN panoları (rol şirket rolünden türetilir)
     *   2. Kişiye özel pano davetleri — bunlar birinciyi EZER
     * Aynı pano iki kaynaktan da gelirse, istisna kazanır (Map'e sonra yazılır).
     *
     * Tek yerde durması bilinçli: özet "hangi panolar benim" sorusunu kendisi
     * yanıtlasaydı, yetki kuralı değiştiğinde ikisinden biri unutulur ve özet
     * erişilemeyen bir panonun kartlarını sızdırırdı.
     *
     * Sorgular toplu: alan ya da üyelik başına ayrı pano sorgusu atılmaz.
     */
    public List<AccessibleBoard> accessibleBoards(Long userId) {
        // LinkedHashMap: hem tekilleştirir hem de eklenme sırasını korur.
        Map<Long, AccessibleBoard> byBoardId = new LinkedHashMap<>();

        // 1) Şirket üyeliklerinden gelen panolar (misafirlik tek başına pano açmaz)
        Map<Long, BoardRole> roleByWorkspace = new LinkedHashMap<>();
        for (WorkspaceMember membership : workspaceMemberRepository.findByUserIdOrderByWorkspaceIdDesc(userId)) {
            accessService.toBoardRole(membership.getRole())
                    .ifPresent(role -> roleByWorkspace.put(membership.getWorkspaceId(), role));
        }
        if (!roleByWorkspace.isEmpty()) {
            // groupingBy listeleri sorgunun sırasını (en yeni pano önce) korur.
            Map<Long, List<Board>> boardsByWorkspace = boardRepository
                    .findByWorkspaceIdInOrderByIdDesc(roleByWorkspace.keySet()).stream()
                    .collect(Collectors.groupingBy(Board::getWorkspaceId));
            roleByWorkspace.forEach((workspaceId, role) ->
                    boardsByWorkspace.getOrDefault(workspaceId, List.of())
                            .forEach(board -> byBoardId.put(board.getId(), new AccessibleBoard(board, role))));
        }

        // 2) Pano bazlı istisnalar — şirketten gelen rolün üstüne yazar
        List<BoardMember> exceptions = memberRepository.findByUserIdOrderByBoardIdDesc(userId);
        Map<Long, Board> exceptionBoards = boardRepository
                .findAllById(exceptions.stream().map(BoardMember::getBoardId).toList()).stream()
                .collect(Collectors.toMap(Board::getId, Function.identity()));
        for (BoardMember member : exceptions) {
            Board board = exceptionBoards.get(member.getBoardId());
            if (board != null) {
                byBoardId.put(board.getId(), new AccessibleBoard(board, member.getRole()));
            }
        }

        return List.copyOf(byBoardId.values());
    }

    /** Pano kartında gösterilen özet (kart ve üye sayılarıyla). */
    /**
     * Alan adı da eklenir: panoya yalnızca pano düzeyinde davet edilen kişi o
     * alanın üyesi değildir ve alan listesinde onu görmez. Panonun nereden
     * geldiğini kartın üstünde ancak böyle görebilir.
     */
    private BoardSummaryResponse summarize(Board board, BoardRole role) {
        String workspaceName = workspaceRepository.findById(board.getWorkspaceId())
                .map(w -> w.getName()).orElse(null);
        return BoardSummaryResponse.of(board, workspaceName, role,
                cardRepository.countByBoardId(board.getId()),
                memberRepository.countByBoardId(board.getId()));
    }
}
