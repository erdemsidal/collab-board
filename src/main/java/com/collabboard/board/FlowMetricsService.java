package com.collabboard.board;

import com.collabboard.audit.BoardActivity;
import com.collabboard.audit.BoardActivityRepository;
import com.collabboard.board.dto.FlowResponse;
import com.collabboard.board.entity.Board;
import com.collabboard.board.entity.BoardColumn;
import com.collabboard.common.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kartların kolonlarda ne kadar beklediğini ölçer ve darboğazı bulur.
 *
 * Ölçüm ayrı bir tabloya değil, olay kaydına dayanır: bir kart bir kolona
 * girdiğinde ve çıktığında zaten olay yazılıyor, dolayısıyla bekleme süresi bu
 * iki damganın farkıdır. Ayrıca "kaç gündür burada duruyor" bilgisini tutan bir
 * alan eklemek gerekmez — geçmiş zaten bunu içeriyor.
 */
@Service
@Transactional(readOnly = true)
public class FlowMetricsService {

    private static final Logger log = LoggerFactory.getLogger(FlowMetricsService.class);

    private final BoardRepository boardRepository;
    private final BoardActivityRepository activityRepository;
    private final BoardAccessService accessService;
    private final ObjectMapper objectMapper;

    public FlowMetricsService(BoardRepository boardRepository, BoardActivityRepository activityRepository,
                              BoardAccessService accessService, ObjectMapper objectMapper) {
        this.boardRepository = boardRepository;
        this.activityRepository = activityRepository;
        this.accessService = accessService;
        this.objectMapper = objectMapper;
    }

    public FlowResponse analyze(Long boardId, String actorEmail) {
        accessService.requireMember(boardId, actorEmail);

        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new ResourceNotFoundException("Board", "id", boardId));

        Map<Long, String> columnNames = new LinkedHashMap<>();
        List<BoardColumn> ordered = board.getColumns().stream()
                .sorted(Comparator.comparingInt(BoardColumn::getPosition))
                .toList();
        ordered.forEach(c -> columnNames.put(c.getId(), c.getName()));
        Long lastColumnId = ordered.isEmpty() ? null : ordered.get(ordered.size() - 1).getId();

        // Kolon başına tamamlanmış bekleme süreleri
        Map<Long, List<Long>> dwellsByColumn = new HashMap<>();
        // Kartın şu anda hangi kolonda olduğu ve o kolona ne zaman girdiği
        Map<Long, CardState> cards = new HashMap<>();
        List<Long> cycleTimes = new ArrayList<>();
        int transitions = 0;

        for (BoardActivity activity : activityRepository.findByBoardIdOrderByIdAsc(boardId)) {
            if (activity.getPayload() == null) {
                continue;
            }
            try {
                JsonNode e = objectMapper.readTree(activity.getPayload());
                LocalDateTime at = activity.getCreatedAt();

                switch (activity.getType()) {
                    case "ADD_CARD" -> cards.put(e.get("card").get("id").asLong(),
                            new CardState(e.get("columnId").asLong(), at, at));

                    case "MOVE_CARD" -> {
                        CardState state = cards.get(e.get("cardId").asLong());
                        if (state == null) {
                            break;   // bu özellikten önce eklenmiş kart: başlangıcı bilinmiyor
                        }
                        long seconds = Duration.between(state.enteredAt, at).toSeconds();
                        dwellsByColumn.computeIfAbsent(state.columnId, k -> new ArrayList<>()).add(seconds);
                        transitions++;

                        long target = e.get("toColumnId").asLong();
                        // Son kolona İLK ulaşma anı çevrim süresini tamamlar.
                        if (target == (lastColumnId == null ? -1 : lastColumnId) && !state.completed) {
                            cycleTimes.add(Duration.between(state.createdAt, at).toSeconds());
                            state.completed = true;
                        }
                        state.columnId = target;
                        state.enteredAt = at;
                    }

                    case "DELETE_CARD" -> {
                        CardState state = cards.remove(e.get("cardId").asLong());
                        if (state != null) {
                            dwellsByColumn.computeIfAbsent(state.columnId, k -> new ArrayList<>())
                                    .add(Duration.between(state.enteredAt, at).toSeconds());
                            transitions++;
                        }
                    }

                    default -> { /* düzenleme ve kolon taşıma akış süresini etkilemez */ }
                }
            } catch (Exception ex) {
                log.warn("Akış ölçümünde kayıt atlandı: activityId={}", activity.getId(), ex);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        List<FlowResponse.ColumnFlow> columnFlows = new ArrayList<>();
        for (Map.Entry<Long, String> col : columnNames.entrySet()) {
            List<CardState> living = cards.values().stream()
                    .filter(s -> s.columnId.equals(col.getKey()))
                    .toList();

            Long oldest = living.stream()
                    .map(s -> Duration.between(s.enteredAt, now).toSeconds())
                    .max(Long::compareTo).orElse(null);

            columnFlows.add(new FlowResponse.ColumnFlow(
                    col.getKey(), col.getValue(), living.size(),
                    average(dwellsByColumn.get(col.getKey())), oldest));
        }

        Long bottleneck = columnFlows.stream()
                .filter(c -> c.avgDwellSeconds() != null)
                .max(Comparator.comparingLong(FlowResponse.ColumnFlow::avgDwellSeconds))
                .map(FlowResponse.ColumnFlow::columnId)
                .orElse(null);

        return new FlowResponse(columnFlows, average(cycleTimes), bottleneck, transitions);
    }

    private Long average(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return Math.round(values.stream().mapToLong(Long::longValue).average().orElse(0));
    }

    /** Bir kartın ölçüm sırasındaki durumu. */
    private static final class CardState {
        Long columnId;
        LocalDateTime enteredAt;
        final LocalDateTime createdAt;
        boolean completed;

        CardState(Long columnId, LocalDateTime enteredAt, LocalDateTime createdAt) {
            this.columnId = columnId; this.enteredAt = enteredAt; this.createdAt = createdAt;
        }
    }
}
