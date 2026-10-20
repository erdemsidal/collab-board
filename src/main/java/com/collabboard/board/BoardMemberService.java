package com.collabboard.board;

import com.collabboard.board.dto.MemberResponse;
import com.collabboard.board.entity.BoardMember;
import com.collabboard.board.entity.BoardRole;
import com.collabboard.common.exception.BadRequestException;
import com.collabboard.common.exception.ResourceNotFoundException;
import com.collabboard.user.UserRepository;
import com.collabboard.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Pano üyeliklerinin yönetimi: listele, ekle/rol değiştir, çıkar.
 *
 * Yetki kontrolleri burada DEĞİL, BoardAccessService'te yapılır ve controller'dan
 * çağrılır; bu servis "ne yapılacağını" bilir, "kimin yapabileceğini" değil.
 */
@Service
@Transactional(readOnly = true)
public class BoardMemberService {

    private static final Logger log = LoggerFactory.getLogger(BoardMemberService.class);

    private final BoardMemberRepository memberRepository;
    private final UserRepository userRepository;

    public BoardMemberService(BoardMemberRepository memberRepository, UserRepository userRepository) {
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
    }

    /** Panonun üyeleri (isim ve rolleriyle). */
    public List<MemberResponse> members(Long boardId) {
        return memberRepository.findByBoardId(boardId).stream()
                .map(member -> userRepository.findById(member.getUserId())
                        .map(user -> MemberResponse.of(user, member.getRole()))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * Mevcut bir üyenin rolünü değiştirir.
     *
     * Yeni üye buradan EKLENEMEZ: panoya katılmak davet edilen kişinin onayını
     * ister (InvitationService). Rol değişikliği ise zaten üye olan biri için
     * yöneticinin kararıdır; onay beklenmez.
     */
    @Transactional
    public MemberResponse changeRole(Long boardId, Long userId, BoardRole role) {
        BoardMember member = memberRepository.findByBoardIdAndUserId(boardId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Üye", "userId", userId));

        // Son OWNER'ın rolü düşürülemez: pano sahipsiz kalmamalı.
        if (member.getRole() == BoardRole.OWNER && role != BoardRole.OWNER) {
            requireAnotherOwnerExists(boardId);
        }
        member.setRole(role);
        memberRepository.save(member);
        log.info("Üye rolü güncellendi: boardId={}, userId={}, yeni rol={}", boardId, userId, role);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı", "id", userId));
        return MemberResponse.of(user, role);
    }

    /** Üyeyi panodan çıkar. */
    @Transactional
    public void remove(Long boardId, Long userId) {
        BoardMember member = memberRepository.findByBoardIdAndUserId(boardId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Üye", "userId", userId));

        if (member.getRole() == BoardRole.OWNER) {
            requireAnotherOwnerExists(boardId);
        }
        memberRepository.delete(member);
        log.info("Üye panodan çıkarıldı: boardId={}, userId={}", boardId, userId);
    }

    /**
     * Panoda başka bir OWNER var mı? Yoksa işlemi engelle.
     *
     * Neden önemli: son sahibi düşürmek/çıkarmak panoyu YÖNETİLEMEZ hâle getirir —
     * kimse üye ekleyemez, kimse yetki veremez. Bu tür "geri dönülemez kilitlenme"
     * durumlarını en baştan engellemek gerekir.
     */
    private void requireAnotherOwnerExists(Long boardId) {
        if (memberRepository.countByBoardIdAndRole(boardId, BoardRole.OWNER) <= 1) {
            throw new BadRequestException(
                    "Panonun son sahibini çıkaramaz veya rolünü düşüremezsiniz. Önce başka bir sahip atayın.");
        }
    }
}
