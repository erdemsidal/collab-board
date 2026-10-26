package com.collabboard.checklist;

import com.collabboard.audit.ActivityService;
import com.collabboard.board.BoardAccessService;
import com.collabboard.board.OperationScopeGuard;
import com.collabboard.board.dto.ChecklistSummary;
import com.collabboard.board.entity.Card;
import com.collabboard.checklist.dto.ChecklistEvent;
import com.collabboard.checklist.dto.ChecklistItemRequest;
import com.collabboard.checklist.dto.ChecklistRequest;
import com.collabboard.checklist.dto.ChecklistResponse;
import com.collabboard.common.exception.BadRequestException;
import com.collabboard.common.exception.ResourceNotFoundException;
import com.collabboard.common.tx.AfterCommit;
import com.collabboard.realtime.BroadcastService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Kartların kontrol listeleri (ADR 0022).
 *
 * Eşzamanlılık kuralı tek cümle: listeye dokunan her işlem önce listenin satırını
 * kilitler (ChecklistRepository.lockOnCard), sonra okur ve yazar. Aynı listeye
 * gelen ikinci istek bekler, ilkinin sonucunu görür. Bu yüzden:
 *  - sıra numaraları her zaman 0..n-1, boşluksuz ve tekil kalır (veritabanı da denetler);
 *  - "son madde işaretlendi, liste tamamlandı" tek bir istekte görülür, bir kez kaydedilir;
 *  - revision işlemlerin yazılma sırasıyla artar, arayüz eski olayı ayırt eder.
 * Farklı listeler birbirini beklemez.
 *
 * Olay kaydına yalnızca anlamlı anlar yazılır: liste eklendi, tamamlandı, silindi.
 * Her işaretleme yazılsaydı "Son hareketler" okunmaz olurdu. Bu yüzden listeler
 * zaman yolculuğunda oynatılmaz (BoardHistoryService); geçmiş görünümünde yoktur.
 */
@Service
@Transactional
public class ChecklistService {

    public static final String ADD = "ADD_CHECKLIST";
    public static final String COMPLETE = "COMPLETE_CHECKLIST";
    public static final String DELETE = "DELETE_CHECKLIST";

    static final int MAX_CHECKLISTS = 10;
    static final int MAX_ITEMS = 100;
    static final int MAX_TITLE_LENGTH = 100;
    static final int MAX_TEXT_LENGTH = 300;
    /** Geçmiş açıklamasında kart başlığının en fazla bu kadarı (sütun 500 karakter). */
    private static final int DESCRIPTION_TITLE = 200;

    private static final Logger log = LoggerFactory.getLogger(ChecklistService.class);

    private final ChecklistRepository checklistRepository;
    private final ChecklistItemRepository itemRepository;
    private final BoardAccessService accessService;
    private final OperationScopeGuard scopeGuard;
    private final ActivityService activityService;
    private final BroadcastService broadcastService;

    public ChecklistService(ChecklistRepository checklistRepository, ChecklistItemRepository itemRepository,
                            BoardAccessService accessService, OperationScopeGuard scopeGuard,
                            ActivityService activityService, BroadcastService broadcastService) {
        this.checklistRepository = checklistRepository;
        this.itemRepository = itemRepository;
        this.accessService = accessService;
        this.scopeGuard = scopeGuard;
        this.activityService = activityService;
        this.broadcastService = broadcastService;
    }

    /** Olay kaydına yazılan veri: hangi kartın hangi listesi. */
    public record ActivityPayload(Long cardId, Long checklistId) {
    }

    // ── okuma ───────────────────────────────────────────────────────

    /** Kartın listeleri maddeleriyle; iki sorgu (listeler, sonra bütün maddeler). İzleyici de okur. */
    @Transactional(readOnly = true)
    public List<ChecklistResponse> list(Long boardId, Long cardId, String actorEmail) {
        accessService.requireMember(boardId, actorEmail);
        Card card = scopeGuard.requireCardOnBoard(boardId, cardId);
        List<Checklist> lists = checklistRepository.findByCardIdOrderByIdAsc(card.getId());
        if (lists.isEmpty()) {
            return List.of();
        }
        Map<Long, List<ChecklistItem>> items = itemRepository
                .findByChecklistIdInOrderByPositionAscIdAsc(lists.stream().map(Checklist::getId).toList())
                .stream().collect(Collectors.groupingBy(ChecklistItem::getChecklistId));
        return lists.stream().map(c -> ChecklistResponse.of(c, items.getOrDefault(c.getId(), List.of()))).toList();
    }

    /** Panonun fotoğrafı için: kart id → listelerinin özeti ("3/5"), tek gruplu sorgu. */
    @Transactional(readOnly = true)
    public Map<Long, List<ChecklistSummary>> summariesByCard(Long boardId) {
        Map<Long, List<ChecklistSummary>> out = new LinkedHashMap<>();
        for (ChecklistRepository.SummaryRow row : checklistRepository.summaries(boardId)) {
            out.computeIfAbsent(row.getCardId(), id -> new ArrayList<>())
                    .add(new ChecklistSummary(row.getId(), row.getDone(), row.getTotal(), row.getRevision()));
        }
        return out;
    }

    // ── liste ───────────────────────────────────────────────────────

    public ChecklistResponse create(Long boardId, Long cardId, ChecklistRequest request, String actorEmail) {
        accessService.requireEditor(boardId, actorEmail);
        Card card = scopeGuard.requireCardOnBoard(boardId, cardId);
        String title = validTitle(request.title());
        // Kartın satırı kilitlenir: aynı anda eklenen iki liste sınırı birlikte aşamaz.
        checklistRepository.lockCard(card.getId())
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound.card", cardId));
        if (checklistRepository.countByCardId(card.getId()) >= MAX_CHECKLISTS) {
            throw new BadRequestException("error.checklist.limit", String.valueOf(MAX_CHECKLISTS));
        }

        Checklist saved = checklistRepository.save(Checklist.builder()
                .cardId(card.getId())
                .title(title)
                .revision(0)
                .createdAt(LocalDateTime.now())
                .build());
        log.info("Kontrol listesi eklendi: id={}, cardId={}", saved.getId(), card.getId());

        record(boardId, actorEmail, ADD, card, saved, "%s kartına %s listesini ekledi");
        ChecklistResponse response = ChecklistResponse.of(saved, List.of());
        broadcast(boardId, ChecklistEvent.updated(response, true));
        return response;
    }

    public ChecklistResponse rename(Long boardId, Long cardId, Long checklistId, ChecklistRequest request,
                                    String actorEmail) {
        accessService.requireEditor(boardId, actorEmail);
        Card card = scopeGuard.requireCardOnBoard(boardId, cardId);
        String title = validTitle(request.title());
        Checklist checklist = lock(card, checklistId);
        List<ChecklistItem> items = itemRepository.findByChecklistIdOrderByPositionAscIdAsc(checklist.getId());
        if (title.equals(checklist.getTitle())) {
            return ChecklistResponse.of(checklist, items);
        }
        checklist.setTitle(title);
        return changed(boardId, checklist, items, false);
    }

    /** Listeyi maddeleriyle siler (checklist_items, ON DELETE CASCADE). */
    public void delete(Long boardId, Long cardId, Long checklistId, String actorEmail) {
        accessService.requireEditor(boardId, actorEmail);
        Card card = scopeGuard.requireCardOnBoard(boardId, cardId);
        Checklist checklist = lock(card, checklistId);

        checklistRepository.delete(checklist);
        checklistRepository.flush();
        log.info("Kontrol listesi silindi: id={}, cardId={}", checklistId, card.getId());

        record(boardId, actorEmail, DELETE, card, checklist, "%s kartındaki %s listesini sildi");
        broadcast(boardId, ChecklistEvent.deleted(card.getId(), checklist.getId()));
    }

    // ── maddeler ────────────────────────────────────────────────────

    /** Listenin sonuna madde ekler. */
    public ChecklistResponse addItem(Long boardId, Long cardId, Long checklistId, ChecklistItemRequest request,
                                     String actorEmail) {
        accessService.requireEditor(boardId, actorEmail);
        Card card = scopeGuard.requireCardOnBoard(boardId, cardId);
        String text = validText(request.text());
        Checklist checklist = lock(card, checklistId);
        List<ChecklistItem> items = itemRepository.findByChecklistIdOrderByPositionAscIdAsc(checklist.getId());
        if (items.size() >= MAX_ITEMS) {
            throw new BadRequestException("error.checklist.itemLimit", String.valueOf(MAX_ITEMS));
        }

        // Kilit altındayız ve sıra boşluksuz: sondaki yer tam olarak madde sayısı.
        items.add(itemRepository.save(ChecklistItem.builder()
                .checklistId(checklist.getId())
                .text(text)
                .done(false)
                .position(items.size())
                .createdAt(LocalDateTime.now())
                .build()));
        return changed(boardId, checklist, items, false);
    }

    /**
     * Maddeyi değiştirir: metin, yapıldı bilgisi ve/veya yeri. Hiçbiri değişmiyorsa
     * hiçbir şey yazılmaz, kimseye olay gitmez.
     *
     * Bir işaretleme listedeki son açık maddeyi kapatırsa "liste tamamlandı" olay
     * kaydına yazılır. Kilit sayesinde aynı anda işaretlenen iki maddeden yalnızca
     * sonra gelen bunu görür: kayıt bir kez düşer.
     */
    public ChecklistResponse updateItem(Long boardId, Long cardId, Long checklistId, Long itemId,
                                        ChecklistItemRequest request, String actorEmail) {
        accessService.requireEditor(boardId, actorEmail);
        Card card = scopeGuard.requireCardOnBoard(boardId, cardId);
        if (request.text() == null && request.done() == null && request.position() == null) {
            throw new BadRequestException("error.checklist.nothingToChange");
        }
        String text = request.text() == null ? null : validText(request.text());
        Checklist checklist = lock(card, checklistId);
        List<ChecklistItem> items = itemRepository.findByChecklistIdOrderByPositionAscIdAsc(checklist.getId());
        ChecklistItem item = items.stream().filter(i -> i.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound.checklistItem", itemId));

        boolean wasComplete = isComplete(items);
        boolean changed = false;
        if (text != null && !text.equals(item.getText())) {
            item.setText(text);
            changed = true;
        }
        if (request.done() != null && request.done() != item.isDone()) {
            item.setDone(request.done());
            changed = true;
        }
        if (request.position() != null) {
            changed |= move(items, item, request.position());
        }
        if (!changed) {
            return ChecklistResponse.of(checklist, items);
        }

        boolean completed = !wasComplete && Boolean.TRUE.equals(request.done()) && isComplete(items);
        if (completed) {
            record(boardId, actorEmail, COMPLETE, card, checklist, "%s kartındaki %s listesini tamamladı");
        }
        return changed(boardId, checklist, items, completed);
    }

    /** Maddeyi siler; arkasındakiler bir öne kayar. */
    public ChecklistResponse deleteItem(Long boardId, Long cardId, Long checklistId, Long itemId, String actorEmail) {
        accessService.requireEditor(boardId, actorEmail);
        Card card = scopeGuard.requireCardOnBoard(boardId, cardId);
        Checklist checklist = lock(card, checklistId);
        List<ChecklistItem> items = itemRepository.findByChecklistIdOrderByPositionAscIdAsc(checklist.getId());
        ChecklistItem item = items.stream().filter(i -> i.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound.checklistItem", itemId));

        items.remove(item);
        itemRepository.delete(item);
        renumber(items);
        return changed(boardId, checklist, items, false);
    }

    // ── yardımcılar ─────────────────────────────────────────────────

    /** Listeyi kilitleyerek getirir; bu kartın listesi değilse 404. */
    private Checklist lock(Card card, Long checklistId) {
        return checklistRepository.lockOnCard(checklistId, card.getId())
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound.checklist", checklistId));
    }

    /**
     * Maddeyi yeni yerine taşır ve listeyi 0'dan yeniden numaralar. Taşınan madde
     * zaten oradaysa false. Listenin dışını gösteren yer başa ya da sona yuvarlanır.
     */
    private static boolean move(List<ChecklistItem> items, ChecklistItem item, int position) {
        int from = items.indexOf(item);
        int to = Math.max(0, Math.min(position, items.size() - 1));
        if (from == to) {
            return false;
        }
        items.remove(from);
        items.add(to, item);
        renumber(items);
        return true;
    }

    private static void renumber(List<ChecklistItem> items) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getPosition() != i) {
                items.get(i).setPosition(i);
            }
        }
    }

    private static boolean isComplete(List<ChecklistItem> items) {
        return !items.isEmpty() && items.stream().allMatch(ChecklistItem::isDone);
    }

    /**
     * Değişikliği bitirir: revision bir artar, istemciye ve panodaki herkese listenin
     * yeni hâli (yayın, işlem kaydedildikten sonra). Satırlar işlem sonunda yazılır.
     */
    private ChecklistResponse changed(Long boardId, Checklist checklist, List<ChecklistItem> items, boolean logged) {
        checklist.setRevision(checklist.getRevision() + 1);
        ChecklistResponse response = ChecklistResponse.of(checklist, items);
        broadcast(boardId, ChecklistEvent.updated(response, logged));
        return response;
    }

    private void record(Long boardId, String actorEmail, String type, Card card, Checklist checklist,
                        String description) {
        activityService.record(boardId, actorEmail, type,
                description.formatted(quoted(card.getTitle()), quoted(checklist.getTitle())),
                new ActivityPayload(card.getId(), checklist.getId()),
                Map.of("card", card.getTitle(), "checklist", checklist.getTitle()));
    }

    private static String validTitle(String raw) {
        String title = raw == null ? "" : raw.strip();
        if (title.isEmpty()) {
            throw new BadRequestException("error.checklist.titleRequired");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new BadRequestException("error.checklist.titleTooLong", String.valueOf(MAX_TITLE_LENGTH));
        }
        return title;
    }

    private static String validText(String raw) {
        String text = raw == null ? "" : raw.strip();
        if (text.isEmpty()) {
            throw new BadRequestException("error.checklist.itemRequired");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new BadRequestException("error.checklist.itemTooLong", String.valueOf(MAX_TEXT_LENGTH));
        }
        return text;
    }

    private static String quoted(String value) {
        String v = value.length() > DESCRIPTION_TITLE ? value.substring(0, DESCRIPTION_TITLE) + "…" : value;
        return "'" + v + "'";
    }

    private void broadcast(Long boardId, ChecklistEvent event) {
        AfterCommit.run(() -> broadcastService.broadcast("/topic/board." + boardId, event));
    }
}
