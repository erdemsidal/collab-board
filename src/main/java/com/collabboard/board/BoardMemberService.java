package com.collabboard.board;

import com.collabboard.board.dto.MemberResponse;
import com.collabboard.board.entity.BoardMember;
import com.collabboard.board.entity.BoardRole;
import com.collabboard.common.exception.BadRequestException;
import com.collabboard.common.exception.ResourceNotFoundException;
import com.collabboard.user.UserRepository;
import com.collabboard.user.UserService;
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
    private final BoardAccessService accessService;
    private final UserRepository userRepository;
    private final UserService userService;

    public BoardMemberService(BoardMemberRepository memberRepository, BoardAccessService accessService,
                              UserRepository userRepository, UserService userService) {
        this.memberRepository = memberRepository;
        this.accessService = accessService;
        this.userRepository = userRepository;
        this.userService = userService;
    }

    /** Panonun üyeleri (isim ve rolleriyle). */
    public List<MemberResponse> members(Long boardId) {
        return memberRepository.findByBoardId(boardId).stream()
                .map(member -> userRepository.findById(member.getUserId())
                        .map(user -> new MemberResponse(user.getId(), user.getFirstName(),
                                user.getLastName(), user.getEmail(), member.getRole()))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * Üye ekle; zaten üyeyse rolünü günceller (davet tekrarını hata yapmıyoruz).
     */
    @Transactional
    public MemberResponse addOrUpdate(Long boardId, String email, BoardRole role) {
        User user = userService.getUserByEmail(email);   // kullanıcı yoksa 404

        BoardMember member = memberRepository.findByBoardIdAndUserId(boardId, user.getId())
                .orElse(null);

        if (member == null) {
            accessService.save(boardId, user.getId(), role);
            log.info("Panoya üye eklendi: boardId={}, kullanıcı={}, rol={}", boardId, email, role);
        } else {
            // Son OWNER'ın rolü düşürülemez: pano sahipsiz kalmamalı.
            if (member.getRole() == BoardRole.OWNER && role != BoardRole.OWNER) {
                requireAnotherOwnerExists(boardId);
            }
            member.setRole(role);
            memberRepository.save(member);
            log.info("Üye rolü güncellendi: boardId={}, kullanıcı={}, yeni rol={}", boardId, email, role);
        }

        return new MemberResponse(user.getId(), user.getFirstName(), user.getLastName(),
                user.getEmail(), role);
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
