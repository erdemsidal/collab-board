package com.collabboard.board;

import com.collabboard.audit.BoardActivity;
import com.collabboard.audit.BoardActivityRepository;
import com.collabboard.board.dto.FlowResponse;
import com.collabboard.board.entity.Board;
import com.collabboard.board.entity.BoardColumn;
import com.collabboard.board.entity.Card;
import com.collabboard.common.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Kartların kolonlarda ne kadar beklediğini ölçer ve darboğazı bulur.
 *
 * Ölçüm ayrı bir tabloya değil, olay kaydına dayanır: bir kart bir kolona
 * girdiğinde ve çıktığında zaten olay yazılıyor, dolayısıyla bekleme süresi bu
 * iki damganın farkıdır. Ayrıca "kaç gündür burada duruyor" bilgisini tutan bir
 * alan eklemek gerekmez — geçmiş zaten bunu içeriyor.
 *
 * Olayların oynatılması CardFlowReplay'de; ana ekran özeti de aynısını kullanır.
 */
@Service
@Transactional(readOnly = true)
public class FlowMetricsService {

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
        // Kartın şu anda hangi kolonda olduğu ve o kolona ne zaman girdiği (ana ekranla ortak)
        CardFlowReplay replay = new CardFlowReplay(objectMapper);
        Set<Long> completed = new HashSet<>();
        List<Long> cycleTimes = new ArrayList<>();
        int transitions = 0;

        for (BoardActivity activity : activityRepository.findByBoardIdOrderByIdAsc(boardId)) {
            CardFlowReplay.Step step = replay.apply(activity).orElse(null);
            // Ekleme ya da kaydı eksik kartın ilk görülüşü: tamamlanmış bir bekleme yok.
            if (step == null || step.fromColumnId() == null) {
                continue;
            }
            dwellsByColumn.computeIfAbsent(step.fromColumnId(), k -> new ArrayList<>())
                    .add(Duration.between(step.enteredAt(), step.at()).toSeconds());
            transitions++;

            // Son kolona İLK ulaşma anı çevrim süresini tamamlar. Eklenme anı
            // bilinmeyen kartlar (kayıttan önce oluşmuş) bu ortalamaya girmez.
            if (step.toColumnId() != null && step.toColumnId().equals(lastColumnId)
                    && step.addedAt() != null && completed.add(step.cardId())) {
                cycleTimes.add(Duration.between(step.addedAt(), step.at()).toSeconds());
            }
        }

        LocalDateTime now = LocalDateTime.now();
        List<FlowResponse.ColumnFlow> columnFlows = new ArrayList<>();
        for (BoardColumn column : ordered) {
            // Doluluk panonun kendisinden okunur — olay kaydından türetilseydi,
            // kaydı eksik olan eski kartlar sayıma girmez ve rakam yanlış çıkardı.
            int cardCount = column.getCards().size();

            Long oldest = replay.earliestEntryIn(column.getId())
                    .map(since -> Duration.between(since, now).toSeconds())
                    .orElse(null);

            columnFlows.add(new FlowResponse.ColumnFlow(
                    column.getId(), column.getName(), cardCount,
                    average(dwellsByColumn.get(column.getId())), oldest));
        }

        Long bottleneck = columnFlows.stream()
                .filter(c -> c.avgDwellSeconds() != null)
                .max(Comparator.comparingLong(FlowResponse.ColumnFlow::avgDwellSeconds))
                .map(FlowResponse.ColumnFlow::columnId)
                .orElse(null);

        return new FlowResponse(columnFlows, aging(ordered, replay, now),
                average(cycleTimes), bottleneck, transitions);
    }

    /**
     * Açık kartların tek tek yaşı, en eskiden yeniye.
     *
     * Ortalamalar bir kolonun genel sağlığını anlatır ama "hangi kart takıldı"
     * sorusuna cevap vermez. Bu liste onu verir; unutulmuş işi görünür kılar.
     *
     * Kartlar panonun kendisinden okunur, olay kaydından değil: kaydı eksik olan
     * eski kartlar da listeye girsin diye. Onlar için giriş anı bilinmediğinden
     * kartın oluşturulma anı kullanılır ve sonuç estimated=true olarak işaretlenir
     * (CardFlowReplay.entry; ana ekranın "yaşlanan iş" listesi de aynı kuralı kullanır).
     */
    private List<FlowResponse.AgingCard> aging(List<BoardColumn> ordered,
                                               CardFlowReplay replay, LocalDateTime now) {
        List<FlowResponse.AgingCard> result = new ArrayList<>();
        for (BoardColumn column : ordered) {
            for (Card card : column.getCards()) {
                replay.entry(card).ifPresent(entry -> result.add(new FlowResponse.AgingCard(
                        card.getId(), card.getTitle(), column.getId(), column.getName(),
                        card.getAssigneeId(), Duration.between(entry.since(), now).toSeconds(),
                        entry.estimated())));
            }
        }
        result.sort(Comparator.comparingLong(FlowResponse.AgingCard::ageSeconds).reversed());
        return result;
    }

    private Long average(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return Math.round(values.stream().mapToLong(Long::longValue).average().orElse(0));
    }
}
