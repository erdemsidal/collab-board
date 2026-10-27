package com.collabboard.security;

import com.collabboard.support.IntegrationTestBase;
import com.collabboard.support.RawStompClient;
import com.collabboard.support.StompTestClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.messaging.simp.stomp.StompHeaders;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Canlı bağlantının izin listesi (ADR 0025, güvenlik incelemesi K1 ve Y1).
 *
 * Eskiden yalnızca "/topic/board.<sayı>" biçimli abonelik denetleniyordu. Joker
 * adres ("/topic/**", "/queue/**") denetimsiz geçiyor ve Spring'in broker'ı onu
 * kalıp olarak kaydediyordu: kayıtlı herkes bütün panoları ve herkesin
 * bildirimlerini dinleyebiliyordu. Broker adreslerine doğrudan SEND de serbestti.
 *
 * Testler gerçek bir STOMP istemcisiyle saldırganın yapacağını dener; her ret
 * bir ERROR çerçevesi ve kapanan bağlantı demek. Meşru akışlar (abonelikler,
 * "/app" gönderimleri) aynı testlerde çalışmaya devam ediyor.
 */
class StompAccessIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("Üye, arayüzün kullandığı bütün adreslere abone olur ve olayları alır")
    void uyeAbonelikleriKabulEdilir() throws Exception {
        Board board = boardWithMember();

        StompTestClient member = StompTestClient.connect(wsUrl(), board.memberToken)
                .subscribe(board.topic())
                .subscribe(board.topic() + "/presence")
                .subscribe(board.topic() + "/cursors")
                .subscribe("/user/queue/errors")
                .subscribe("/user/queue/notifications");
        StompTestClient owner = StompTestClient.connect(wsUrl(), board.ownerToken);
        StompTestClient.awaitSubscriptions();

        owner.send("/app/board/" + board.id + "/ops",
                Map.of("type", "ADD_CARD", "columnId", board.todoColumn, "title", "Yetkili kart"));

        JsonNode event = member.awaitMessage(board.topic());
        assertThat(event.get("card").get("title").asText()).isEqualTo("Yetkili kart");
        assertThat(member.pollError(1)).as("meşru aboneliklerden hiçbiri reddedilmemeli").isNull();
        assertThat(member.isConnected()).isTrue();

        member.disconnect();
        owner.disconnect();
    }

    @Test
    @DisplayName("Meşru /app gönderimleri çalışır: operasyon, panoya katılma ve imleç")
    void appGonderimleriCalisir() throws Exception {
        Board board = boardWithMember();
        String presence = board.topic() + "/presence";
        String cursors = board.topic() + "/cursors";

        StompTestClient owner = StompTestClient.connect(wsUrl(), board.ownerToken)
                .subscribe(board.topic()).subscribe(presence);
        StompTestClient member = StompTestClient.connect(wsUrl(), board.memberToken).subscribe(cursors);
        StompTestClient.awaitSubscriptions();

        owner.send("/app/board/" + board.id + "/presence/join", Map.of());
        assertThat(owner.awaitMessage(presence).get("type").asText()).isEqualTo("PRESENCE");

        owner.send("/app/board/" + board.id + "/cursor", Map.of("x", 10, "y", 20, "gone", false));
        assertThat(member.awaitMessage(cursors).get("type").asText()).isEqualTo("CURSOR");

        owner.send("/app/board/" + board.id + "/ops",
                Map.of("type", "ADD_CARD", "columnId", board.todoColumn, "title", "Gönderim"));
        assertThat(owner.awaitMessage(board.topic()).get("type").asText()).isEqualTo("ADD_CARD");

        assertThat(owner.pollError(1)).isNull();
        owner.disconnect();
        member.disconnect();
    }

    @Test
    @DisplayName("Üye olmayan, panonun hiçbir yayınına abone olamaz")
    void uyeOlmayanReddedilir() throws Exception {
        Board board = boardWithMember();
        String outsiderToken = registerAndLogin("Dis", "Kisi", uniqueEmail("dis"));

        for (String destination : List.of(board.topic(), board.topic() + "/presence", board.topic() + "/cursors")) {
            StompTestClient outsider = StompTestClient.connect(wsUrl(), outsiderToken).subscribe(destination);

            StompHeaders error = outsider.awaitError();
            assertThat(error.getFirst("x-reason")).as(destination).isEqualTo("board-access");
            assertThat(error.getFirst("message")).isNotBlank();
            assertThat(outsider.awaitClosed(5)).as("ret sonrası bağlantı kapanır").isTrue();
        }
    }

    /**
     * Saldırganın deneyeceği adresler. Hepsi reddedilmeli; hiçbiri bir panonun
     * ya da başkasının kuyruğunun yayınını almamalı.
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "/topic/**",
            "/topic/*",
            "/topic/board.*",
            "/topic/board.**",
            "/topic/board.{id}",
            "/topic/board.?",
            "/topic/board.1?",
            "/topic/board.abc",
            "/topic/board.-1",
            "/topic/board.01",
            "/topic/board.1/**",
            "/topic/board.1/presence/**",
            "/topic/board.1/gizli",
            "/topic/board.99999999999999999999",
            "/topic/other",
            "/queue/**",
            "/queue/notifications",
            "/queue/errors",
            "/queue/notifications-user*",
            "/user/queue/**",
            "/user/queue/*",
            "/user/queue/notifications/**",
            "/user/queue/other",
            "/user/someone@test.com/queue/notifications",
            "/app/board/1/ops",
            "/**",
            "/"
    })
    @DisplayName("Joker, kalıp ve listede olmayan abonelikler reddedilir")
    void jokerVeKalipAboneliklerReddedilir(String destination) throws Exception {
        String token = registerAndLogin("Joker", "Deneme", uniqueEmail("joker"));
        StompTestClient attacker = StompTestClient.connect(wsUrl(), token).subscribe(destination);

        StompHeaders error = attacker.awaitError();
        assertThat(error.getFirst("x-reason")).isIn("forbidden", "board-access");
        assertThat(attacker.awaitClosed(5)).isTrue();
    }

    @Test
    @DisplayName("Joker abonelik bir panonun ve başkasının bildirimlerinin yayınını almaz")
    void jokerAbonelikHicbirSeyAlmaz() throws Exception {
        Board board = boardWithMember();
        String attackerToken = registerAndLogin("Kotu", "Niyet", uniqueEmail("kotu"));

        StompTestClient topics = StompTestClient.connect(wsUrl(), attackerToken).subscribe("/topic/**");
        StompTestClient queues = StompTestClient.connect(wsUrl(), attackerToken).subscribe("/queue/**");
        StompTestClient owner = StompTestClient.connect(wsUrl(), board.ownerToken).subscribe(board.topic());
        StompTestClient.awaitSubscriptions();

        // Panoda bir olay ve üyeye bir bildirim (davet) üret.
        owner.send("/app/board/" + board.id + "/ops",
                Map.of("type", "ADD_CARD", "columnId", board.todoColumn, "title", "Gizli kart"));
        owner.awaitMessage(board.topic());
        invite(board.id, board.ownerToken, uniqueEmail("davetli"), "VIEWER");

        assertThat(topics.poll("/topic/**", 1)).isNull();
        assertThat(queues.poll("/queue/**", 1)).isNull();
        assertThat(topics.awaitError().getFirst("x-reason")).isEqualTo("forbidden");
        assertThat(queues.awaitError().getFirst("x-reason")).isEqualTo("forbidden");
        owner.disconnect();
    }

    @Test
    @DisplayName("Broker adresine doğrudan SEND reddedilir; sahte olay panodakilere ulaşmaz")
    void brokerAdresineGonderimReddedilir() throws Exception {
        Board board = boardWithMember();
        StompTestClient member = StompTestClient.connect(wsUrl(), board.memberToken).subscribe(board.topic());
        StompTestClient.awaitSubscriptions();

        for (String destination : List.of(
                board.topic(),
                board.topic() + "/presence",
                "/queue/notifications",
                "/user/queue/notifications",
                "/user/" + board.memberEmail + "/queue/notifications",
                "/app/board/" + board.id + "/bilinmeyen",
                "/app/board/abc/ops")) {
            String attackerToken = registerAndLogin("Sahte", "Olay", uniqueEmail("sahte"));
            StompTestClient attacker = StompTestClient.connect(wsUrl(), attackerToken);
            attacker.send(destination, Map.of("type", "COMMENT_ADDED", "body", "Faturayı şu IBAN'a yatırın"));

            assertThat(attacker.awaitError().getFirst("x-reason")).as(destination).isEqualTo("forbidden");
            assertThat(attacker.awaitClosed(5)).isTrue();
        }

        // Panonun meşru abonesi hiçbir sahte olay görmedi.
        assertThat(member.poll(board.topic(), 1)).isNull();
        // Üyenin kendisi de broker adresine yazamaz: kural kimliğe değil adrese bağlı.
        member.send(board.topic(), Map.of("type", "CARD_EDITED"));
        assertThat(member.awaitError().getFirst("x-reason")).isEqualTo("forbidden");
    }

    @Test
    @DisplayName("Başkasının kişisel kuyruğuna abone olunamaz")
    void baskasininKuyruguReddedilir() throws Exception {
        Board board = boardWithMember();
        StompTestClient victim = StompTestClient.connect(wsUrl(), board.memberToken).subscribe("/user/queue/notifications");
        String attackerToken = registerAndLogin("Meraklı", "Komsu", uniqueEmail("komsu"));
        StompTestClient.awaitSubscriptions();

        for (String destination : List.of(
                "/user/" + board.memberEmail + "/queue/notifications",
                "/queue/notifications-user" + victim.sessionId(),
                "/queue/errors-user" + victim.sessionId())) {
            StompTestClient attacker = StompTestClient.connect(wsUrl(), attackerToken).subscribe(destination);
            assertThat(attacker.awaitError().getFirst("x-reason")).as(destination).isEqualTo("forbidden");
        }
        victim.disconnect();
    }

    @Test
    @DisplayName("Jetonsuz 'STOMP' çerçevesiyle bağlanılamaz (CONNECT'in diğer adı)")
    void stompCercevesiyleJetonsuzBaglanilamaz() throws Exception {
        RawStompClient raw = RawStompClient.open(wsUrl())
                .send("STOMP", Map.of("accept-version", "1.2", "host", "localhost"), null);

        String error = raw.awaitFrame("ERROR", 5);
        assertThat(error).as("bağlantı reddedilmeli").isNotNull();
        assertThat(error).contains("x-reason:unauthenticated");
        assertThat(raw.awaitClosed(5)).isTrue();
    }

    @Test
    @DisplayName("CONNECT'siz abonelik ve gönderim reddedilir")
    void connectSizCerceveReddedilir() throws Exception {
        RawStompClient subscriber = RawStompClient.open(wsUrl())
                .send("SUBSCRIBE", Map.of("id", "sub-0", "destination", "/topic/**"), null);
        assertThat(subscriber.awaitFrame("ERROR", 5)).contains("x-reason:unauthenticated");

        RawStompClient sender = RawStompClient.open(wsUrl())
                .send("SEND", Map.of("destination", "/topic/board.1", "content-type", "application/json"), "{}");
        assertThat(sender.awaitFrame("ERROR", 5)).contains("x-reason:unauthenticated");
    }

    @Test
    @DisplayName("Uygulamanın kullanmadığı çerçeveler (ACK, BEGIN) reddedilir")
    void kullanilmayanCercevelerReddedilir() throws Exception {
        String token = registerAndLogin("Cerceve", "Deneme", uniqueEmail("cerceve"));
        for (String command : List.of("ACK", "NACK", "BEGIN", "COMMIT", "ABORT")) {
            RawStompClient raw = RawStompClient.open(wsUrl())
                    .send("CONNECT", Map.of("accept-version", "1.2", "host", "localhost",
                            "Authorization", "Bearer " + token), null);
            assertThat(raw.awaitFrame("CONNECTED", 5)).as("geçerli jetonla bağlanır").isNotNull();

            raw.send(command, Map.of("id", "1", "transaction", "tx-1"), null);
            assertThat(raw.awaitFrame("ERROR", 5)).as(command).contains("x-reason:forbidden");
        }
    }

    // ── yardımcılar ──────────────────────────────────────────────────

    private record Board(long id, long todoColumn, String ownerToken, String memberToken, String memberEmail) {
        String topic() {
            return "/topic/board." + id;
        }
    }

    private Board boardWithMember() {
        String ownerToken = registerAndLogin("Erdem", "Sidal", uniqueEmail("izin"));
        JsonNode board = createBoard(ownerToken, "İzin listesi panosu");
        long boardId = board.get("id").asLong();
        String memberEmail = uniqueEmail("izin-uye");
        registerAndLogin("Ayse", "Yilmaz", memberEmail);
        addMember(boardId, ownerToken, memberEmail, "EDITOR");
        return new Board(boardId, board.get("columns").get(0).get("id").asLong(),
                ownerToken, loginToken(memberEmail), memberEmail);
    }
}
