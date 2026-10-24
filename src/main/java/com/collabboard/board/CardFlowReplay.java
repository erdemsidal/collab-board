package com.collabboard.board;

import com.collabboard.audit.BoardActivity;
import com.collabboard.board.entity.Card;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Kart olaylarını (ekleme, taşıma, silme) kayıt sırasıyla oynatır ve her kartın
 * hangi kolonda durduğunu, oraya ne zaman girdiğini izler.
 *
 * Akış ölçümü (FlowMetricsService) ile ana ekran özeti aynı oynatıcıyı kullanır.
 * "Kart bu kolona ne zaman girdi?" iki yerde ayrı yazılsaydı biri düzeltildiğinde
 * öbürü başka bir şey söylemeye başlardı: akış paneli "3 gündür bekliyor" derken
 * ana ekran "5 gün" derdi.
 *
 * Durum tutar ve tek kullanımlıktır: bir hesap için oluşturulur, olaylar id
 * sırasıyla verilir (id sırası gerçek uygulanma sırasıdır, bkz. BoardActivityRepository).
 * Kart kimlikleri tüm panolarda tekildir; birden çok panonun olayları tek
 * oynatıcıdan geçebilir.
 */
public final class CardFlowReplay {

    /** Oynatıcının okuduğu olay türleri — sorguyu bunlarla daraltmak için. */
    public static final List<String> CARD_EVENTS = List.of("ADD_CARD", "MOVE_CARD", "DELETE_CARD");

    private static final Logger log = LoggerFactory.getLogger(CardFlowReplay.class);

    /**
     * Bir olayın kart üzerindeki etkisi.
     *
     * @param fromColumnId kartın olaydan önceki kolonu. Eklemede ve eklenme kaydı
     *                     olmayan kartın ilk görülüşünde null: nereden geldiği bilinmiyor.
     * @param enteredAt    kartın fromColumnId'ye giriş anı (fromColumnId null ise null)
     * @param toColumnId   olaydan sonraki kolon; silmede null
     * @param addedAt      kartın eklenme anı; eklenme kaydı yoksa null
     */
    public record Step(String type, long cardId, Long fromColumnId, LocalDateTime enteredAt,
                       Long toColumnId, LocalDateTime at, LocalDateTime addedAt) {

        /** Kart bu adımla verilen kolona başka bir yerden mi girdi? (kolon içi sıralama girmez) */
        public boolean entered(Long columnId) {
            return "MOVE_CARD".equals(type) && columnId != null
                    && columnId.equals(toColumnId) && !columnId.equals(fromColumnId);
        }
    }

    /**
     * Kartın şu anki kolonuna giriş anı.
     *
     * @param estimated giriş olay kaydında yok, kartın oluşturulma anı kullanıldı:
     *                  gerçek bekleme bundan kısa olabilir, uzun olamaz
     */
    public record Entry(LocalDateTime since, boolean estimated) {
    }

    /** Bir kartın oynatma sırasındaki yeri. */
    private static final class Position {
        Long columnId;
        LocalDateTime enteredAt;
        final LocalDateTime addedAt;

        Position(Long columnId, LocalDateTime enteredAt, LocalDateTime addedAt) {
            this.columnId = columnId; this.enteredAt = enteredAt; this.addedAt = addedAt;
        }
    }

    private final ObjectMapper objectMapper;
    private final Map<Long, Position> cards = new HashMap<>();

    public CardFlowReplay(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Olayı uygula. Kartla ilgisi olmayan, yapılandırılmış verisi olmayan (V9'dan
     * önceki) ya da okunamayan kayıt için boş döner ve durum değişmez.
     */
    public Optional<Step> apply(BoardActivity activity) {
        if (activity.getPayload() == null) {
            return Optional.empty();
        }
        try {
            JsonNode e = objectMapper.readTree(activity.getPayload());
            LocalDateTime at = activity.getCreatedAt();

            return switch (activity.getType()) {
                case "ADD_CARD" -> {
                    long cardId = e.get("card").get("id").asLong();
                    long columnId = e.get("columnId").asLong();
                    cards.put(cardId, new Position(columnId, at, at));
                    yield Optional.of(new Step("ADD_CARD", cardId, null, null, columnId, at, at));
                }
                case "MOVE_CARD" -> {
                    long cardId = e.get("cardId").asLong();
                    long target = e.get("toColumnId").asLong();
                    Position position = cards.get(cardId);
                    if (position == null) {
                        // Kartın eklenme olayı kayıtta yok (bu özellikten önce eklenmiş).
                        // Görmezden gelmek yerine BURADAN itibaren izlemeye alıyoruz:
                        // nereye ve ne zaman gittiğini biliyoruz, bir sonraki hareketinden
                        // itibaren bekleme ölçülebilir. Eklenme anı bilinmiyor (addedAt null).
                        cards.put(cardId, new Position(target, at, null));
                        yield Optional.of(new Step("MOVE_CARD", cardId, null, null, target, at, null));
                    }
                    Step step = new Step("MOVE_CARD", cardId, position.columnId, position.enteredAt,
                            target, at, position.addedAt);
                    position.columnId = target;
                    position.enteredAt = at;
                    yield Optional.of(step);
                }
                case "DELETE_CARD" -> {
                    long cardId = e.get("cardId").asLong();
                    Position position = cards.remove(cardId);
                    yield position == null ? Optional.<Step>empty()
                            : Optional.of(new Step("DELETE_CARD", cardId, position.columnId,
                                    position.enteredAt, null, at, position.addedAt));
                }
                default -> Optional.empty();   // düzenleme, kolon ve yorum olayları kartın yerini değiştirmez
            };
        } catch (Exception ex) {
            log.warn("Kart olayı okunamadı, atlandı: activityId={}", activity.getId(), ex);
            return Optional.empty();
        }
    }

    /**
     * Kartın şu anki kolonuna ne zamandan beri durduğu. Olay kaydında izi yoksa
     * oluşturulma anı (estimated); o da yoksa boş — uydurmaktansa ölçmeyelim.
     */
    public Optional<Entry> entry(Card card) {
        Position position = cards.get(card.getId());
        if (position != null) {
            return Optional.of(new Entry(position.enteredAt, false));
        }
        return Optional.ofNullable(card.getCreatedAt()).map(at -> new Entry(at, true));
    }

    /** Kolonda hâlâ duran (kaydı izlenen) kartlardan en erken giren ne zaman girdi. */
    public Optional<LocalDateTime> earliestEntryIn(Long columnId) {
        return cards.values().stream()
                .filter(p -> p.columnId.equals(columnId))
                .map(p -> p.enteredAt)
                .min(LocalDateTime::compareTo);
    }
}
