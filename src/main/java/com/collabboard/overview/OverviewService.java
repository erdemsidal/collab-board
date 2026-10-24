package com.collabboard.overview;

import com.collabboard.audit.ActivityService;
import com.collabboard.audit.BoardActivity;
import com.collabboard.audit.BoardActivityRepository;
import com.collabboard.board.AccessibleBoard;
import com.collabboard.board.BoardService;
import com.collabboard.board.CardRepository;
import com.collabboard.board.entity.Board;
import com.collabboard.board.entity.Card;
import com.collabboard.overview.dto.OverviewResponse;
import com.collabboard.user.UserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ana ekran özeti: "bana atanan ne var, ne gecikti, panolarımda ne oluyor?"
 *
 * Yetki burada YENİDEN yazılmaz. Hangi panoların kişiye açık olduğunu
 * "panolarım" listesini üreten BoardService.accessibleBoards söyler; sonraki her
 * sorgu yalnızca o pano kümesiyle sınırlanır. Böylece özet, pano listesinde
 * görünmeyen bir panodan tek bir kart başlığı bile sızdıramaz.
 *
 * Sorgu sayısı pano sayısından bağımsızdır: kartlar, hareketler ve hareket
 * yapanların fotoğrafları birer toplu sorguyla gelir. Akış (FlowSection) ve
 * kişiler (TeamSection) bölümleri ayrı sınıflarda, aynı okuma işleminde çalışır;
 * tek istisna sağlık kartlarındaki darboğaz (en fazla 6 pano için akış ölçümü).
 */
@Service
@Transactional(readOnly = true)
public class OverviewService {

    /** Listelenen atanmış kart sayısı — sayaç yine gerçek toplamı gösterir. */
    static final int ASSIGNED_LIMIT = 20;
    /** "Son hareketler" listesinin uzunluğu. */
    static final int ACTIVITY_LIMIT = 15;
    /** "Son N günde hareket" sayacının penceresi. */
    static final int ACTIVITY_WINDOW_DAYS = 7;

    private final UserService userService;
    private final BoardService boardService;
    private final CardRepository cardRepository;
    private final BoardActivityRepository activityRepository;
    private final ActivityService activityService;
    private final FlowSection flowSection;
    private final TeamSection teamSection;

    public OverviewService(UserService userService, BoardService boardService, CardRepository cardRepository,
                           BoardActivityRepository activityRepository, ActivityService activityService,
                           FlowSection flowSection, TeamSection teamSection) {
        this.userService = userService;
        this.boardService = boardService;
        this.cardRepository = cardRepository;
        this.activityRepository = activityRepository;
        this.activityService = activityService;
        this.flowSection = flowSection;
        this.teamSection = teamSection;
    }

    /**
     * @param today gecikmenin ölçüldüğü gün. Son tarih saatsiz bir GÜNDÜR ve
     *              "bugün" kişinin takvimine göre değişir: sunucu UTC'de gece
     *              yarısını geçmemişken Türkiye'de ertesi gün başlamış olabilir.
     *              İstemci kendi gününü gönderir ki sayaç, kartın üstündeki
     *              rozetle aynı şeyi söylesin. Gelmezse sunucunun günü kullanılır.
     */
    public OverviewResponse overview(String actorEmail, LocalDate today) {
        Long userId = userService.getUserByEmail(actorEmail).getId();
        LocalDate day = today != null ? today : LocalDate.now();

        Map<Long, Board> boards = boardService.accessibleBoards(userId).stream()
                .map(AccessibleBoard::board)
                .collect(Collectors.toMap(Board::getId, Function.identity()));

        // Hiç panosu olmayan yeni kullanıcı: boş bir IN listesiyle sorgu atmaya gerek yok.
        if (boards.isEmpty()) {
            return new OverviewResponse(0, 0, 0, 0, List.of(), List.of(), activityByDay(Set.of(), day),
                    0, 0, List.of(), List.of(), List.of(), List.of());
        }

        List<Card> open = cardRepository.findOpenAssigned(userId, boards.keySet());
        int overdue = (int) open.stream()
                .filter(c -> c.getDueDate() != null && c.getDueDate().isBefore(day))
                .count();
        List<OverviewResponse.AssignedCard> assigned = open.stream()
                .limit(ASSIGNED_LIMIT)
                .map(c -> toAssigned(c, boards))
                .toList();

        long recentCount = activityRepository.countByBoardIdInAndCreatedAtGreaterThanEqual(
                boards.keySet(), LocalDateTime.now().minusDays(ACTIVITY_WINDOW_DAYS));

        FlowSection.Result flow = flowSection.summarize(boards, day, actorEmail);
        TeamSection.Result people = teamSection.summarize(boards, userId);

        return new OverviewResponse(boards.size(), open.size(), overdue, recentCount,
                assigned, recentActivity(boards), activityByDay(boards.keySet(), day),
                flow.doneLast7Days(), flow.donePrev7Days(), people.liveBoards(), people.team(),
                flow.boardsHealth(), flow.aging());
    }

    /**
     * `day` ile biten 7 günün hareket sayısı, eskiden yeniye; hareketsiz gün 0.
     *
     * Gün sınırı UTC gece yarısı: veritabanı zamanı UTC tutar ve günü orada ayırmak
     * tek sorgu demek. `day` ise istemcinin takvim günü, yani pencerenin hangi günle
     * bittiğini istemci söyler. İstemci saat dilimini göndermediği için Türkiye'de
     * 00:00–03:00 arasındaki hareket bir önceki güne yazılır (ADR 0015).
     */
    private List<OverviewResponse.DayCount> activityByDay(Set<Long> boardIds, LocalDate day) {
        LocalDate from = day.minusDays(ACTIVITY_WINDOW_DAYS - 1);
        Map<LocalDate, Long> counts = boardIds.isEmpty() ? Map.of()
                : activityRepository.countPerDay(boardIds, from, day).stream()
                        .collect(Collectors.toMap(d -> LocalDate.parse(d.getDay()),
                                BoardActivityRepository.DayCount::getEvents));
        return from.datesUntil(day.plusDays(1))
                .map(d -> new OverviewResponse.DayCount(d, counts.getOrDefault(d, 0L)))
                .toList();
    }

    /**
     * Sunucu saatiyle tutulan an hangi UTC gününe düşer — activityByDay'in veritabanında
     * yaptığı ayrımın Java tarafı. Canlıda sunucu UTC'de çalıştığı için dönüşüm etkisizdir;
     * yerelde (ör. Europe/Istanbul) iki taraf ancak böyle aynı günü söyler.
     */
    static LocalDate utcDay(LocalDateTime serverTime) {
        return serverTime.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toLocalDate();
    }

    private OverviewResponse.AssignedCard toAssigned(Card card, Map<Long, Board> boards) {
        // Kolonun panosu yüklenmez: id'si zaten kolon satırında (yabancı anahtar).
        Long boardId = card.getColumn().getBoard().getId();
        return new OverviewResponse.AssignedCard(card.getId(), card.getTitle(),
                boardId, boards.get(boardId).getName(),
                card.getColumn().getId(), card.getColumn().getName(), card.getDueDate());
    }

    private List<OverviewResponse.RecentActivity> recentActivity(Map<Long, Board> boards) {
        List<BoardActivity> rows = activityRepository.findByBoardIdInOrderByIdDesc(
                boards.keySet(), PageRequest.of(0, ACTIVITY_LIMIT));
        Map<Long, String> avatars = activityService.actorAvatarUrls(rows);
        return rows.stream()
                .map(a -> new OverviewResponse.RecentActivity(a.getId(), a.getBoardId(),
                        boards.get(a.getBoardId()).getName(), a.getActorName(),
                        a.getUserId() == null ? null : avatars.get(a.getUserId()),
                        a.getType(), a.getDescription(), a.getCreatedAt()))
                .toList();
    }
}
