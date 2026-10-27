package com.collabboard.chathook;

import com.collabboard.support.IntegrationTestBase;
import com.collabboard.support.StompTestClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Şifreleme anahtarları tanımlı değilken kanal bildirimleri KAPALI (ADR 0023).
 *
 * AYRI BAĞLAM: anahtarlar boş verildiği için Spring ayrı bir uygulama bağlamı
 * kurar. Uydurma bir varsayılan anahtar yok; uçlar açık bir mesajla 503 döner,
 * panonun geri kalanı hiç etkilenmez.
 */
@TestPropertySource(properties = {
        "app.integrations.enc-password=",
        "app.integrations.enc-salt="
})
class ChatHookUnavailableIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("Anahtar yoksa yöneticiye 503 ve iki dilde açık bir mesaj; yönetici olmayana yine 403")
    void anahtarYoksaKapali() throws Exception {
        String ownerEmail = uniqueEmail("kapali"), editorEmail = uniqueEmail("kapali");
        String owner = registerAndLogin("Kapali", "Ozellik", ownerEmail);
        String editor = registerAndLogin("Kapali", "Editor", editorEmail);
        JsonNode board = createBoard(owner, "Anahtarsız");
        long boardId = board.get("id").asLong();
        addMember(boardId, owner, editorEmail, "EDITOR");
        String path = "/api/boards/" + boardId + "/chat-hooks";

        ResponseEntity<JsonNode> list = rest.exchange(path, HttpMethod.GET, new HttpEntity<>(authHeaders(owner)), JsonNode.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(list.getBody().get("message").asText()).startsWith("Kanal bildirimleri bu sunucuda açık değil.")
                .contains("INTEGRATIONS_ENC_PASSWORD").contains("INTEGRATIONS_ENC_SALT");

        HttpHeaders english = authHeaders(owner);
        english.set(HttpHeaders.ACCEPT_LANGUAGE, "en");
        ResponseEntity<JsonNode> en = rest.exchange(path, HttpMethod.POST, new HttpEntity<>(Map.of(
                "provider", "SLACK", "url", "https://hooks.slack.com/services/T0001/B0002/abc",
                "events", List.of("CARD_CREATED")), english), JsonNode.class);
        assertThat(en.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(en.getBody().get("message").asText()).startsWith("Channel notifications aren't turned on for this server.");

        ResponseEntity<JsonNode> test = rest.exchange(path + "/test", HttpMethod.POST, new HttpEntity<>(Map.of(
                "provider", "SLACK", "url", "https://hooks.slack.com/services/T0001/B0002/abc"), authHeaders(owner)),
                JsonNode.class);
        assertThat(test.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        // Yetki önce gelir: yönetici olmayan sunucunun ayarını öğrenmez.
        assertThat(rest.exchange(path, HttpMethod.GET, new HttpEntity<>(authHeaders(editor)), JsonNode.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Panonun kendisi etkilenmez: kart eklemek çalışır (olay dinleyicisi sessizce döner).
        long columnId = board.get("columns").get(0).get("id").asLong();
        String topic = "/topic/board." + boardId;
        StompTestClient client = StompTestClient.connect(wsUrl(), owner).subscribe(topic);
        StompTestClient.awaitSubscriptions();
        client.send("/app/board/" + boardId + "/ops", Map.of("type", "ADD_CARD", "columnId", columnId, "title", "Çalışıyor"));
        assertThat(client.awaitMessage(topic).get("type").asText()).isEqualTo("ADD_CARD");
        client.disconnect();
    }
}
