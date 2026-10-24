package com.collabboard.notification;

import com.collabboard.board.BoardRepository;
import com.collabboard.board.entity.Board;
import com.collabboard.board.entity.Card;
import com.collabboard.common.exception.ResourceNotFoundException;
import com.collabboard.common.tx.AfterCommit;
import com.collabboard.invitation.BoardInvitation;
import com.collabboard.invitation.BoardInvitationRepository;
import com.collabboard.invitation.InvitationStatus;
import com.collabboard.notification.dto.InboxEvent;
import com.collabboard.notification.dto.NotificationListResponse;
import com.collabboard.notification.dto.NotificationResponse;
import com.collabboard.realtime.BroadcastService;
import com.collabboard.user.AvatarUrls;
import com.collabboard.user.UserRepository;
import com.collabboard.user.entity.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Kişiye özel bildirimler: oluşturma, canlı gönderim, zil menüsü.
 *
 * Bildirim iki yoldan ulaşır:
 *  - Kişi çevrimiçiyse kişisel kanalına ANINDA, hangi ekranda olursa olsun.
 *  - Çevrim dışıysa bir sonraki girişinde zil menüsünden.
 * İkisi de aynı kayıttan beslenir; canlı mesaj kaybolsa bile bildirim kaybolmaz.
 */
@Service
@Transactional
public class NotificationService {

    /** Kullanıcıya özel kanal; istemci "/user/queue/notifications" adresine abone olur. */
    public static final String DESTINATION = "/queue/notifications";

    private static final int MAX_LIMIT = 50;

    private final NotificationRepository repository;
    private final BoardInvitationRepository invitationRepository;
    private final BoardRepository boardRepository;
    private final UserRepository userRepository;
    private final BroadcastService broadcastService;

    public NotificationService(NotificationRepository repository, BoardInvitationRepository invitationRepository,
                               BoardRepository boardRepository, UserRepository userRepository,
                               BroadcastService broadcastService) {
        this.repository = repository;
        this.invitationRepository = invitationRepository;
        this.boardRepository = boardRepository;
        this.userRepository = userRepository;
        this.broadcastService = broadcastService;
    }

    /** Bildirim oluşturur; işlem kaydedilince kullanıcıya canlı gönderir. */
    public Notification notify(User recipient, NotificationType type, User actor, Board board,
                               BoardInvitation invitation) {
        return save(build(recipient, type, actor, board, invitation), recipient, actor,
                invitation == null ? null : invitation.getStatus());
    }

    /**
     * Bir karttaki yorumla ilgili bildirim (bahsetme ya da atanan kişiye haber).
     * Kart ve yorum kimliği taşır: zil menüsünden tıklanınca kart açılır.
     */
    public Notification notifyAboutCard(User recipient, NotificationType type, User actor, Board board,
                                        Card card, Long commentId) {
        Notification n = build(recipient, type, actor, board, null);
        n.setCardId(card.getId());
        n.setCardTitle(card.getTitle());
        n.setCommentId(commentId);
        return save(n, recipient, actor, null);
    }

    /** Bu kişi bu yorumda daha önce anıldı mı? Düzenlemede ikinci bildirim ve posta gitmesin. */
    public boolean wasMentioned(Long userId, Long commentId) {
        return repository.existsByUserIdAndCommentIdAndType(userId, commentId, NotificationType.COMMENT_MENTION);
    }

    private Notification save(Notification n, User recipient, User actor, InvitationStatus invitationStatus) {
        Notification saved = repository.save(n);
        NotificationResponse response = NotificationResponse.of(saved,
                actor == null ? null : AvatarUrls.of(actor.getAvatarKey()), invitationStatus);
        AfterCommit.run(() -> broadcastService.sendToUser(recipient.getEmail(), DESTINATION,
                InboxEvent.notification(response)));
        return saved;
    }

    /** Kişi daveti yanıtladı: öteki sekmeleri de haberdar et. */
    public void announceResolved(User user, BoardInvitation invitation) {
        InboxEvent event = InboxEvent.invitationResolved(invitation.getId(), invitation.getStatus());
        AfterCommit.run(() -> broadcastService.sendToUser(user.getEmail(), DESTINATION, event));
    }

    /** Davet bildirimini okundu say: yanıtlanan ya da geri çekilen davet artık eylem beklemiyor. */
    public void markInviteHandled(Long userId, Long invitationId) {
        repository.findByUserIdAndInvitationIdAndType(userId, invitationId, NotificationType.BOARD_INVITE)
                .filter(n -> n.getReadAt() == null)
                .ifPresent(n -> n.setReadAt(LocalDateTime.now()));
    }

    /** Zil menüsü: son bildirimler + okunmamış sayısı. */
    public NotificationListResponse list(String email, int limit) {
        User user = userByEmail(email);
        backfillInvites(user);

        int safeLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        List<Notification> rows = repository.findByUserIdOrderByIdDesc(user.getId(), PageRequest.of(0, safeLimit));

        // Satır başına sorgu atmamak için davet durumları ve fotoğraflar toplu çekilir.
        Map<Long, InvitationStatus> statuses = invitationRepository.findAllById(
                        rows.stream().map(Notification::getInvitationId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(BoardInvitation::getId, BoardInvitation::getStatus));
        Map<Long, User> actors = userRepository.findAllById(
                        rows.stream().map(Notification::getActorId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));

        List<NotificationResponse> items = rows.stream()
                .map(n -> {
                    User actor = n.getActorId() == null ? null : actors.get(n.getActorId());
                    return NotificationResponse.of(n, actor == null ? null : AvatarUrls.of(actor.getAvatarKey()),
                            n.getInvitationId() == null ? null : statuses.get(n.getInvitationId()));
                })
                .toList();

        return new NotificationListResponse(repository.countByUserIdAndReadAtIsNull(user.getId()), items);
    }

    public void markRead(Long id, String email) {
        User user = userByEmail(email);
        Notification n = repository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Bildirim", "id", id));
        if (n.getReadAt() == null) {
            n.setReadAt(LocalDateTime.now());
        }
    }

    public void markAllRead(String email) {
        repository.markAllRead(userByEmail(email).getId(), LocalDateTime.now());
    }

    /**
     * Hesap açılmadan ÖNCE gönderilmiş davetlerin bildirimlerini üretir.
     *
     * Davet e-postaya bağlı; davet anında o adresin bir hesabı yoksa bildirim
     * yazılacak bir kullanıcı da yoktu. Kişi sonradan kayıt olup ilk kez zil
     * menüsünü açtığında eksik bildirimler burada tamamlanır. Tekrar üretmeyi
     * hem bu kontrol hem veritabanındaki tekil index (V14) engeller.
     */
    private void backfillInvites(User user) {
        for (BoardInvitation inv : invitationRepository.findByEmailAndStatusOrderByIdDesc(
                user.getEmail().toLowerCase(), InvitationStatus.PENDING)) {
            if (repository.existsByUserIdAndInvitationIdAndType(user.getId(), inv.getId(), NotificationType.BOARD_INVITE)) {
                continue;
            }
            Board board = boardRepository.findById(inv.getBoardId()).orElse(null);
            User inviter = inv.getInvitedBy() == null ? null : userRepository.findById(inv.getInvitedBy()).orElse(null);
            repository.save(build(user, NotificationType.BOARD_INVITE, inviter, board, inv));
        }
    }

    private Notification build(User recipient, NotificationType type, User actor, Board board,
                               BoardInvitation invitation) {
        return Notification.builder()
                .userId(recipient.getId())
                .type(type)
                .actorId(actor == null ? null : actor.getId())
                .actorName(actor == null ? null : fullName(actor))
                .boardId(board == null ? null : board.getId())
                .boardName(board == null ? null : board.getName())
                .invitationId(invitation == null ? null : invitation.getId())
                .role(invitation == null ? null : invitation.getRole())
                .createdAt(LocalDateTime.now())
                .build();
    }

    private User userByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı", "email", email));
    }

    public static String fullName(User user) {
        return (user.getFirstName() + " " + user.getLastName()).trim();
    }
}
