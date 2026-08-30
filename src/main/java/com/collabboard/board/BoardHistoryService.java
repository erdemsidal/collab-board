package com.collabboard.board;

import com.collabboard.audit.BoardActivity;
import com.collabboard.audit.BoardActivityRepository;
import com.collabboard.board.dto.BoardResponse;
import com.collabboard.board.dto.CardResponse;
import com.collabboard.board.dto.ColumnResponse;
import com.collabboard.board.dto.TimelineEntry;
import com.collabboard.board.entity.Board;
import com.collabboard.board.entity.BoardColumn;
import com.collabboard.common.exception.ResourceNotFoundException;
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
    private final ObjectMapper objectMapper;

    public BoardHistoryService(BoardRepository boardRepository, BoardActivityRepository activityRepository,
                               BoardAccessService accessService, ObjectMapper objectMapper) {
        this.boardRepository = boardRepository;
        this.activityRepository = activityRepository;
        this.accessService = accessService;
        this.objectMapper = objectMapper;
    }

    /**
     * Zaman çizelgesi: geri sarılabilecek anların listesi (eskiden yeniye).
     * Arayüzdeki kaydırıcı bu listeyi kullanır; her giriş bir "an"dır.
     */
    public List<TimelineEntry> timeline(Long boardId, String actorEmail) {
        accessService.requireMember(boardId, actorEmail);

        return activityRepository.findByBoardIdOrderByIdAsc(boardId).stream()
                .filter(a -> a.getPayload() != null)   // eski kayıtlar yeniden kurulamaz
                .map(a -> new TimelineEntry(a.getId(), a.getActorName(), a.getType(),
                        a.getDescription(), a.getCreatedAt()))
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
                .orElseThrow(() -> new ResourceNotFoundException("Board", "id", boardId));

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

        return new BoardResponse(board.getId(), board.getName(), result, board.getCreatedAt());
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
                list.add(new CardResponse(c.id, c.title, i, c.description, c.assigneeId, c.dueDate, c.version));
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

        ReplayCard(long id, String title, long version) {
            this.id = id; this.title = title; this.version = version;
        }
    }
}
