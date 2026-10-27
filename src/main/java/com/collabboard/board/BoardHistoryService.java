package com.collabboard.board;

import com.collabboard.audit.ActivityService;
import com.collabboard.audit.BoardActivity;
import com.collabboard.audit.BoardActivityRepository;
import com.collabboard.board.dto.BoardResponse;
import com.collabboard.board.dto.CardResponse;
import com.collabboard.board.dto.ColumnResponse;
import com.collabboard.board.dto.TimelineEntry;
import com.collabboard.board.entity.Board;
import com.collabboard.board.entity.BoardColumn;
import com.collabboard.common.exception.ResourceNotFoundException;
import com.collabboard.label.LabelColor;
import com.collabboard.label.LabelService;
import com.collabboard.label.dto.LabelResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Panonun geçmişteki hâlini yeniden kurar.
 *
 * Durumu geçmişe doğru geri almak yerine, BAŞTAN İLERİ SARARIZ: kolonlar boşken
 * başlar, olay kaydını sırayla uygulayıp istenen ana kadar geliriz. Geri alma
 * yaklaşımı her operasyonun tersini de tanımlamayı gerektirirdi (silinen kartın
 * içeriğini geri getirmek gibi); ileri sarmada böyle bir borç yoktur.
 *
 * Kolonların kendisi (ad ve kimlik) panoyla birlikte oluşur ve silinmez; bu yüzden
 * güncel kolonlardan başlanır, yalnızca sıraları olaylardan hesaplanır.
 */
@Service
@Transactional(readOnly = true)
public class BoardHistoryService {

    private static final Logger log = LoggerFactory.getLogger(BoardHistoryService.class);

    private final BoardRepository boardRepository;
    private final BoardActivityRepository activityRepository;
    private final BoardAccessService accessService;
    private final ActivityService activityService;
    private final LabelService labelService;
    private final ObjectMapper objectMapper;

    public BoardHistoryService(BoardRepository boardRepository, BoardActivityRepository activityRepository,
                               BoardAccessService accessService, ActivityService activityService,
                               LabelService labelService, ObjectMapper objectMapper) {
        this.boardRepository = boardRepository;
        this.activityRepository = activityRepository;
        this.accessService = accessService;
        this.activityService = activityService;
        this.labelService = labelService;
        this.objectMapper = objectMapper;
    }

    /**
     * Panonun hâlini değiştiren, yani geri sarılabilen olay türleri (apply'ın
     * bildikleri). Kayıtta başka türler de var: yorum (ADR 0019) ve panoya
     * katılma gibi. Onlar "Son hareketler"de görünür ama kaydırıcıda bir an
     * değildir; panonun o anki hâli bir önceki anla aynı olurdu.
     *
     * Etiketler oynatılır (ADR 0022): karta takma, çıkarma ve etiketi silme her
     * seferinde kaydediliyor. Kontrol listeleri oynatılmaz: işaretlemeler kayda
     * yazılmıyor, o anki ilerleme bilinemez.
     */
    static final Set<String> REPLAYABLE = Set.of("ADD_CARD", "MOVE_CARD", "EDIT_CARD", "DELETE_CARD",
            "SET_WIP_LIMIT", "MOVE_COLUMN", "ADD_COLUMN", "RENAME_COLUMN", "DELETE_COLUMN",
            LabelService.ADD_TO_CARD, LabelService.REMOVE_FROM_CARD, LabelService.DELETE);

    /**
     * Zaman çizelgesi: geri sarılabilecek anların listesi (eskiden yeniye).
     * Arayüzdeki kaydırıcı bu listeyi kullanır; her giriş bir "an"dır.
     */
    public List<TimelineEntry> timeline(Long boardId, String actorEmail) {
        accessService.requireMember(boardId, actorEmail);

        return activityRepository.findByBoardIdOrderByIdAsc(boardId).stream()
                .filter(a -> a.getPayload() != null)   // eski kayıtlar yeniden kurulamaz
                .filter(a -> REPLAYABLE.contains(a.getType()))
                .map(a -> new TimelineEntry(a.getId(), a.getActorName(), a.getType(),
                        a.getDescription(), activityService.paramsOf(a), a.getCreatedAt()))
                .toList();
    }

    /**
     * Panonun, verilen ana kadarki olaylar uygulandığındaki hâli.
     *
     * @param upToActivityId bu kaydı DA içerecek şekilde uygulanır; 0 verilirse
     *                       pano ilk kurulduğu andaki boş hâliyle döner.
     */
    public BoardResponse stateAt(Long boardId, Long upToActivityId, String actorEmail) {
        accessService.requireMember(boardId, actorEmail);

        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound.board", boardId));

        Map<Long, ReplayColumn> columns = seedColumns(board);

        List<BoardActivity> events = activityRepository
                .findByBoardIdAndIdLessThanEqualOrderByIdAsc(boardId, upToActivityId);
        for (BoardActivity activity : events) {
            apply(columns, activity);
        }

        List<ColumnResponse> result = columns.values().stream()
                .sorted((a, b) -> Integer.compare(a.position, b.position))
                .map(ReplayColumn::toResponse)
                .toList();

        return new BoardResponse(board.getId(), board.getName(), result, board.getCreatedAt(),
                labelsEverSeen(boardId));
    }

    /**
     * Geçmiş görünümünün etiket takımı: bugünkü etiketler ve sonradan silinenler.
     * Silinen etiketin adı ve rengi silme kaydında duruyor (silinen kolon gibi,
     * ADR 0008); o etiketi o anda taşıyan kart onu yine gösterebilsin. Adı ya da
     * rengi sonradan değişen etiket bugünkü hâliyle görünür.
     */
    private List<LabelResponse> labelsEverSeen(Long boardId) {
        Map<Long, LabelResponse> labels = new java.util.TreeMap<>();
        labelService.labelsOf(boardId).forEach(l -> labels.put(l.id(), l));
        for (BoardActivity activity : activityRepository.findByBoardIdInAndTypeInOrderByIdAsc(
                List.of(boardId), List.of(LabelService.DELETE))) {
            try {
                JsonNode e = objectMapper.readTree(activity.getPayload());
                long id = e.get("labelId").asLong();
                LabelColor color = LabelColor.parse(e.path("color").asText()).orElse(LabelColor.GREEN);
                String name = e.hasNonNull("name") ? e.get("name").asText() : null;
                labels.putIfAbsent(id, new LabelResponse(id, name, color));
            } catch (Exception ex) {
                log.warn("Silinen etiket okunamadı: activityId={}", activity.getId(), ex);
            }
        }
        return List.copyOf(labels.values());
    }

    /**
     * Yeniden kurulumun BAŞLANGIÇ hâli: panonun ilk olaydan önceki kolonları.
     *
     * Kolonlar artık eklenip silinebildiği için "bugünkü kolonlar" başlangıç
     * sayılamaz. İki düzeltme gerekir:
     *  - Sonradan EKLENEN kolon başlangıçta yoktu → tohuma konmaz, ADD_COLUMN
     *    olayı sırası gelince ekler. Yoksa var olmadığı anlarda da görünürdü.
     *  - SİLİNEN kolon bugün panoda yok ama geçmişte vardı → DELETE_COLUMN olayı
     *    adını ve konumunu taşıdığı için oradan geri kurulur.
     */
    private Map<Long, ReplayColumn> seedColumns(Board board) {
        java.util.Set<Long> eklenenler = new java.util.HashSet<>();
        Map<Long, ReplayColumn> silinenler = new LinkedHashMap<>();

        for (BoardActivity activity : activityRepository.findByBoardIdOrderByIdAsc(board.getId())) {
            if (activity.getPayload() == null) {
                continue;
            }
            try {
                JsonNode e = objectMapper.readTree(activity.getPayload());
                if ("ADD_COLUMN".equals(activity.getType())) {
                    eklenenler.add(e.get("column").get("id").asLong());
                } else if ("DELETE_COLUMN".equals(activity.getType())) {
                    long id = e.get("columnId").asLong();
                    silinenler.put(id, new ReplayColumn(id, e.get("name").asText(), e.get("position").asInt()));
                }
            } catch (Exception ex) {
                log.warn("Kolon tohumlaması sırasında kayıt atlandı: activityId={}", activity.getId(), ex);
            }
        }

        Map<Long, ReplayColumn> seed = new LinkedHashMap<>();
        for (BoardColumn column : board.getColumns()) {
            if (!eklenenler.contains(column.getId())) {
                seed.put(column.getId(), new ReplayColumn(column.getId(), column.getName(), column.getPosition()));
            }
        }
        silinenler.forEach((id, col) -> {
            if (!eklenenler.contains(id)) {
                seed.put(id, col);
            }
        });
        return seed;
    }

    private void apply(Map<Long, ReplayColumn> columns, BoardActivity activity) {
        if (activity.getPayload() == null) {
            return;
        }
        try {
            JsonNode e = objectMapper.readTree(activity.getPayload());
            switch (activity.getType()) {
                case "ADD_CARD" -> {
                    JsonNode card = e.get("card");
                    ReplayColumn col = columns.get(e.get("columnId").asLong());
                    if (col != null) {
                        ReplayCard fresh = new ReplayCard(card.get("id").asLong(),
                                card.get("title").asText(), card.get("version").asLong());
                        applyDetails(fresh, card);
                        col.insert(fresh, card.get("position").asInt());
                    }
                }
                case "MOVE_CARD" -> {
                    ReplayCard card = remove(columns, e.get("cardId").asLong());
                    ReplayColumn target = columns.get(e.get("toColumnId").asLong());
                    if (card != null && target != null) {
                        card.version = e.get("version").asLong();
                        target.insert(card, e.get("position").asInt());
                    }
                }
                case "EDIT_CARD" -> find(columns, e.get("cardId").asLong()).ifPresent(card -> {
                    card.title = e.get("title").asText();
                    card.version = e.get("version").asLong();
                    applyDetails(card, e);
                });
                case "DELETE_CARD" -> remove(columns, e.get("cardId").asLong());
                case "SET_WIP_LIMIT" -> {
                    ReplayColumn col = columns.get(e.get("columnId").asLong());
                    if (col != null) {
                        col.wipLimit = e.get("wipLimit").isNull() ? null : e.get("wipLimit").asInt();
                    }
                }
                case "MOVE_COLUMN" -> reorder(columns, e.get("columnId").asLong(), e.get("position").asInt());
                case "ADD_COLUMN" -> {
                    JsonNode col = e.get("column");
                    long id = col.get("id").asLong();
                    ReplayColumn fresh = new ReplayColumn(id, col.get("name").asText(), col.get("position").asInt());
                    fresh.wipLimit = col.hasNonNull("wipLimit") ? col.get("wipLimit").asInt() : null;
                    columns.put(id, fresh);
                }
                case "RENAME_COLUMN" -> {
                    ReplayColumn col = columns.get(e.get("columnId").asLong());
                    if (col != null) {
                        col.name = e.get("name").asText();
                    }
                }
                case "DELETE_COLUMN" -> columns.remove(e.get("columnId").asLong());
                case LabelService.ADD_TO_CARD -> find(columns, e.get("cardId").asLong())
                        .ifPresent(card -> card.labelIds.add(e.get("labelId").asLong()));
                case LabelService.REMOVE_FROM_CARD -> find(columns, e.get("cardId").asLong())
                        .ifPresent(card -> card.labelIds.remove(e.get("labelId").asLong()));
                case LabelService.DELETE -> {
                    long labelId = e.get("labelId").asLong();
                    columns.values().forEach(col -> col.cards.forEach(card -> card.labelIds.remove(labelId)));
                }
                default -> { /* bilinmeyen tip: yeniden kurulumu etkilemez */ }
            }
        } catch (Exception ex) {
            // Bozuk tek bir kayıt yüzünden tüm geçmişi kaybetmeyelim; atlayıp devam et.
            log.warn("Geçmiş kaydı uygulanamadı: activityId={}", activity.getId(), ex);
        }
    }

    /**
     * Kart detaylarını olay verisinden okur.
     *
     * Alanlar yoksa dokunmaz: bu özellikten ÖNCE kaydedilmis olaylarda detay alanlari
     * bulunmuyor ve onlari null'a cekmek gecmisi bozardi.
     */
    private void applyDetails(ReplayCard card, JsonNode node) {
        if (node.hasNonNull("description")) {
            card.description = node.get("description").asText();
        }
        if (node.hasNonNull("assigneeId")) {
            card.assigneeId = node.get("assigneeId").asLong();
        }
        if (node.hasNonNull("dueDate")) {
            card.dueDate = LocalDate.parse(node.get("dueDate").asText());
        }
    }

    private java.util.Optional<ReplayCard> find(Map<Long, ReplayColumn> columns, long cardId) {
        return columns.values().stream()
                .flatMap(c -> c.cards.stream())
                .filter(c -> c.id == cardId)
                .findFirst();
    }

    private ReplayCard remove(Map<Long, ReplayColumn> columns, long cardId) {
        for (ReplayColumn col : columns.values()) {
            for (int i = 0; i < col.cards.size(); i++) {
                if (col.cards.get(i).id == cardId) {
                    return col.cards.remove(i);
                }
            }
        }
        return null;
    }

    /** Kolonu yeni sıraya taşır ve diğerlerinin sırasını buna göre kaydırır. */
    private void reorder(Map<Long, ReplayColumn> columns, long columnId, int newPosition) {
        List<ReplayColumn> ordered = new ArrayList<>(columns.values());
        ordered.sort((a, b) -> Integer.compare(a.position, b.position));

        ReplayColumn moving = columns.get(columnId);
        if (moving == null) {
            return;
        }
        ordered.remove(moving);
        ordered.add(Math.min(Math.max(newPosition, 0), ordered.size()), moving);
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).position = i;
        }
    }

    /** Yeniden kurulum sırasındaki geçici kolon durumu. */
    private static final class ReplayColumn {
        final Long id;
        String name;   // RENAME_COLUMN ile değişebilir
        int position;
        /** null = sınırsız. Kartlar gibi boştan başlar, SET_WIP_LIMIT olaylarıyla kurulur. */
        Integer wipLimit;
        final List<ReplayCard> cards = new ArrayList<>();

        ReplayColumn(Long id, String name, int position) {
            this.id = id; this.name = name; this.position = position;
        }

        void insert(ReplayCard card, int at) {
            cards.add(Math.min(Math.max(at, 0), cards.size()), card);
        }

        ColumnResponse toResponse() {
            List<CardResponse> list = new ArrayList<>();
            for (int i = 0; i < cards.size(); i++) {
                ReplayCard c = cards.get(i);
                // Yorumlar geçmişte oynatılmaz (ADR 0019): geçmiş görünümünde sayı yok.
                // Etiketler o anki hâliyle, kontrol listeleri yok (ADR 0022).
                list.add(new CardResponse(c.id, c.title, i, c.description, c.assigneeId, c.dueDate, c.version, 0,
                        List.copyOf(c.labelIds), List.of()));
            }
            return new ColumnResponse(id, name, position, wipLimit, list);
        }
    }

    private static final class ReplayCard {
        final long id;
        String title;
        String description;
        Long assigneeId;
        LocalDate dueDate;
        long version;
        /** Takılı etiketler, id sırasıyla (panodaki etiket sırası). */
        final java.util.SortedSet<Long> labelIds = new java.util.TreeSet<>();

        ReplayCard(long id, String title, long version) {
            this.id = id; this.title = title; this.version = version;
        }
    }
}
