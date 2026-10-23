package com.collabboard.overview;

import com.collabboard.audit.BoardActivity;
import com.collabboard.audit.BoardActivityRepository;
import com.collabboard.board.CardFlowReplay;
import com.collabboard.board.CardRepository;
import com.collabboard.board.ColumnRepository;
import com.collabboard.board.FlowMetricsService;
import com.collabboard.board.entity.Board;
import com.collabboard.board.entity.BoardColumn;
import com.collabboard.board.entity.Card;
import com.collabboard.overview.dto.OverviewResponse.AgingCard;
import com.collabboard.overview.dto.OverviewResponse.BoardHealth;
import com.collabboard.overview.dto.OverviewResponse.ColumnLoad;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ana ekran özetinin akış bölümü: ne bitti, hangi pano tıkanıyor, hangi kart unutuldu.
 *
 * Hepsi "son kolon = bitti" kuralına dayanır (ADR 0015) ve kartın bir kolona ne
 * zaman girdiğini akış ölçümüyle aynı oynatıcıdan öğrenir (CardFlowReplay). Kart
 * olayları tüm panolar için TEK sorguda okunup bir kez oynatılır; pano başına
 * sorgu yalnızca darboğaz için var (FlowMetricsService, en fazla HEALTH_LIMIT pano).
 *
 * OverviewService'in okuma işlemi içinde çağrılır; panoları o verir.
 */
@Component
class FlowSection {

    /** Sağlık kartı gösterilen pano sayısı (her biri için akış ölçümü çalışır). */
    static final int HEALTH_LIMIT = 6;
    /** "Bekleyen iş" listesinin uzunluğu. */
    static final int AGING_LIMIT = 5;
    /** "Biten iş" pencerelerinin gün sayısı (bu hafta / geçen hafta). */
    static final int WINDOW_DAYS = 7;

    record Result(int doneLast7Days, int donePrev7Days, List<BoardHealth> boardsHealth, List<AgingCard> aging) {
    }

    private final ColumnRepository columnRepository;
    private final CardRepository cardRepository;
    private final BoardActivityRepository activityRepository;
    private final FlowMetricsService flowMetricsService;
    private final ObjectMapper objectMapper;

    FlowSection(ColumnRepository columnRepository, CardRepository cardRepository,
                BoardActivityRepository activityRepository, FlowMetricsService flowMetricsService,
                ObjectMapper objectMapper) {
        this.columnRepository = columnRepository;
        this.cardRepository = cardRepository;
        this.activityRepository = activityRepository;
        this.flowMetricsService = flowMetricsService;
        this.objectMapper = objectMapper;
    }

    /**
     * @param boards     isteği yapanın erişebildiği panolar (boş olmamalı)
     * @param day        istemcinin bugünü; pencereler bu günle biter
     * @param actorEmail darboğaz, akış ölçümünün kendi yetki kontrolünden geçerek alınır
     */
    Result summarize(Map<Long, Board> boards, LocalDate day, String actorEmail) {
        Map<Long, List<BoardColumn>> columnsByBoard = columnRepository
                .findByBoardIdInOrderByPositionAsc(boards.keySet()).stream()
                .collect(Collectors.groupingBy(c -> c.getBoard().getId(), LinkedHashMap::new, Collectors.toList()));
        Set<Long> doneColumns = columnsByBoard.values().stream()
                .map(FlowSection::doneColumn).filter(Objects::nonNull).collect(Collectors.toSet());

        // Açık kartlar: "bitti" kolonunda olmayanlar — sağlık sayıları ve bekleyen iş için.
        List<Long> openColumns = columnsByBoard.values().stream().flatMap(List::stream)
                .map(BoardColumn::getId).filter(id -> !doneColumns.contains(id)).toList();
        List<Card> open = openColumns.isEmpty() ? List.of() : cardRepository.findInColumns(openColumns);

        // Kart olaylarını bir kez oynat: biten işler sayılır, kartların giriş anları öğrenilir.
        CardFlowReplay replay = new CardFlowReplay(objectMapper);
        LocalDate lastFrom = day.minusDays(WINDOW_DAYS - 1);
        LocalDate prevFrom = lastFrom.minusDays(WINDOW_DAYS);
        Set<Long> doneLast = new HashSet<>();
        Set<Long> donePrev = new HashSet<>();
        for (BoardActivity activity : activityRepository.findByBoardIdInAndTypeInOrderByIdAsc(
                boards.keySet(), CardFlowReplay.CARD_EVENTS)) {
            replay.apply(activity)
                    // Son kolona BAŞKA bir kolondan giriş; Done içinde sıra değiştirmek iş bitirmek değil.
                    .filter(step -> doneColumns.contains(step.toColumnId()) && step.entered(step.toColumnId()))
                    .ifPresent(step -> {
                        LocalDate on = OverviewService.utcDay(step.at());
                        if (!on.isBefore(lastFrom) && !on.isAfter(day)) {
                            doneLast.add(step.cardId());
                        } else if (!on.isBefore(prevFrom) && on.isBefore(lastFrom)) {
                            donePrev.add(step.cardId());
                        }
                    });
        }

        return new Result(doneLast.size(), donePrev.size(),
                health(boards, columnsByBoard, open, day, actorEmail), aging(boards, open, replay));
    }

    /**
     * En son hareket gören panolar önce; hiç hareketi olmayanlar sonda (yeni pano önce).
     * Darboğaz yeniden hesaplanmaz: panonun akış ekranı ne diyorsa o (FlowMetricsService).
     */
    private List<BoardHealth> health(Map<Long, Board> boards, Map<Long, List<BoardColumn>> columnsByBoard,
                                     List<Card> open, LocalDate day, String actorEmail) {
        Map<Long, LocalDateTime> lastActivity = activityRepository.lastActivityPerBoard(boards.keySet()).stream()
                .collect(Collectors.toMap(BoardActivityRepository.LastActivity::getBoardId,
                        BoardActivityRepository.LastActivity::getLastAt));
        Map<Long, Long> cardsPerColumn = cardRepository.countPerColumn(boards.keySet()).stream()
                .collect(Collectors.toMap(CardRepository.ColumnCount::getColumnId, CardRepository.ColumnCount::getCards));
        Map<Long, List<Card>> openByBoard = open.stream()
                .collect(Collectors.groupingBy(c -> c.getColumn().getBoard().getId()));

        return boards.values().stream()
                .sorted(Comparator.comparing((Board b) -> lastActivity.get(b.getId()),
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Board::getId, Comparator.reverseOrder()))
                .limit(HEALTH_LIMIT)
                .map(board -> {
                    List<BoardColumn> columns = columnsByBoard.getOrDefault(board.getId(), List.of());
                    List<Card> openHere = openByBoard.getOrDefault(board.getId(), List.of());
                    Long bottleneckId = flowMetricsService.analyze(board.getId(), actorEmail).bottleneckColumnId();
                    return new BoardHealth(board.getId(), board.getName(),
                            columns.stream().map(c -> new ColumnLoad(c.getName(),
                                    cardsPerColumn.getOrDefault(c.getId(), 0L).intValue(), c.getWipLimit())).toList(),
                            columns.stream().filter(c -> c.getId().equals(bottleneckId))
                                    .map(BoardColumn::getName).findFirst().orElse(null),
                            columns.stream().filter(c -> c.getWipLimit() != null
                                            && cardsPerColumn.getOrDefault(c.getId(), 0L) >= c.getWipLimit())
                                    .map(BoardColumn::getName).findFirst().orElse(null),
                            openHere.size(),
                            (int) openHere.stream()
                                    .filter(c -> c.getDueDate() != null && c.getDueDate().isBefore(day)).count(),
                            lastActivity.get(board.getId()));
                })
                .toList();
    }

    /** Açık kartlar, şu anki kolonlarında en uzun bekleyen önce (giriş anı akış ölçümüyle aynı). */
    private List<AgingCard> aging(Map<Long, Board> boards, List<Card> open, CardFlowReplay replay) {
        LocalDateTime now = LocalDateTime.now();
        return open.stream()
                .flatMap(card -> replay.entry(card).map(entry -> {
                    Long boardId = card.getColumn().getBoard().getId();
                    return new AgingCard(card.getId(), card.getTitle(), boardId, boards.get(boardId).getName(),
                            card.getColumn().getName(), Duration.between(entry.since(), now).toSeconds(),
                            entry.estimated());
                }).stream())
                .sorted(Comparator.comparingLong(AgingCard::ageSeconds).reversed())
                .limit(AGING_LIMIT)
                .toList();
    }

    /**
     * "Bitti" kolonu: panonun son kolonu (akış ölçümü ve atanmış kartlarla aynı kural).
     * Tek kolonlu panoda akış yoktur, o kolon "bitti" anlamına gelmez → null.
     */
    private static Long doneColumn(List<BoardColumn> ordered) {
        return ordered.size() < 2 ? null : ordered.get(ordered.size() - 1).getId();
    }
}
