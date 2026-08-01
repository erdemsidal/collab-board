package com.collabboard.board;

import com.collabboard.board.entity.Board;
import com.collabboard.board.entity.BoardMember;
import com.collabboard.board.entity.BoardRole;
import com.collabboard.common.exception.ForbiddenException;
import com.collabboard.user.UserService;
import com.collabboard.workspace.WorkspaceAccessService;
import com.collabboard.workspace.entity.WorkspaceRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Yetkilendirmenin tek kapısı: "bu kullanıcı bu panoda ne yapabilir?"
 *
 * Kontroller tek yerde toplanır; koda dağılsaydı bir gün biri unutulur ve
 * unutulan kontrol sessiz bir açık olurdu.
 */
@Service
@Transactional(readOnly = true)
public class BoardAccessService {

    private static final Logger log = LoggerFactory.getLogger(BoardAccessService.class);

    private final BoardMemberRepository memberRepository;
    private final BoardRepository boardRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final UserService userService;

    public BoardAccessService(BoardMemberRepository memberRepository, BoardRepository boardRepository,
                              WorkspaceAccessService workspaceAccessService, UserService userService) {
        this.memberRepository = memberRepository;
        this.boardRepository = boardRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.userService = userService;
    }

    /**
     * Kullanıcının panodaki rolü. Yetkisi yoksa ForbiddenException.
     * Okuma erişimi için bunu çağırmak yeterlidir (her rol okuyabilir).
     */
    public BoardRole requireMember(Long boardId, String email) {
        Long userId = userService.getUserByEmail(email).getId();
        return effectiveRole(boardId, userId)
                .orElseThrow(() -> {
                    log.warn("Yetkisiz pano erişimi: boardId={}, kullanıcı={}", boardId, email);
                    // Not: "pano yok" ile "erişimin yok" ayrımını dışarıya sızdırmıyoruz;
                    // ikisi de aynı mesajı verir, böylece pano varlığı tahmin edilemez.
                    return new ForbiddenException("Bu panoya erişim yetkiniz yok");
                });
    }

    /**
     * Etkin rol: önce panoya özel kayda, yoksa çalışma alanı üyeliğine bakılır.
     *
     * Sıra bilinçli — panoya özel kayıt bir istisnadır ve alan rolünü ezer. Böylece
     * hem dışarıdan biri tek bir panoya davet edilebilir, hem de bir ekip üyesi
     * hassas bir panoda kısıtlanabilir.
     *
     * Her çağrı 1-2 sorgu yapar; yük artarsa kısa ömürlü bir cache gerekir, ama
     * üyelik değiştiğinde temizlenmesi şart.
     */
    private Optional<BoardRole> effectiveRole(Long boardId, Long userId) {
        Optional<BoardRole> boardException = memberRepository.findByBoardIdAndUserId(boardId, userId)
                .map(BoardMember::getRole);
        if (boardException.isPresent()) {
            return boardException;
        }

        return boardRepository.findById(boardId)
                .map(Board::getWorkspaceId)
                .flatMap(workspaceId -> workspaceAccessService.roleOf(workspaceId, userId))
                .flatMap(this::toBoardRole);
    }

    /**
     * Çalışma alanı rolünün panodaki karşılığı. Bir politika kararı olduğu için
     * pano tarafında duruyor; "panolarım" listesi de aynı eşlemeyi kullanır.
     */
    public Optional<BoardRole> toBoardRole(WorkspaceRole workspaceRole) {
        return switch (workspaceRole) {
            case OWNER, ADMIN -> Optional.of(BoardRole.OWNER);    // şirket yöneticisi her panoda tam yetkili
            case MEMBER       -> Optional.of(BoardRole.EDITOR);   // ekip üyesi içerik düzenler
            case GUEST        -> Optional.empty();                // misafir: yalnızca ayrıca davet edildiği panolar
        };
    }

    /** Kart/kolon değiştirmek için: OWNER veya EDITOR olmalı. */
    public BoardRole requireEditor(Long boardId, String email) {
        BoardRole role = requireMember(boardId, email);
        if (!role.canEdit()) {
            throw new ForbiddenException("Bu panoda değişiklik yapma yetkiniz yok (rolünüz: " + role + ")");
        }
        return role;
    }

    /** Üye yönetimi için: OWNER olmalı. */
    public BoardRole requireOwner(Long boardId, String email) {
        BoardRole role = requireMember(boardId, email);
        if (!role.canManageMembers()) {
            throw new ForbiddenException("Bu işlem için pano sahibi olmalısınız (rolünüz: " + role + ")");
        }
        return role;
    }

    /** Panoyu oluşturan kişiyi OWNER olarak kaydeder. */
    @Transactional
    public void addOwner(Long boardId, Long userId) {
        save(boardId, userId, BoardRole.OWNER);
    }

    @Transactional
    public BoardMember save(Long boardId, Long userId, BoardRole role) {
        return memberRepository.save(BoardMember.builder()
                .boardId(boardId)
                .userId(userId)
                .role(role)
                .createdAt(LocalDateTime.now())
                .build());
    }
}
