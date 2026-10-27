package com.collabboard.chathook;

import com.collabboard.board.BoardAccessService;
import com.collabboard.board.BoardMemberRepository;
import com.collabboard.board.BoardRepository;
import com.collabboard.board.entity.Board;
import com.collabboard.board.entity.BoardMember;
import com.collabboard.board.entity.BoardRole;
import com.collabboard.notification.NotificationService;
import com.collabboard.user.UserRepository;
import com.collabboard.workspace.WorkspaceMemberRepository;
import com.collabboard.workspace.entity.WorkspaceMember;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Kayıtlı bir bağlantıya mesajı götürür ve sonucu bağlantıya yazar.
 *
 * Bilerek işlem (transaction) AÇMAZ: HTTP isteği 5 saniye sürebilir, o sırada
 * bir veritabanı bağlantısını tutmanın anlamı yok. Sonuçlar depodaki tek satırlık
 * güncellemelerle yazılır (ChatHookRepository), her biri kendi kısa işleminde.
 */
@Component
public class ChatDelivery {

    private static final Logger log = LoggerFactory.getLogger(ChatDelivery.class);

    /** Üst üste bu kadar geçici hatadan sonra bağlantı durur. */
    static final int FAILURE_LIMIT = 5;

    private final ChatHookRepository repository;
    private final WebhookUrlCipher cipher;
    private final WebhookUrlPolicy policy;
    private final WebhookSender sender;
    private final BoardRepository boardRepository;
    private final BoardMemberRepository boardMembers;
    private final WorkspaceMemberRepository workspaceMembers;
    private final BoardAccessService accessService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public ChatDelivery(ChatHookRepository repository, WebhookUrlCipher cipher, WebhookUrlPolicy policy,
                        WebhookSender sender, BoardRepository boardRepository, BoardMemberRepository boardMembers,
                        WorkspaceMemberRepository workspaceMembers, BoardAccessService accessService,
                        UserRepository userRepository, NotificationService notificationService) {
        this.repository = repository;
        this.cipher = cipher;
        this.policy = policy;
        this.sender = sender;
        this.boardRepository = boardRepository;
        this.boardMembers = boardMembers;
        this.workspaceMembers = workspaceMembers;
        this.accessService = accessService;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    /**
     * Bağlantıya gerçek bir olay mesajı gönderir ve sonucu kaydeder.
     * Hata fırlatmaz: kanal ne derse desin çağıranın (arka plan) akışı sürer.
     */
    public WebhookSender.Result deliver(ChatHook hook, String json) {
        String ref = "hook " + hook.getId();
        URI url = savedUrl(hook);
        if (url == null) {
            return new WebhookSender.Result(DeliveryOutcome.REJECTED, null);
        }
        WebhookSender.Result result = sender.post(url, json, ref);
        record(hook, result.outcome());
        return result;
    }

    /**
     * Kayıtlı bağlantıya deneme mesajı: sonucu bağlantının durumuna YAZMAZ.
     * Durmuş bir bağlantı da denenebilir ("düzeldi mi?"); açmak yine "Yeniden aç" ile.
     */
    public WebhookSender.Result tryOnce(ChatHook hook, String json) {
        String url;
        try {
            url = cipher.decrypt(hook.getUrlEncrypted());
        } catch (IllegalStateException e) {
            return new WebhookSender.Result(DeliveryOutcome.REJECTED, null);
        }
        return sender.post(policy.require(hook.getProvider(), url), json, "deneme, hook " + hook.getId());
    }

    /** Formdaki (kaydedilmemiş) adrese deneme mesajı. Adres izin listesine uymuyorsa 400. */
    public WebhookSender.Result tryUrl(ChatProvider provider, String url, String json) {
        return sender.post(policy.require(provider, url), json, "deneme, kaydedilmemiş");
    }

    /**
     * Kayıtlı adres: çözülür ve gönderimden hemen önce izin listesine yeniden
     * sorulur. Çözülemezse ya da artık izinli değilse bağlantı durur, null döner.
     */
    private URI savedUrl(ChatHook hook) {
        String url;
        try {
            url = cipher.decrypt(hook.getUrlEncrypted());
        } catch (IllegalStateException e) {
            log.warn("Kanal bağlantısı çözülemedi, durduruluyor: hook {}", hook.getId());
            disable(hook, DisabledReason.UNREADABLE);
            return null;
        }
        if (!policy.allows(hook.getProvider(), url)) {
            log.warn("Kanal bağlantısının adresi izin listesine artık uymuyor, durduruluyor: hook {}", hook.getId());
            disable(hook, DisabledReason.BLOCKED);
            return null;
        }
        return URI.create(url);
    }

    private void record(ChatHook hook, DeliveryOutcome outcome) {
        if (outcome == DeliveryOutcome.OK) {
            repository.markSent(hook.getId(), LocalDateTime.now());
            return;
        }
        DisabledReason permanent = outcome.permanentReason();
        if (permanent != null) {
            disable(hook, permanent);
            return;
        }
        repository.countFailure(hook.getId());
        int failures = repository.failureCount(hook.getId()).orElse(0);
        if (failures >= FAILURE_LIMIT) {
            disable(hook, DisabledReason.FAILING);
        }
    }

    /**
     * Bağlantıyı durdurur ve panonun yöneticilerine uygulama içinde haber verir.
     * Aynı anda iki gönderim başarısız olsa da haber bir kez gider (ChatHookRepository.disable).
     */
    void disable(ChatHook hook, DisabledReason reason) {
        if (repository.disable(hook.getId(), reason) == 0) {
            return;   // zaten durmuş ya da silinmiş
        }
        log.info("Kanal bağlantısı durduruldu: hook {}, neden={}", hook.getId(), reason);
        Board board = boardRepository.findById(hook.getBoardId()).orElse(null);
        if (board == null) {
            return;
        }
        String name = hook.getLabel() == null || hook.getLabel().isBlank()
                ? hook.getProvider().displayName()
                : hook.getProvider().displayName() + " · " + hook.getLabel();
        userRepository.findAllById(adminsOf(board)).forEach(admin ->
                notificationService.notifyAboutChatHook(admin, board, name));
    }

    /**
     * Panonun yöneticileri: etkin rolü OWNER olanlar. Panoya özel üyeler ve
     * çalışma alanının üyeleri aday; kararı kuralın tek kapısı (BoardAccessService) verir.
     */
    private Set<Long> adminsOf(Board board) {
        Set<Long> candidates = new LinkedHashSet<>();
        boardMembers.findByBoardId(board.getId()).stream().map(BoardMember::getUserId).forEach(candidates::add);
        workspaceMembers.findByWorkspaceId(board.getWorkspaceId()).stream()
                .map(WorkspaceMember::getUserId).forEach(candidates::add);
        candidates.removeIf(id -> accessService.roleOf(board.getId(), id).filter(r -> r == BoardRole.OWNER).isEmpty());
        return candidates;
    }
}
