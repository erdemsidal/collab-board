package com.collabboard.chathook;

import com.collabboard.board.BoardAccessService;
import com.collabboard.board.BoardRepository;
import com.collabboard.board.entity.Board;
import com.collabboard.chathook.dto.ChatHookListResponse;
import com.collabboard.chathook.dto.ChatHookRequest;
import com.collabboard.chathook.dto.ChatHookResponse;
import com.collabboard.chathook.dto.ChatHookTestRequest;
import com.collabboard.chathook.dto.ChatHookTestResponse;
import com.collabboard.common.exception.BadRequestException;
import com.collabboard.common.exception.ResourceNotFoundException;
import com.collabboard.i18n.AppLocale;
import com.collabboard.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Panonun kanal bağlantılarını yönetmek (ADR 0023).
 *
 * Yalnızca panonun yöneticisi (etkin rolü OWNER; çalışma alanı sahibi ve
 * yöneticisi dahil) görür, ekler, değiştirir, dener ve siler. Kural
 * BoardAccessService'ten gelir. Üye olmayan ve editör/izleyici 403 alır.
 *
 * Şifreleme anahtarları tanımlı değilse bütün uçlar 503 ve açık bir mesaj döner
 * (yetki kontrolünden SONRA: yönetici olmayan sunucunun ayarını öğrenmez).
 */
@Service
@Transactional
public class ChatHookService {

    private static final Logger log = LoggerFactory.getLogger(ChatHookService.class);

    /** Bir panoya en fazla bu kadar kanal: her olay her bağlantıya ayrı bir istek. */
    public static final int MAX_HOOKS_PER_BOARD = 5;
    static final int MAX_LABEL_LENGTH = 60;

    private final ChatHookRepository repository;
    private final BoardAccessService accessService;
    private final BoardRepository boardRepository;
    private final UserService userService;
    private final WebhookUrlCipher cipher;
    private final WebhookUrlPolicy policy;
    private final ChatDelivery delivery;
    private final ChatMessages chatMessages;

    public ChatHookService(ChatHookRepository repository, BoardAccessService accessService,
                           BoardRepository boardRepository, UserService userService, WebhookUrlCipher cipher,
                           WebhookUrlPolicy policy, ChatDelivery delivery, ChatMessages chatMessages) {
        this.repository = repository;
        this.accessService = accessService;
        this.boardRepository = boardRepository;
        this.userService = userService;
        this.cipher = cipher;
        this.policy = policy;
        this.delivery = delivery;
        this.chatMessages = chatMessages;
    }

    @Transactional(readOnly = true)
    public ChatHookListResponse list(Long boardId, String actorEmail) {
        requireAdmin(boardId, actorEmail);
        return new ChatHookListResponse(MAX_HOOKS_PER_BOARD,
                repository.findByBoardIdOrderByIdAsc(boardId).stream().map(ChatHookResponse::of).toList());
    }

    /**
     * @param requestLocale isteğin dili; mesaj dili seçilmediyse bağlantının dili bu olur
     */
    public ChatHookResponse create(Long boardId, ChatHookRequest request, String actorEmail, String requestLocale) {
        requireAdmin(boardId, actorEmail);
        if (repository.countByBoardId(boardId) >= MAX_HOOKS_PER_BOARD) {
            throw new BadRequestException("error.chatHook.limit", MAX_HOOKS_PER_BOARD);
        }
        ChatProvider provider = requireProvider(request.provider());
        String url = allowedUrl(provider, request.url());

        ChatHook saved = repository.save(ChatHook.builder()
                .boardId(boardId)
                .provider(provider)
                .urlEncrypted(cipher.encrypt(url))
                .urlHint(hintOf(url))
                .label(validLabel(request.label()))
                .events(validEvents(request.events()))
                .locale(validLocale(request.locale(), requestLocale))
                .createdBy(userService.getUserByEmail(actorEmail).getId())
                .createdAt(LocalDateTime.now())
                .build());
        log.info("Kanal bağlantısı eklendi: boardId={}, hook {}, {}", boardId, saved.getId(), provider);
        return ChatHookResponse.of(saved);
    }

    /** Yalnızca gönderilen alanlar değişir (ChatHookRequest). */
    public ChatHookResponse update(Long boardId, Long hookId, ChatHookRequest request, String actorEmail) {
        requireAdmin(boardId, actorEmail);
        ChatHook hook = hookOnBoard(boardId, hookId);

        boolean newUrl = request.url() != null && !request.url().isBlank();
        ChatProvider provider = request.provider() == null ? hook.getProvider() : request.provider();
        if (provider != hook.getProvider() && !newUrl) {
            // Slack'in adresi Teams'e yazmaz: uygulama değişince adres de değişmeli.
            throw new BadRequestException("error.chatHook.urlRequired");
        }
        if (newUrl) {
            String url = allowedUrl(provider, request.url());
            hook.setProvider(provider);
            hook.setUrlEncrypted(cipher.encrypt(url));
            hook.setUrlHint(hintOf(url));
            // Yeni adres yeni bir başlangıç: eski hatalar ona ait değil.
            hook.setFailureCount(0);
            hook.setDisabledReason(null);
        }
        if (request.label() != null) {
            hook.setLabel(validLabel(request.label()));
        }
        if (request.events() != null) {
            hook.setEvents(validEvents(request.events()));
        }
        if (request.locale() != null) {
            hook.setLocale(validLocale(request.locale(), null));
        }
        if (Boolean.TRUE.equals(request.active())) {
            hook.setDisabledReason(null);
            hook.setFailureCount(0);
        }
        return ChatHookResponse.of(repository.saveAndFlush(hook));
    }

    public void delete(Long boardId, Long hookId, String actorEmail) {
        requireAdmin(boardId, actorEmail);
        repository.delete(hookOnBoard(boardId, hookId));
        log.info("Kanal bağlantısı silindi: boardId={}, hook {}", boardId, hookId);
    }

    /**
     * Kaydetmeden önce formdaki adresi dener. İşlem açmaz: kanal 5 saniye
     * düşünebilir, o sırada veritabanı bağlantısı tutulmasın.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ChatHookTestResponse testUrl(Long boardId, ChatHookTestRequest request, String actorEmail,
                                        String requestLocale) {
        requireAdmin(boardId, actorEmail);
        ChatProvider provider = requireProvider(request.provider());
        String url = allowedUrl(provider, request.url());
        String json = chatMessages.test(provider, validLocale(request.locale(), requestLocale),
                boardName(boardId), boardId);
        return response(delivery.tryUrl(provider, url, json));
    }

    /** Kayıtlı bağlantıyı dener; bağlantının durumunu değiştirmez. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ChatHookTestResponse testSaved(Long boardId, Long hookId, String actorEmail) {
        requireAdmin(boardId, actorEmail);
        ChatHook hook = hookOnBoard(boardId, hookId);
        String json = chatMessages.test(hook.getProvider(), hook.getLocale(), boardName(boardId), boardId);
        return response(delivery.tryOnce(hook, json));
    }

    // ── yardımcılar ─────────────────────────────────────────────────

    private void requireAdmin(Long boardId, String actorEmail) {
        accessService.requireOwner(boardId, actorEmail);
        cipher.requireAvailable();
    }

    private ChatHook hookOnBoard(Long boardId, Long hookId) {
        // Başka panonun bağlantısı ile olmayan bağlantı aynı cevabı alır.
        return repository.findByIdAndBoardId(hookId, boardId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound.chatHook", hookId));
    }

    private String boardName(Long boardId) {
        return boardRepository.findById(boardId).map(Board::getName).orElse("");
    }

    private static ChatHookTestResponse response(WebhookSender.Result result) {
        return new ChatHookTestResponse(result.ok(), result.outcome(), result.status());
    }

    private static ChatProvider requireProvider(ChatProvider provider) {
        if (provider == null) {
            throw new BadRequestException("error.chatHook.providerRequired");
        }
        return provider;
    }

    private String allowedUrl(ChatProvider provider, String raw) {
        String url = raw == null ? "" : raw.strip();
        if (url.isEmpty()) {
            throw new BadRequestException("error.chatHook.urlRequired");
        }
        policy.require(provider, url);
        return url;
    }

    /** Arayüzde gösterilen tek parça: adresin son 4 karakteri. */
    static String hintOf(String url) {
        return url.length() <= 4 ? url : url.substring(url.length() - 4);
    }

    private static String validLabel(String raw) {
        String label = raw == null ? "" : raw.strip();
        if (label.length() > MAX_LABEL_LENGTH) {
            throw new BadRequestException("error.chatHook.labelTooLong", MAX_LABEL_LENGTH);
        }
        return label.isEmpty() ? null : label;
    }

    private static Set<ChatEvent> validEvents(List<ChatEvent> requested) {
        if (requested == null || requested.stream().noneMatch(Objects::nonNull)) {
            throw new BadRequestException("error.chatHook.eventsRequired");
        }
        Set<ChatEvent> events = EnumSet.noneOf(ChatEvent.class);
        requested.stream().filter(Objects::nonNull).forEach(events::add);
        return events;
    }

    private static String validLocale(String requested, String fallback) {
        if (requested == null || requested.isBlank()) {
            return AppLocale.isSupported(fallback) ? fallback : AppLocale.tag(AppLocale.DEFAULT);
        }
        if (!AppLocale.isSupported(requested)) {
            throw new BadRequestException("error.locale.unsupported", AppLocale.supportedTags());
        }
        return requested;
    }
}
