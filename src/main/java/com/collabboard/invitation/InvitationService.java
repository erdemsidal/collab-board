package com.collabboard.invitation;

import com.collabboard.audit.ActivityService;
import com.collabboard.board.BoardAccessService;
import com.collabboard.board.BoardMemberRepository;
import com.collabboard.board.BoardRepository;
import com.collabboard.board.entity.Board;
import com.collabboard.board.entity.BoardRole;
import com.collabboard.board.operation.BoardSignal;
import com.collabboard.common.exception.BadRequestException;
import com.collabboard.common.exception.ConflictException;
import com.collabboard.common.exception.ResourceNotFoundException;
import com.collabboard.common.tx.AfterCommit;
import com.collabboard.i18n.AppLocale;
import com.collabboard.invitation.dto.AcceptInvitationResponse;
import com.collabboard.invitation.dto.InvitationResponse;
import com.collabboard.mail.InvitationMailTemplate;
import com.collabboard.mail.MailService;
import com.collabboard.notification.NotificationService;
import com.collabboard.notification.NotificationType;
import com.collabboard.realtime.BroadcastService;
import com.collabboard.user.AvatarUrls;
import com.collabboard.user.UserRepository;
import com.collabboard.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Pano davetleri.
 *
 * Önceden yönetici bir e-posta yazdığı anda o kişi panoya ekleniyordu — haberi
 * ya da onayı olmadan. Artık üyelik iki adımlı: yönetici DAVET EDER, davet
 * edilen KABUL EDİNCE üye olur. Her adım karşı tarafa canlı bildirilir.
 *
 * Yetki kontrolü (kim davet edebilir) burada değil controller'da, tek kapıdan
 * (BoardAccessService) yapılır. Yanıt verme yetkisi ise burada: daveti yalnızca
 * davet edilen adresin sahibi yanıtlayabilir.
 */
@Service
@Transactional
public class InvitationService {

    private static final Logger log = LoggerFactory.getLogger(InvitationService.class);

    private final BoardInvitationRepository invitationRepository;
    private final BoardRepository boardRepository;
    private final BoardMemberRepository memberRepository;
    private final BoardAccessService accessService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final BroadcastService broadcastService;
    private final ActivityService activityService;
    private final MailService mailService;
    private final InvitationMailTemplate mailTemplate;
    private final String baseUrl;

    public InvitationService(BoardInvitationRepository invitationRepository, BoardRepository boardRepository,
                             BoardMemberRepository memberRepository, BoardAccessService accessService,
                             UserRepository userRepository,
                             NotificationService notificationService, BroadcastService broadcastService,
                             ActivityService activityService, MailService mailService,
                             InvitationMailTemplate mailTemplate, @Value("${app.base-url}") String baseUrl) {
        this.invitationRepository = invitationRepository;
        this.boardRepository = boardRepository;
        this.memberRepository = memberRepository;
        this.accessService = accessService;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.broadcastService = broadcastService;
        this.activityService = activityService;
        this.mailService = mailService;
        this.mailTemplate = mailTemplate;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** Davet gönderir. Davet edilenin hesabı varsa canlı bildirim, yoksa e-posta gider. */
    public InvitationResponse invite(Long boardId, String inviterEmail, String rawEmail, BoardRole role) {
        String email = normalize(rawEmail);
        User inviter = userByEmail(inviterEmail);
        if (email.equals(normalize(inviter.getEmail()))) {
            throw new BadRequestException("error.invite.self");
        }
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound.board", boardId));

        Optional<User> invitee = userRepository.findByEmailIgnoreCase(email);
        if (invitee.isPresent() && memberRepository.findByBoardIdAndUserId(boardId, invitee.get().getId()).isPresent()) {
            throw new ConflictException("error.invite.alreadyMember");
        }
        if (invitationRepository.existsByBoardIdAndEmailAndStatus(boardId, email, InvitationStatus.PENDING)) {
            throw new ConflictException("error.invite.alreadyPending");
        }

        BoardInvitation invitation;
        try {
            invitation = invitationRepository.saveAndFlush(BoardInvitation.builder()
                    .boardId(boardId)
                    .email(email)
                    .role(role)
                    .invitedBy(inviter.getId())
                    .build());
        } catch (DataIntegrityViolationException e) {
            // Aynı anda iki yönetici aynı adresi davet etti; tekil index (V13) ikincisini durdurdu.
            throw new ConflictException("error.invite.alreadyPending");
        }

        if (invitee.isPresent()) {
            notificationService.notify(invitee.get(), NotificationType.BOARD_INVITE, inviter, board, invitation);
        } else {
            sendInvitationMail(email, inviter, board, role);
        }
        AfterCommit.run(() -> broadcastService.broadcast("/topic/board." + boardId, BoardSignal.invitationsChanged()));

        log.info("Davet gönderildi: boardId={}, davetli={}, rol={}, hesabı var mı={}",
                boardId, email, role, invitee.isPresent());
        return toResponse(invitation, invitee.orElse(null), inviter);
    }

    /** Panonun bekleyen davetleri (yönetici görür). */
    @Transactional(readOnly = true)
    public List<InvitationResponse> pendingForBoard(Long boardId) {
        return invitationRepository.findByBoardIdAndStatusOrderByIdDesc(boardId, InvitationStatus.PENDING).stream()
                .map(inv -> toResponse(inv,
                        userRepository.findByEmailIgnoreCase(inv.getEmail()).orElse(null),
                        inv.getInvitedBy() == null ? null : userRepository.findById(inv.getInvitedBy()).orElse(null)))
                .toList();
    }

    /**
     * Daveti kabul eder: kişi panonun üyesi olur.
     *
     * Kişi panoya zaten çalışma alanı üzerinden erişiyorsa da kayıt oluşur; o
     * zaman pano bazlı rol, alan rolünün önüne geçen bir istisna olur
     * (BoardAccessService.effectiveRole).
     */
    public AcceptInvitationResponse accept(Long invitationId, String userEmail) {
        User user = userByEmail(userEmail);
        BoardInvitation invitation = respondable(invitationId, user);
        Board board = boardRepository.findById(invitation.getBoardId())
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound.board", invitation.getBoardId()));

        memberRepository.findByBoardIdAndUserId(board.getId(), user.getId()).ifPresentOrElse(
                existing -> existing.setRole(invitation.getRole()),
                () -> accessService.save(board.getId(), user.getId(), invitation.getRole()));

        resolve(invitation, InvitationStatus.ACCEPTED, user);
        notifyInviter(invitation, NotificationType.INVITE_ACCEPTED, user, board);
        activityService.record(board.getId(), user.getEmail(), "MEMBER_JOINED",
                "panoya %s olarak katıldı".formatted(roleLabel(invitation.getRole())), null,
                Map.of("role", invitation.getRole().name()));

        Long boardId = board.getId();
        AfterCommit.run(() -> {
            broadcastService.broadcast("/topic/board." + boardId, BoardSignal.membersChanged());
            broadcastService.broadcast("/topic/board." + boardId, BoardSignal.invitationsChanged());
        });

        log.info("Davet kabul edildi: invitationId={}, boardId={}, kullanıcı={}", invitationId, boardId, user.getId());
        return new AcceptInvitationResponse(board.getId(), board.getName(), invitation.getRole());
    }

    /** Daveti reddeder. Gönderen bilgilendirilir. */
    public void decline(Long invitationId, String userEmail) {
        User user = userByEmail(userEmail);
        BoardInvitation invitation = respondable(invitationId, user);
        Board board = boardRepository.findById(invitation.getBoardId()).orElse(null);

        resolve(invitation, InvitationStatus.DECLINED, user);
        notifyInviter(invitation, NotificationType.INVITE_DECLINED, user, board);

        Long boardId = invitation.getBoardId();
        AfterCommit.run(() -> broadcastService.broadcast("/topic/board." + boardId, BoardSignal.invitationsChanged()));
        log.info("Davet reddedildi: invitationId={}, kullanıcı={}", invitationId, user.getId());
    }

    /** Yönetici bekleyen daveti geri çeker. Davet edilenin hesabı varsa haberi olur. */
    public void cancel(Long boardId, Long invitationId, String ownerEmail) {
        BoardInvitation invitation = invitationRepository.findById(invitationId)
                .filter(inv -> inv.getBoardId().equals(boardId))
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound.invitation", invitationId));
        if (!invitation.isPending()) {
            throw new BadRequestException("error.invite.alreadyHandled");
        }
        User owner = userByEmail(ownerEmail);

        invitation.setStatus(InvitationStatus.CANCELLED);
        invitation.setRespondedAt(LocalDateTime.now());

        userRepository.findByEmailIgnoreCase(invitation.getEmail()).ifPresent(invitee -> {
            notificationService.markInviteHandled(invitee.getId(), invitation.getId());
            notificationService.notify(invitee, NotificationType.INVITE_CANCELLED, owner,
                    boardRepository.findById(boardId).orElse(null), invitation);
        });
        AfterCommit.run(() -> broadcastService.broadcast("/topic/board." + boardId, BoardSignal.invitationsChanged()));
        log.info("Davet geri çekildi: invitationId={}, boardId={}", invitationId, boardId);
    }

    /**
     * Yanıt verilebilecek davet: bekliyor olmalı ve bu kişiye gönderilmiş olmalı.
     *
     * Başkasının davetine 404 dönülür, 403 değil: davetin var olduğunu bile
     * doğrulamamak için (pano erişiminde de aynı ilke).
     */
    private BoardInvitation respondable(Long invitationId, User user) {
        BoardInvitation invitation = invitationRepository.findById(invitationId)
                .filter(inv -> inv.getEmail().equals(normalize(user.getEmail())))
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound.invitation", invitationId));
        if (invitation.getStatus() == InvitationStatus.CANCELLED) {
            throw new BadRequestException("error.invite.cancelled");
        }
        if (!invitation.isPending()) {
            throw new BadRequestException("error.invite.alreadyAnswered");
        }
        return invitation;
    }

    private void resolve(BoardInvitation invitation, InvitationStatus status, User user) {
        invitation.setStatus(status);
        invitation.setRespondedAt(LocalDateTime.now());
        notificationService.markInviteHandled(user.getId(), invitation.getId());
        notificationService.announceResolved(user, invitation);
    }

    private void notifyInviter(BoardInvitation invitation, NotificationType type, User invitee, Board board) {
        if (invitation.getInvitedBy() == null) {
            return;   // davet edenin hesabı silinmiş
        }
        userRepository.findById(invitation.getInvitedBy()).ifPresent(inviter ->
                notificationService.notify(inviter, type, invitee, board, invitation));
    }

    /**
     * Hesabı olmayan adrese davet postası. Alıcının kayıtlı dili yok: posta davet
     * edenin dilinde gider (ADR 0020).
     */
    private void sendInvitationMail(String email, User inviter, Board board, BoardRole role) {
        String link = baseUrl + "/?signup=" + URLEncoder.encode(email, StandardCharsets.UTF_8);
        String inviterName = NotificationService.fullName(inviter);
        Locale locale = AppLocale.of(inviter);
        mailService.send(email, mailTemplate.subject(locale, inviterName, board.getName()),
                mailTemplate.html(locale, inviterName, board.getName(), role.name(), link),
                mailTemplate.text(locale, inviterName, board.getName(), role.name(), link));
    }

    private InvitationResponse toResponse(BoardInvitation inv, User invitee, User inviter) {
        return InvitationResponse.of(inv, invitee != null,
                invitee == null ? null : NotificationService.fullName(invitee),
                invitee == null ? null : AvatarUrls.of(invitee.getAvatarKey()),
                inviter == null ? null : NotificationService.fullName(inviter));
    }

    private User userByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound.user", email));
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Geçmiş satırının Türkçe yedek metnindeki rol adı. Arayüz satırı params'taki
     * rol kodundan kendi dilinde kurar; postadaki rol adı messages_*.properties'te.
     */
    static String roleLabel(BoardRole role) {
        return switch (role) {
            case OWNER -> "Yönetici";
            case EDITOR -> "Editör";
            case VIEWER -> "İzleyici";
        };
    }
}
