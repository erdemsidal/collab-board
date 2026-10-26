package com.collabboard.comment;

import com.collabboard.audit.ActivityService;
import com.collabboard.board.BoardAccessService;
import com.collabboard.board.CardActivityEvent;
import com.collabboard.board.CardRepository;
import com.collabboard.board.entity.Board;
import com.collabboard.board.entity.BoardRole;
import com.collabboard.board.entity.Card;
import com.collabboard.comment.dto.CommentEvent;
import com.collabboard.comment.dto.CommentRequest;
import com.collabboard.comment.dto.CommentResponse;
import com.collabboard.common.exception.BadRequestException;
import com.collabboard.common.exception.ForbiddenException;
import com.collabboard.common.exception.ResourceNotFoundException;
import com.collabboard.common.tx.AfterCommit;
import com.collabboard.i18n.AppLocale;
import com.collabboard.mail.MailService;
import com.collabboard.mail.MentionMailTemplate;
import com.collabboard.notification.NotificationService;
import com.collabboard.notification.NotificationType;
import com.collabboard.realtime.BroadcastService;
import com.collabboard.user.AvatarUrls;
import com.collabboard.user.UserRepository;
import com.collabboard.user.UserService;
import com.collabboard.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Kart yorumları ve @bahsetme (ADR 0019).
 *
 * Yetki tek kapıdan (BoardAccessService) gelir: panonun her üyesi, izleyici
 * dahil, yorumları okur ve yazar. Yorum panoyu değiştirmez; Trello'da da
 * yalnızca izleyen biri kartta soru sorabilir.
 *
 * Yorum kart operasyonlarından ayrı yaşar: kartın sürümünü artırmaz, zaman
 * yolculuğunda oynatılmaz, akış ölçümüne girmez. Geçmişe yalnızca "Son
 * hareketler" listesinde görünsün diye bilgi olarak yazılır.
 */
@Service
@Transactional
public class CommentService {

    /** Olay kaydındaki tür. Zaman yolculuğu ve akış ölçümü bu türü oynatmaz. */
    public static final String ACTIVITY_TYPE = "ADD_COMMENT";

    static final int MAX_LENGTH = 5000;
    static final int MAX_MENTIONS = 20;

    private static final Logger log = LoggerFactory.getLogger(CommentService.class);

    private final CommentRepository commentRepository;
    private final CardRepository cardRepository;
    private final BoardAccessService accessService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final ActivityService activityService;
    private final NotificationService notificationService;
    private final BroadcastService broadcastService;
    private final MailService mailService;
    private final MentionMailTemplate mailTemplate;
    private final CommentRateLimiter rateLimiter;
    private final ApplicationEventPublisher events;
    private final String baseUrl;

    public CommentService(CommentRepository commentRepository, CardRepository cardRepository,
                          BoardAccessService accessService, UserService userService, UserRepository userRepository,
                          ActivityService activityService, NotificationService notificationService,
                          BroadcastService broadcastService, MailService mailService,
                          MentionMailTemplate mailTemplate, CommentRateLimiter rateLimiter,
                          ApplicationEventPublisher events, @Value("${app.base-url}") String baseUrl) {
        this.commentRepository = commentRepository;
        this.cardRepository = cardRepository;
        this.accessService = accessService;
        this.userService = userService;
        this.userRepository = userRepository;
        this.activityService = activityService;
        this.notificationService = notificationService;
        this.broadcastService = broadcastService;
        this.mailService = mailService;
        this.mailTemplate = mailTemplate;
        this.rateLimiter = rateLimiter;
        this.events = events;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** Olay kaydına yazılan veri: hangi karta, hangi yorum. */
    public record ActivityPayload(Long cardId, Long commentId) {
    }

    /** Kartın yorumları, eskiden yeniye. */
    @Transactional(readOnly = true)
    public List<CommentResponse> list(Long boardId, Long cardId, String actorEmail) {
        accessService.requireMember(boardId, actorEmail);
        Card card = cardOnBoard(boardId, cardId);
        List<Comment> comments = commentRepository.findByCardIdWithMentions(card.getId());
        Map<Long, UserRepository.PersonRow> people = people(comments);
        return comments.stream().map(c -> toResponse(c, people)).toList();
    }

    public CommentResponse create(Long boardId, Long cardId, CommentRequest request, String actorEmail) {
        accessService.requireMember(boardId, actorEmail);
        Card card = cardOnBoard(boardId, cardId);
        User author = userService.getUserByEmail(actorEmail);
        String body = validBody(request.body());
        Set<Long> mentions = validMentions(boardId, author.getId(), request.mentions());
        rateLimiter.consume(author.getId());

        Comment saved = commentRepository.save(Comment.builder()
                .cardId(card.getId())
                .authorId(author.getId())
                .body(body)
                .mentionIds(new LinkedHashSet<>(mentions))
                .createdAt(LocalDateTime.now())
                .build());

        Board board = card.getColumn().getBoard();
        activityService.record(boardId, actorEmail, ACTIVITY_TYPE,
                "'%s' kartına yorum yazdı".formatted(card.getTitle()),
                new ActivityPayload(card.getId(), saved.getId()), Map.of("card", card.getTitle()));

        notifyMentioned(mentions, author, board, card, saved);
        notifyAssignee(boardId, card, author, mentions, board, saved);
        // Kanal bildirimleri (ADR 0023). Yorumun metni gitmez, yalnızca "yorum yazıldı".
        events.publishEvent(CardActivityEvent.commented(boardId, actorEmail, card.getId(), card.getTitle(),
                card.getColumn().getId(), card.getColumn().getName()));

        CommentResponse response = toResponse(saved, people(List.of(saved)));
        long count = commentRepository.countByCardId(card.getId());
        AfterCommit.run(() -> broadcastService.broadcast(topic(boardId), CommentEvent.added(response, count)));
        log.info("Yorum yazıldı: commentId={}, cardId={}, anılan={}", saved.getId(), card.getId(), mentions.size());
        return response;
    }

    /**
     * Yorumu düzenler (yalnızca yazan). Anılanlar yeni listeyle değişir; bildirim
     * ve posta yalnızca bu yorumda İLK KEZ anılan kişilere gider.
     */
    public CommentResponse update(Long boardId, Long cardId, Long commentId, CommentRequest request,
                                  String actorEmail) {
        accessService.requireMember(boardId, actorEmail);
        Card card = cardOnBoard(boardId, cardId);
        Comment comment = commentOnCard(commentId, card.getId());
        User actor = userService.getUserByEmail(actorEmail);
        if (!actor.getId().equals(comment.getAuthorId())) {
            throw new ForbiddenException("error.comment.editOwnOnly");
        }
        String body = validBody(request.body());
        Set<Long> mentions = validMentions(boardId, actor.getId(), request.mentions());

        // Değişiklik yoksa "düzenlendi" damgası basılmaz, kimseye bir şey gitmez.
        if (body.equals(comment.getBody()) && mentions.equals(comment.getMentionIds())) {
            return toResponse(comment, people(List.of(comment)));
        }
        rateLimiter.consume(actor.getId());

        Set<Long> added = new LinkedHashSet<>(mentions);
        added.removeAll(comment.getMentionIds());
        comment.setBody(body);
        comment.getMentionIds().retainAll(mentions);
        comment.getMentionIds().addAll(mentions);
        comment.setEditedAt(LocalDateTime.now());
        commentRepository.flush();

        notifyMentioned(added, actor, card.getColumn().getBoard(), card, comment);

        CommentResponse response = toResponse(comment, people(List.of(comment)));
        long count = commentRepository.countByCardId(card.getId());
        AfterCommit.run(() -> broadcastService.broadcast(topic(boardId), CommentEvent.edited(response, count)));
        return response;
    }

    /** Yorumu siler: yazan ya da panonun yöneticisi (OWNER). */
    public void delete(Long boardId, Long cardId, Long commentId, String actorEmail) {
        BoardRole role = accessService.requireMember(boardId, actorEmail);
        Card card = cardOnBoard(boardId, cardId);
        Comment comment = commentOnCard(commentId, card.getId());
        Long actorId = userService.getUserByEmail(actorEmail).getId();
        if (!actorId.equals(comment.getAuthorId()) && role != BoardRole.OWNER) {
            throw new ForbiddenException("error.comment.deleteForbidden");
        }

        commentRepository.delete(comment);
        commentRepository.flush();
        long count = commentRepository.countByCardId(card.getId());
        AfterCommit.run(() -> broadcastService.broadcast(topic(boardId),
                CommentEvent.deleted(card.getId(), commentId, count)));
        log.info("Yorum silindi: commentId={}, cardId={}", commentId, card.getId());
    }

    // ── bildirimler ─────────────────────────────────────────────────

    /**
     * Anılan kişilere bildirim ve posta. Aynı yorumda daha önce anılmış kişi
     * atlanır: düzenlemede çıkarılıp yeniden eklenen biri ikinci postayı almaz.
     * Posta işlem kaydedildikten sonra gider; gidemezse yalnızca loglanır (MailService).
     * Her alıcıya kendi dilinde (ADR 0020).
     */
    private void notifyMentioned(Collection<Long> userIds, User author, Board board, Card card, Comment comment) {
        if (userIds.isEmpty()) {
            return;
        }
        String authorName = NotificationService.fullName(author);
        String link = baseUrl + "/?board=" + board.getId() + "&card=" + card.getId();

        for (User recipient : userRepository.findAllById(userIds)) {
            if (notificationService.wasMentioned(recipient.getId(), comment.getId())) {
                continue;
            }
            notificationService.notifyAboutCard(recipient, NotificationType.COMMENT_MENTION, author, board,
                    card, comment.getId());
            Locale locale = AppLocale.of(recipient);
            String to = recipient.getEmail();
            String subject = mailTemplate.subject(locale, authorName, card.getTitle());
            String html = mailTemplate.html(locale, authorName, board.getName(), card.getTitle(), comment.getBody(), link);
            String text = mailTemplate.text(locale, authorName, board.getName(), card.getTitle(), comment.getBody(), link);
            AfterCommit.run(() -> mailService.send(to, subject, html, text));
        }
    }

    /**
     * Karta atanmış kişiye uygulama içinde haber (posta yok). Yorumu kendisi
     * yazdıysa ya da yorumda zaten anıldıysa (bahsetme bildirimi aldı) atlanır.
     * Panodan çıkarılmış kişiye, artık göremediği bir kart için bildirim gitmez.
     */
    private void notifyAssignee(Long boardId, Card card, User author, Set<Long> mentions, Board board,
                                Comment comment) {
        Long assigneeId = card.getAssigneeId();
        if (assigneeId == null || assigneeId.equals(author.getId()) || mentions.contains(assigneeId)
                || accessService.roleOf(boardId, assigneeId).isEmpty()) {
            return;
        }
        userRepository.findById(assigneeId).ifPresent(assignee -> notificationService.notifyAboutCard(
                assignee, NotificationType.CARD_COMMENT, author, board, card, comment.getId()));
    }

    // ── doğrulama ───────────────────────────────────────────────────

    private String validBody(String raw) {
        String body = raw == null ? "" : raw.strip();
        if (body.isEmpty()) {
            throw new BadRequestException("error.comment.empty");
        }
        if (body.length() > MAX_LENGTH) {
            throw new BadRequestException("error.comment.tooLong", String.valueOf(MAX_LENGTH));
        }
        return body;
    }

    /**
     * Anılabilecek kişiler: panoya erişebilenler, yazanın kendisi hariç. Geri
     * kalanı hata vermeden düşülür; istemci eski bir üye listesiyle yazmış
     * olabilir, yorumun tamamını reddetmek için sebep değil. Kural yeniden
     * yazılmaz, BoardAccessService'e sorulur (en fazla 20 kişi).
     */
    private Set<Long> validMentions(Long boardId, Long authorId, List<Long> requested) {
        Set<Long> ids = requested == null ? new LinkedHashSet<>()
                : requested.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.size() > MAX_MENTIONS) {
            throw new BadRequestException("error.comment.tooManyMentions", String.valueOf(MAX_MENTIONS));
        }
        ids.remove(authorId);
        ids.removeIf(id -> accessService.roleOf(boardId, id).isEmpty());
        return ids;
    }

    /**
     * Kart bu panoda mı? Değilse 404: üyenin bilmediği bir panodaki kartın
     * varlığını doğrulamamak için "başka panoda" ile "yok" aynı cevabı alır.
     * Üye olmayan buraya hiç gelmez, önce 403 alır.
     */
    private Card cardOnBoard(Long boardId, Long cardId) {
        return cardRepository.findById(cardId)
                .filter(card -> card.getColumn().getBoard().getId().equals(boardId))
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound.card", cardId));
    }

    private Comment commentOnCard(Long commentId, Long cardId) {
        return commentRepository.findByIdAndCardId(commentId, cardId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound.comment", commentId));
    }

    // ── yanıt ───────────────────────────────────────────────────────

    /**
     * Yazarlar ve anılanlar, tek sorguda. Hafif satırlar (findPeople): tam
     * kullanıcı nesnesi rollerini de çeker, kişi başına bir sorgu daha demek.
     */
    private Map<Long, UserRepository.PersonRow> people(List<Comment> comments) {
        Set<Long> ids = new HashSet<>();
        for (Comment c : comments) {
            if (c.getAuthorId() != null) {
                ids.add(c.getAuthorId());
            }
            ids.addAll(c.getMentionIds());
        }
        return ids.isEmpty() ? Map.of()
                : userRepository.findPeople(ids).stream()
                        .collect(Collectors.toMap(UserRepository.PersonRow::getId, Function.identity()));
    }

    private CommentResponse toResponse(Comment c, Map<Long, UserRepository.PersonRow> people) {
        UserRepository.PersonRow author = c.getAuthorId() == null ? null : people.get(c.getAuthorId());
        List<CommentResponse.Mention> mentions = new ArrayList<>();
        for (Long id : c.getMentionIds()) {
            UserRepository.PersonRow p = people.get(id);
            if (p != null) {
                mentions.add(new CommentResponse.Mention(p.getId(), name(p)));
            }
        }
        mentions.sort(Comparator.comparing(CommentResponse.Mention::name));
        return new CommentResponse(c.getId(), c.getCardId(),
                author == null ? null
                        : new CommentResponse.Person(author.getId(), name(author), AvatarUrls.of(author.getAvatarKey())),
                c.getBody(), mentions, c.getCreatedAt(), c.getEditedAt());
    }

    private static String name(UserRepository.PersonRow p) {
        return (p.getFirstName() + " " + p.getLastName()).trim();
    }

    private static String topic(Long boardId) {
        return "/topic/board." + boardId;
    }
}
