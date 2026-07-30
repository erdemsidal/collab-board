package com.collabboard.board;

import com.collabboard.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pano REST uçlarının entegrasyon testleri — "tam fotoğraf" kanalı.
 */
class BoardApiIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("Token olmadan pano oluşturulamaz")
    void tokensizIstekReddedilir() {
        ResponseEntity<JsonNode> response = rest.postForEntity("/api/boards",
                Map.of("name", "Gizli Pano"), JsonNode.class);

        // Kimlik doğrulaması yoksa Spring Security isteği durdurur (ADR 0005).
        assertThat(response.getStatusCode())
                .isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Pano oluşturulunca 3 varsayılan kolonla gelir")
    void panoUcVarsayilanKolonlaOlusur() {
        String token = registerAndLogin("Erdem", "Sidal", uniqueEmail("board"));

        ResponseEntity<JsonNode> response = rest.exchange("/api/boards", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Sprint 1"), authHeaders(token)), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode board = response.getBody();
        assertThat(board.get("name").asText()).isEqualTo("Sprint 1");

        JsonNode columns = board.get("columns");
        assertThat(columns).hasSize(3);
        assertThat(columns.get(0).get("name").asText()).isEqualTo("To Do");
        assertThat(columns.get(1).get("name").asText()).isEqualTo("In Progress");
        assertThat(columns.get(2).get("name").asText()).isEqualTo("Done");
        // Kolonlar soldan sağa sıralı olmalı
        assertThat(columns.get(0).get("position").asInt()).isZero();
        assertThat(columns.get(2).get("position").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("Panonun tam hâli (kolonlar + kartlar) getirilebilir")
    void panoTamHaliyleGetirilir() {
        String token = registerAndLogin("Ayse", "Yilmaz", uniqueEmail("board"));
        long boardId = createBoard(token, "Fotoğraf Testi").get("id").asLong();

        ResponseEntity<JsonNode> response = rest.exchange("/api/boards/" + boardId,
                HttpMethod.GET, new HttpEntity<>(authHeaders(token)), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode board = response.getBody();
        assertThat(board.get("id").asLong()).isEqualTo(boardId);
        // İç içe yapı: her kolon kendi kart listesini taşır (henüz boş).
        assertThat(board.get("columns").get(0).get("cards")).isEmpty();
    }

    @Test
    @DisplayName("Olmayan pano 404 döner")
    void olmayanPano404Doner() {
        String token = registerAndLogin("Cem", "Demir", uniqueEmail("board"));

        ResponseEntity<JsonNode> response = rest.exchange("/api/boards/999999",
                HttpMethod.GET, new HttpEntity<>(authHeaders(token)), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Boş pano adı doğrulama hatası verir")
    void bosPanoAdiReddedilir() {
        String token = registerAndLogin("Deniz", "Kaya", uniqueEmail("board"));

        ResponseEntity<JsonNode> response = rest.exchange("/api/boards", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "   "), authHeaders(token)), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
