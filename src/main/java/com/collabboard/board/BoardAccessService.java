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
 * Yetkilendirmenin TEK KAPISI: "bu kullanıcı bu panoda ne yapabilir?"
 *
 * Kontroller neden tek bir serviste toplanıyor? Yetki kontrolü koda dağılırsa
 * bir gün birinin unutulması kaçınılmazdır — ve unutulan kontrol sessiz bir
 * güvenlik açığıdır. Tek kapı olunca hem denetlemesi hem değiştirmesi kolay.
 *
 * ŞİRKET/WORKSPACE HAZIRLIĞI: İleride üyelik zinciri
 *   kullanıcı → şirket üyeliği → şirketin panoları
 * hâline geldiğinde, çağıran kodun hiçbiri değişmez; yalnızca bu sınıftaki
 * roleOf(...) metodu "önce pano üyeliğine, yoksa şirket üyeliğine bak" diye
 * genişletilir. Çağrı noktalarını bu yüzden requireX(...) şeklinde soyutladık.
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
     * ETKİN ROL — yetkilendirmenin kalbi.
     *
     * Sıra önemli:
     *  1. Panoda kişiye özel bir kayıt var mı? → o rol geçerlidir.
     *     Bu bir İSTİSNADIR ve şirket rolünü EZER. Böylece hem şirket dışından bir
     *     misafiri tek panoya davet edebiliriz, hem de bir şirket üyesini tek bir
     *     panoda kısıtlayabiliriz (ör. hassas pano → VIEWER).
     *  2. Yoksa panonun ait olduğu ŞİRKETTEKİ rolüne bak. Şirket üyeliği, şirketin
     *     tüm panolarına varsayılan erişim verir — özelliğin asıl amacı budur:
     *     ekibe bir kez davet, her panoya erişim.
     *  3. İkisi de yoksa erişim yok.
     *
     * Not (performans): her yetki kontrolü 1-2 sorgu yapar. Yük artarsa etkin rol
     * kısa ömürlü bir cache'e alınabilir — ama üyelik değişince temizlenmelidir.
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
     * Şirket rolünün panodaki karşılığı.
     *
     * Bu eşleme bir POLİTİKA kararıdır ve bilerek pano tarafında duruyor:
     * "şirket yöneticisi panoda ne yapabilir" sorusunun cevabı pano modülüne aittir.
     * "Panolarım" listesi de aynı eşlemeyi kullanır — kural tek yerde tanımlı kalsın.
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
