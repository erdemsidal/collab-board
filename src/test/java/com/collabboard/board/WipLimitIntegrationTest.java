package com.collabboard.board;

import com.collabboard.support.IntegrationTestBase;
import com.collabboard.support.StompTestClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WIP limiti: bir kolonda aynı anda bulunabilecek kart sayısının sınırı.
 *
 * Sınır dolduğunda operasyon reddedilir — bu bir hata değil, Kanban kuralının
 * çalışmasıdır. Testler kuralın hem uygulandığını hem de fazla uygulanmadığını
 * (kolon içi sıralamayı engellemediğini) doğrular.
 */
class WipLimitIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("Limit dolu kolona yeni kart eklenemez, gönderene gerekçe döner")
    void doluKolonaKartEklenemez() throws Exception {
        String token = registerAndLogin("Wip", "Test", uniqueEmail("wip"));
        JsonNode board = createBoard(token, "WIP Testi");
        long boardId = board.get("id").asLong();
        long todo = board.get("columns").get(0).get("id").asLong();
        String topic = "/topic/board." + boardId;
        String ops = "/app/board/" + boardId + "/ops";

        StompTestClient client = StompTestClient.connect(wsUrl(), token)
                .subscribe(topic).subscribe("/user/queue/errors");
        StompTestClient.awaitSubscriptions();

        client.send(ops, Map.of("type", "SET_WIP_LIMIT", "columnId", todo, "limit", 2));
        assertThat(client.awaitMessage(topic).get("wipLimit").asInt()).isEqualTo(2);

        client.send(ops, Map.of("type", "ADD_CARD", "columnId", todo, "title", "Birinci"));
        client.awaitMessage(topic);
        client.send(ops, Map.of("type", "ADD_CARD", "columnId", todo, "title", "İkinci"));
        client.awaitMessage(topic);

        // Üçüncü kart limiti aşar.
        client.send(ops, Map.of("type", "ADD_CARD", "columnId", todo, "title", "Üçüncü"));

        JsonNode rejection = client.awaitMessage("/user/queue/errors");
        assertThat(rejection.get("type").asText()).isEqualTo("OP_REJECTED");
        assertThat(rejection.get("reason").asText()).isEqualTo("WIP_LIMIT");
        assertThat(rejection.get("message").asText()).contains("To Do");

        // Reddedilen operasyon yayına çıkmamalı: diğer kullanıcılar hayalet kart görmesin.
        assertThat(client.poll(topic, 1)).isNull();
        client.disconnect();

        JsonNode fresh = rest.exchange("/api/boards/" + boardId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), JsonNode.class).getBody();
        assertThat(fresh.get("columns").get(0).get("cards")).hasSize(2);
        assertThat(fresh.get("columns").get(0).get("wipLimit").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("Limit dolu kolona kart taşınamaz")
    void doluKolonaKartTasinamaz() throws Exception {
        String token = registerAndLogin("Wip", "Tasima", uniqueEmail("wip"));
        JsonNode board = createBoard(token, "Taşıma Testi");
        long boardId = board.get("id").asLong();
        long todo = board.get("columns").get(0).get("id").asLong();
        long progress = board.get("columns").get(1).get("id").asLong();
        String topic = "/topic/board." + boardId;
        String ops = "/app/board/" + boardId + "/ops";

        StompTestClient client = StompTestClient.connect(wsUrl(), token)
                .subscribe(topic).subscribe("/user/queue/errors");
        StompTestClient.awaitSubscriptions();

        client.send(ops, Map.of("type", "SET_WIP_LIMIT", "columnId", progress, "limit", 1));
        client.awaitMessage(topic);

        client.send(ops, Map.of("type", "ADD_CARD", "columnId", todo, "title", "Kart A"));
        long a = client.awaitMessage(topic).get("card").get("id").asLong();
        client.send(ops, Map.of("type", "ADD_CARD", "columnId", todo, "title", "Kart B"));
        long b = client.awaitMessage(topic).get("card").get("id").asLong();

        // İlk taşıma limiti tam doldurur.
        client.send(ops, Map.of("type", "MOVE_CARD", "cardId", a,
                "toColumnId", progress, "position", 0, "baseVersion", 0));
        client.awaitMessage(topic);

        // İkincisi reddedilmeli.
        client.send(ops, Map.of("type", "MOVE_CARD", "cardId", b,
                "toColumnId", progress, "position", 0, "baseVersion", 0));

        JsonNode rejection = client.awaitMessage("/user/queue/errors");
        assertThat(rejection.get("reason").asText()).isEqualTo("WIP_LIMIT");
        assertThat(client.poll(topic, 1)).isNull();
        client.disconnect();

        JsonNode fresh = rest.exchange("/api/boards/" + boardId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), JsonNode.class).getBody();
        assertThat(fresh.get("columns").get(0).get("cards")).hasSize(1);   // B To Do'da kaldı
        assertThat(fresh.get("columns").get(1).get("cards")).hasSize(1);   // sadece A geçti
    }

    @Test
    @DisplayName("Limitteki kolonda kartlar yine de sıralanabilir")
    void limittekiKolondaSiralamaCalisir() throws Exception {
        String token = registerAndLogin("Wip", "Sira", uniqueEmail("wip"));
        JsonNode board = createBoard(token, "Sıralama Testi");
        long boardId = board.get("id").asLong();
        long todo = board.get("columns").get(0).get("id").asLong();
        String topic = "/topic/board." + boardId;
        String ops = "/app/board/" + boardId + "/ops";

        StompTestClient client = StompTestClient.connect(wsUrl(), token)
                .subscribe(topic).subscribe("/user/queue/errors");
        StompTestClient.awaitSubscriptions();

        client.send(ops, Map.of("type", "SET_WIP_LIMIT", "columnId", todo, "limit", 2));
        client.awaitMessage(topic);
        client.send(ops, Map.of("type", "ADD_CARD", "columnId", todo, "title", "Önce"));
        client.awaitMessage(topic);
        client.send(ops, Map.of("type", "ADD_CARD", "columnId", todo, "title", "Sonra"));
        long sonra = client.awaitMessage(topic).get("card").get("id").asLong();

        // Kolon tam limitte. Kart kendi kolonunda yer değiştirebilmeli: taşınan kart
        // kendi yerini işgal ediyor sayılırsa dolu kolonda sıralama imkânsız olurdu.
        client.send(ops, Map.of("type", "MOVE_CARD", "cardId", sonra,
                "toColumnId", todo, "position", 0, "baseVersion", 0));

        JsonNode moved = client.awaitMessage(topic);
        assertThat(moved.get("type").asText()).isEqualTo("MOVE_CARD");
        assertThat(moved.get("position").asInt()).isZero();
        assertThat(client.poll("/user/queue/errors", 1)).isNull();
        client.disconnect();
    }

    @Test
    @DisplayName("Sınır kaldırılınca kolon yeniden kart kabul eder")
    void sinirKaldirilincaKartKabulEdilir() throws Exception {
        String token = registerAndLogin("Wip", "Kaldir", uniqueEmail("wip"));
        JsonNode board = createBoard(token, "Sınır Kaldırma");
        long boardId = board.get("id").asLong();
        long todo = board.get("columns").get(0).get("id").asLong();
        String topic = "/topic/board." + boardId;
        String ops = "/app/board/" + boardId + "/ops";

        StompTestClient client = StompTestClient.connect(wsUrl(), token)
                .subscribe(topic).subscribe("/user/queue/errors");
        StompTestClient.awaitSubscriptions();

        client.send(ops, Map.of("type", "SET_WIP_LIMIT", "columnId", todo, "limit", 1));
        client.awaitMessage(topic);
        client.send(ops, Map.of("type", "ADD_CARD", "columnId", todo, "title", "Tek kart"));
        client.awaitMessage(topic);

        client.send(ops, Map.of("type", "ADD_CARD", "columnId", todo, "title", "Reddedilecek"));
        assertThat(client.awaitMessage("/user/queue/errors").get("reason").asText()).isEqualTo("WIP_LIMIT");

        // limit alanı yok → null → sınırsız. Map.of null kabul etmediği için HashMap.
        Map<String, Object> kaldir = new HashMap<>();
        kaldir.put("type", "SET_WIP_LIMIT");
        kaldir.put("columnId", todo);
        kaldir.put("limit", null);
        client.send(ops, kaldir);
        assertThat(client.awaitMessage(topic).get("wipLimit").isNull()).isTrue();

        client.send(ops, Map.of("type", "ADD_CARD", "columnId", todo, "title", "Artık kabul"));
        assertThat(client.awaitMessage(topic).get("card").get("title").asText()).isEqualTo("Artık kabul");
        client.disconnect();
    }
}
