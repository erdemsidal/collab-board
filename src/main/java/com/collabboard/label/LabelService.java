package com.collabboard.label;

import com.collabboard.audit.ActivityService;
import com.collabboard.board.BoardAccessService;
import com.collabboard.board.OperationScopeGuard;
import com.collabboard.board.entity.Card;
import com.collabboard.common.exception.BadRequestException;
import com.collabboard.common.exception.ConflictException;
import com.collabboard.common.exception.ResourceNotFoundException;
import com.collabboard.common.tx.AfterCommit;
import com.collabboard.label.dto.LabelEvent;
import com.collabboard.label.dto.LabelRequest;
import com.collabboard.label.dto.LabelResponse;
import com.collabboard.realtime.BroadcastService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Panonun etiketleri ve kartlara takılmaları (ADR 0022).
 *
 * Yetki tek kapıdan (BoardAccessService): etiket takımını değiştirmek ve karta
 * etiket takmak editör ve yöneticinin işi; izleyici yalnızca görür. Hedefin
 * (kart, etiket) adresteki panoya ait olduğu ayrıca denetlenir: başka panonun
 * etiketi ya da kartı "bulunamadı" (404) alır, "yetkin yok" değil.
 *
 * Etiket kart operasyonu değildir: kartın sürümünü artırmaz. Karta takmak ve
 * çıkarmak bir kümeye ekleyip çıkarmak olduğu için aynı işlemi iki kez yapmak
 * zararsızdır (idempotent); aynı anda gelen iki istek birbirini bozmaz.
 */
@Service
@Transactional
public class LabelService {

    /** Olay kaydındaki türler. Üçü de zaman yolculuğunda oynatılır (BoardHistoryService). */
    public static final String ADD_TO_CARD = "ADD_LABEL";
    public static final String REMOVE_FROM_CARD = "REMOVE_LABEL";
    public static final String DELETE = "DELETE_LABEL";

    static final int MAX_LABELS = 30;
    static final int MAX_NAME_LENGTH = 40;
    /** Geçmiş açıklamasında kart başlığının en fazla bu kadarı (sütun 500 karakter). */
    private static final int DESCRIPTION_TITLE = 200;

    private static final Logger log = LoggerFactory.getLogger(LabelService.class);

    private final LabelRepository labelRepository;
    private final BoardAccessService accessService;
    private final OperationScopeGuard scopeGuard;
    private final ActivityService activityService;
    private final BroadcastService broadcastService;

    public LabelService(LabelRepository labelRepository, BoardAccessService accessService,
                        OperationScopeGuard scopeGuard, ActivityService activityService,
                        BroadcastService broadcastService) {
        this.labelRepository = labelRepository;
        this.accessService = accessService;
        this.scopeGuard = scopeGuard;
        this.activityService = activityService;
        this.broadcastService = broadcastService;
    }

    /** Karta takma ve çıkarma kaydının verisi: zaman yolculuğu bunu oynatır. */
    public record CardLabelPayload(Long cardId, Long labelId) {
    }

    /**
     * Silme kaydının verisi. Ad ve renk de yazılır: etiket artık yok ama geçmişte
     * o etiketi taşıyan kartlar onu yine göstermeli (silinen kolon gibi, ADR 0008).
     */
    public record DeletedLabelPayload(Long labelId, String name, LabelColor color, long cards) {
    }

    // ── panonun fotoğrafı için okumalar ─────────────────────────────

    @Transactional(readOnly = true)
    public List<LabelResponse> labelsOf(Long boardId) {
        return labelRepository.findByBoardIdOrderByIdAsc(boardId).stream().map(LabelResponse::of).toList();
    }

    /** Kart id → takılı etiketlerin id'leri; tek sorgu. Etiketi olmayan kart haritada yok. */
    @Transactional(readOnly = true)
    public Map<Long, List<Long>> labelIdsByCard(Long boardId) {
        Map<Long, List<Long>> out = new LinkedHashMap<>();
        for (LabelRepository.CardLabelRow row : labelRepository.cardLabels(boardId)) {
            out.computeIfAbsent(row.getCardId(), id -> new ArrayList<>()).add(row.getLabelId());
        }
        return out;
    }

    // ── etiket takımı ───────────────────────────────────────────────

    public LabelResponse create(Long boardId, LabelRequest request, String actorEmail) {
        accessService.requireEditor(boardId, actorEmail);
        LabelColor color = validColor(request.color());
        String name = validName(request.name());
        lockBoard(boardId);
        if (labelRepository.countByBoardId(boardId) >= MAX_LABELS) {
            throw new BadRequestException("error.label.limit", String.valueOf(MAX_LABELS));
        }
        requireFreeName(boardId, name, null);

        Label saved = save(Label.builder()
                .boardId(boardId)
                .name(name)
                .color(color)
                .createdAt(LocalDateTime.now())
                .build());
        log.info("Etiket oluşturuldu: id={}, boardId={}", saved.getId(), boardId);

        LabelResponse response = LabelResponse.of(saved);
        broadcast(boardId, LabelEvent.created(response));
        return response;
    }

    /**
     * Ad ve rengi değiştirir. Olay kaydına yazılmaz: geçmiş "kim neyi yaptı"
     * listesi, bir etiketin rengini düzeltmek gibi ayarlarla dolmasın.
     */
    public LabelResponse update(Long boardId, Long labelId, LabelRequest request, String actorEmail) {
        accessService.requireEditor(boardId, actorEmail);
        LabelColor color = validColor(request.color());
        String name = validName(request.name());
        lockBoard(boardId);
        Label label = labelOnBoard(boardId, labelId);
        if (Objects.equals(name, label.getName()) && color == label.getColor()) {
            return LabelResponse.of(label);   // değişiklik yok: kimseye bir şey gitmez
        }
        requireFreeName(boardId, name, label.getId());

        label.setName(name);
        label.setColor(color);
        save(label);

        LabelResponse response = LabelResponse.of(label);
        broadcast(boardId, LabelEvent.updated(response));
        return response;
    }

    /** Etiketi siler; takılı olduğu bütün kartlardan da kalkar (card_labels, ON DELETE CASCADE). */
    public void delete(Long boardId, Long labelId, String actorEmail) {
        accessService.requireEditor(boardId, actorEmail);
        lockBoard(boardId);
        Label label = labelOnBoard(boardId, labelId);
        long cards = labelRepository.countCards(label.getId());

        labelRepository.delete(label);
        labelRepository.flush();
        log.info("Etiket silindi: id={}, boardId={}, kart={}", labelId, boardId, cards);

        Map<String, Object> params = params(null, label);
        params.put("count", cards);
        activityService.record(boardId, actorEmail, DELETE, "%s sildi".formatted(labelPhrase(label)),
                new DeletedLabelPayload(label.getId(), label.getName(), label.getColor(), cards), params);
        broadcast(boardId, LabelEvent.deleted(labelId));
    }

    // ── kartın etiketleri ───────────────────────────────────────────

    /** Etiketi karta takar. Zaten takılıysa hiçbir şey olmaz: kayıt da yayın da yok. */
    public void addToCard(Long boardId, Long cardId, Long labelId, String actorEmail) {
        accessService.requireEditor(boardId, actorEmail);
        Card card = scopeGuard.requireCardOnBoard(boardId, cardId);
        Label label = labelOnBoard(boardId, labelId);

        int added;
        try {
            added = labelRepository.attach(card.getId(), label.getId());
        } catch (DataIntegrityViolationException e) {
            // Etiket (ya da kart) tam bu sırada başka bir istekle silindi.
            throw new ResourceNotFoundException("error.notFound.label", labelId);
        }
        if (added == 0) {
            return;
        }
        activityService.record(boardId, actorEmail, ADD_TO_CARD,
                "%s kartına %s ekledi".formatted(quoted(card.getTitle()), labelPhrase(label)),
                new CardLabelPayload(card.getId(), label.getId()), params(card.getTitle(), label));
        broadcast(boardId, LabelEvent.addedToCard(card.getId(), label.getId()));
    }

    /** Etiketi karttan çıkarır. Takılı değilse hiçbir şey olmaz. */
    public void removeFromCard(Long boardId, Long cardId, Long labelId, String actorEmail) {
        accessService.requireEditor(boardId, actorEmail);
        Card card = scopeGuard.requireCardOnBoard(boardId, cardId);
        Label label = labelOnBoard(boardId, labelId);

        if (labelRepository.detach(card.getId(), label.getId()) == 0) {
            return;
        }
        activityService.record(boardId, actorEmail, REMOVE_FROM_CARD,
                "%s kartından %s kaldırdı".formatted(quoted(card.getTitle()), labelPhrase(label)),
                new CardLabelPayload(card.getId(), label.getId()), params(card.getTitle(), label));
        broadcast(boardId, LabelEvent.removedFromCard(card.getId(), label.getId()));
    }

    // ── yardımcılar ─────────────────────────────────────────────────

    private void lockBoard(Long boardId) {
        labelRepository.lockBoard(boardId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound.board", boardId));
    }

    private Label labelOnBoard(Long boardId, Long labelId) {
        return labelRepository.findByIdAndBoardId(labelId, boardId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound.label", labelId));
    }

    private void requireFreeName(Long boardId, String name, Long exceptId) {
        if (name != null && labelRepository.nameTaken(boardId, name, exceptId == null ? -1L : exceptId)) {
            throw new ConflictException("error.label.duplicate");
        }
    }

    /**
     * Kaydeder ve hemen yazar. Benzersiz ad indeksi ikinci savunma hattı: kilit
     * olmasaydı aynı anda gelen iki "Acil"den birini bu yakalardı.
     */
    private Label save(Label label) {
        try {
            return labelRepository.saveAndFlush(label);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("error.label.duplicate");
        }
    }

    private static LabelColor validColor(String raw) {
        return LabelColor.parse(raw)
                .orElseThrow(() -> new BadRequestException("error.label.color", LabelColor.allNames()));
    }

    /** Boş ad = adsız etiket (null). */
    private static String validName(String raw) {
        String name = raw == null ? "" : raw.strip();
        if (name.isEmpty()) {
            return null;
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new BadRequestException("error.label.nameTooLong", String.valueOf(MAX_NAME_LENGTH));
        }
        return name;
    }

    /**
     * Geçmiş satırının parçaları (ADR 0020): arayüz cümleyi kendi dilinde kurar.
     * Adsız etikette ad yoktur, renk vardır ("yeşil etiketi ekledi").
     */
    private static Map<String, Object> params(String cardTitle, Label label) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (cardTitle != null) {
            params.put("card", cardTitle);
        }
        if (label.getName() != null) {
            params.put("label", label.getName());
        }
        params.put("color", label.getColor().name());
        return params;
    }

    /** Türkçe yedek açıklamadaki etiket: "'Acil' etiketini" ya da "yeşil etiketi". */
    private static String labelPhrase(Label label) {
        return label.getName() != null
                ? "%s etiketini".formatted(quoted(label.getName()))
                : "%s etiketi".formatted(label.getColor().trName());
    }

    private static String quoted(String value) {
        String v = value.length() > DESCRIPTION_TITLE ? value.substring(0, DESCRIPTION_TITLE) + "…" : value;
        return "'" + v + "'";
    }

    private void broadcast(Long boardId, LabelEvent event) {
        AfterCommit.run(() -> broadcastService.broadcast("/topic/board." + boardId, event));
    }
}
