package com.collabboard.board;

import com.collabboard.support.IntegrationTestBase;
import com.collabboard.support.StompTestClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Akış sağlığı: kartların kolonlarda bekleme süresi ve darboğaz tespiti.
 *
 * Test içinde olaylar milisaniyeler içinde olduğu için süreler sıfıra yakındır;
 * bu yüzden doğrulama SÜRENİN KENDİSİNE değil, ölçümün doğru kurulduğuna bakar:
 * hangi kolonda kaç kart var, kaç geçiş ölçüldü, çevrim süresi hesaplandı mı.
 */
class FlowMetricsIntegrationTest extends IntegrationTestBase {

    @org.springframework.beans.factory.annotation.Autowired
    com.collabboard.audit.BoardActivityRepository activityRepository;

    @Test
    @DisplayName("Akış ölçümü kolon doluluğunu, geçişleri ve çevrim süresini hesaplar")
    void akisOlcumuHesaplanir() throws Exception {
        String token = registerAndLogin("Akis", "Analisti", uniqueEmail("flow"));
        JsonNode board = createBoard(token, "Akış Testi");
        long boardId = board.get("id").asLong();
        long todo = board.get("columns").get(0).get("id").asLong();
        long progress = board.get("columns").get(1).get("id").asLong();
        long done = board.get("columns").get(2).get("id").asLong();
        String topic = "/topic/board." + boardId;
        String ops = "/app/board/" + boardId + "/ops";

        StompTestClient client = StompTestClient.connect(wsUrl(), token).subscribe(topic);
        StompTestClient.awaitSubscriptions();

        // Bir kart tüm akışı tamamlıyor: To Do → In Progress → Done
        client.send(ops, Map.of("type", "ADD_CARD", "columnId", todo, "title", "Akan kart"));
        long akan = client.awaitMessage(topic).get("card").get("id").asLong();
        client.send(ops, Map.of("type", "MOVE_CARD", "cardId", akan,
                "toColumnId", progress, "position", 0, "baseVersion", 0));
        client.awaitMessage(topic);
        client.send(ops, Map.of("type", "MOVE_CARD", "cardId", akan,
                "toColumnId", done, "position", 0, "baseVersion", 1));
        client.awaitMessage(topic);

        // İki kart To Do'da bekliyor (henüz hiç taşınmadı)
        client.send(ops, Map.of("type", "ADD_CARD", "columnId", todo, "title", "Bekleyen 1"));
        client.awaitMessage(topic);
        client.send(ops, Map.of("type", "ADD_CARD", "columnId", todo, "title", "Bekleyen 2"));
        client.awaitMessage(topic);
        client.disconnect();

        JsonNode flow = rest.exchange("/api/boards/" + boardId + "/flow", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), JsonNode.class).getBody();

        JsonNode columns = flow.get("columns");
        assertThat(columns).hasSize(3);

        // Kolonlardaki güncel doluluk
        assertThat(columns.get(0).get("cardCount").asInt()).isEqualTo(2);   // To Do
        assertThat(columns.get(1).get("cardCount").asInt()).isZero();       // In Progress (geçildi)
        assertThat(columns.get(2).get("cardCount").asInt()).isEqualTo(1);   // Done

        // İki taşıma = iki tamamlanmış geçiş ölçüldü
        assertThat(flow.get("measuredTransitions").asInt()).isEqualTo(2);

        // Kart son kolona ulaştığı için çevrim süresi hesaplanabildi
        assertThat(flow.get("averageCycleTimeSeconds").isNull()).isFalse();

        // To Do'dan bir kart geçtiği için ortalama bekleme hesaplanabiliyor;
        // hâlâ bekleyen iki kart olduğu için "en eski kartın yaşı" da dolu.
        assertThat(columns.get(0).get("avgDwellSeconds").isNull()).isFalse();
        assertThat(columns.get(0).get("oldestCardSeconds").isNull()).isFalse();

        // Done'dan hiç kart çıkmadı → oradan geçiş ölçülmedi, ortalama yok.
        assertThat(columns.get(2).get("avgDwellSeconds").isNull()).isTrue();
        assertThat(columns.get(2).get("oldestCardSeconds").isNull()).isFalse();

        // Darboğaz, ortalaması ölçülebilen kolonlardan biri olmalı (To Do ya da In Progress)
        long bottleneck = flow.get("bottleneckColumnId").asLong();
        assertThat(bottleneck).isIn(todo, progress);
    }

    @Test
    @DisplayName("Eklenme kaydı olmayan eski kartlar, ilk taşımadan sonra ölçüme dahil olur")
    void kaydiEksikKartlarSonradanOlcumeGirer() throws Exception {
        String token = registerAndLogin("Eski", "Kart", uniqueEmail("flow"));
        JsonNode board = createBoard(token, "Eksik Kayıt Testi");
        long boardId = board.get("id").asLong();
        long todo = board.get("columns").get(0).get("id").asLong();
        long progress = board.get("columns").get(1).get("id").asLong();
        long done = board.get("columns").get(2).get("id").asLong();
        String topic = "/topic/board." + boardId;
        String ops = "/app/board/" + boardId + "/ops";

        StompTestClient client = StompTestClient.connect(wsUrl(), token).subscribe(topic);
        StompTestClient.awaitSubscriptions();

        client.send(ops, Map.of("type", "ADD_CARD", "columnId", todo, "title", "Eski kart"));
        long cardId = client.awaitMessage(topic).get("card").get("id").asLong();

        // Bu özellikten önce eklenmiş bir kartı taklit et: eklenme kaydının
        // yapılandırılmış verisini sil, yalnızca metin açıklaması kalsın.
        activityRepository.findByBoardIdOrderByIdAsc(boardId).stream()
                .filter(a -> "ADD_CARD".equals(a.getType()))
                .forEach(a -> { a.setPayload(null); activityRepository.save(a); });

        client.send(ops, Map.of("type", "MOVE_CARD", "cardId", cardId,
                "toColumnId", progress, "position", 0, "baseVersion", 0));
        client.awaitMessage(topic);
        client.send(ops, Map.of("type", "MOVE_CARD", "cardId", cardId,
                "toColumnId", done, "position", 0, "baseVersion", 1));
        client.awaitMessage(topic);
        client.disconnect();

        JsonNode flow = rest.exchange("/api/boards/" + boardId + "/flow", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), JsonNode.class).getBody();

        // İlk taşıma kartı izlemeye alır, ikincisi ölçülebilir bir geçiş üretir.
        assertThat(flow.get("measuredTransitions").asInt()).isEqualTo(1);
        assertThat(flow.get("columns").get(1).get("avgDwellSeconds").isNull()).isFalse();

        // Kartın eklenme anı bilinmediği için çevrim süresine katılmaz.
        assertThat(flow.get("averageCycleTimeSeconds").isNull()).isTrue();

        // Doluluk panonun kendisinden okunduğu için kayıt eksikliğinden etkilenmez.
        assertThat(flow.get("columns").get(2).get("cardCount").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("Hiç hareket olmayan panoda ölçüm boş döner, hata vermez")
    void bosPanodaOlcumBosDoner() {
        String token = registerAndLogin("Akis", "Analisti", uniqueEmail("flow"));
        long boardId = createBoard(token, "Boş Pano").get("id").asLong();

        JsonNode flow = rest.exchange("/api/boards/" + boardId + "/flow", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), JsonNode.class).getBody();

        assertThat(flow.get("measuredTransitions").asInt()).isZero();
        assertThat(flow.get("averageCycleTimeSeconds").isNull()).isTrue();
        assertThat(flow.get("bottleneckColumnId").isNull()).isTrue();
        flow.get("columns").forEach(c -> assertThat(c.get("cardCount").asInt()).isZero());
    }
}
