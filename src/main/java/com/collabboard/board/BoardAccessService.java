package com.collabboard.board;

import com.collabboard.board.entity.BoardMember;
import com.collabboard.board.entity.BoardRole;
import com.collabboard.common.exception.ForbiddenException;
import com.collabboard.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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
    private final UserService userService;

    public BoardAccessService(BoardMemberRepository memberRepository, UserService userService) {
        this.memberRepository = memberRepository;
        this.userService = userService;
    }

    /**
     * Kullanıcının panodaki rolü. Üye değilse ForbiddenException.
     * Okuma erişimi için bunu çağırmak yeterlidir (her rol okuyabilir).
     */
    public BoardRole requireMember(Long boardId, String email) {
        Long userId = userService.getUserByEmail(email).getId();
        return memberRepository.findByBoardIdAndUserId(boardId, userId)
                .map(BoardMember::getRole)
                .orElseThrow(() -> {
                    log.warn("Yetkisiz pano erişimi: boardId={}, kullanıcı={}", boardId, email);
                    // Not: "pano yok" ile "erişimin yok" ayrımını dışarıya sızdırmıyoruz;
                    // ikisi de aynı mesajı verir, böylece pano varlığı tahmin edilemez.
                    return new ForbiddenException("Bu panoya erişim yetkiniz yok");
                });
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
