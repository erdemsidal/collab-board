package com.collabboard.workspace;

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
 * Çalışma alanı REST uçları — arayüzün kenar çubuğunu besleyen sözleşme.
 */
class WorkspaceApiIntegrationTest extends IntegrationTestBase {

    private ResponseEntity<JsonNode> createWorkspace(String token, String name) {
        return rest.exchange("/api/workspaces", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", name), authHeaders(token)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> listWorkspaces(String token) {
        return rest.exchange("/api/workspaces", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), JsonNode.class);
    }

    @Test
    @DisplayName("Çalışma alanı kurulur; kuran kişi OWNER olur ve listesinde görür")
    void alanKurulurVeListelenir() {
        String token = registerAndLogin("Alan", "Kuran", uniqueEmail("wsapi"));

        ResponseEntity<JsonNode> created = createWorkspace(token, "Acme Yazılım A.Ş.");
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("name").asText()).isEqualTo("Acme Yazılım A.Ş.");
        assertThat(created.getBody().get("role").asText()).isEqualTo("OWNER");
        // Slug sadeleştirilir: Türkçe karakterler ve noktalama düşer.
        assertThat(created.getBody().get("slug").asText()).isEqualTo("acme-yazilim-a-s");

        JsonNode list = listWorkspaces(token).getBody();
        assertThat(list.toString()).contains("Acme Yazılım A.Ş.");
    }

    @Test
    @DisplayName("Yalnızca üyesi olunan çalışma alanları listelenir")
    void sadeceUyeOlunanAlanlarListelenir() {
        String ben = registerAndLogin("Ben", "Kisi", uniqueEmail("wsapi"));
        String baskasi = registerAndLogin("Baska", "Kisi", uniqueEmail("wsapi"));

        createWorkspace(ben, "Benim Şirketim");
        createWorkspace(baskasi, "Başkasının Şirketi");

        JsonNode list = listWorkspaces(ben).getBody();
        assertThat(list.toString()).contains("Benim Şirketim");
        assertThat(list.toString()).doesNotContain("Başkasının Şirketi");
    }

    @Test
    @DisplayName("Alan yeniden adlandırılabilir; yetkisiz kişi adlandıramaz")
    void yenidenAdlandirmaYetkiIster() {
        String owner = registerAndLogin("Sahip", "Kisi", uniqueEmail("wsapi"));
        String yabanci = registerAndLogin("Yabanci", "Kisi", uniqueEmail("wsapi"));
        long wsId = createWorkspace(owner, "Eski Ad").getBody().get("id").asLong();

        ResponseEntity<JsonNode> renamed = rest.exchange("/api/workspaces/" + wsId, HttpMethod.PATCH,
                new HttpEntity<>(Map.of("name", "Yeni Ad"), authHeaders(owner)), JsonNode.class);
        assertThat(renamed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(renamed.getBody().get("name").asText()).isEqualTo("Yeni Ad");

        ResponseEntity<JsonNode> reddedilen = rest.exchange("/api/workspaces/" + wsId, HttpMethod.PATCH,
                new HttpEntity<>(Map.of("name", "Ele Geçirildi"), authHeaders(yabanci)), JsonNode.class);
        assertThat(reddedilen.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Alan silinince içindeki panolar da silinir")
    void alanSilinincePanolarDaSilinir() {
        String token = registerAndLogin("Silen", "Kisi", uniqueEmail("wsapi"));
        long wsId = createWorkspace(token, "Kapanacak Şirket").getBody().get("id").asLong();

        long boardId = rest.exchange("/api/boards", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Gidecek Pano", "workspaceId", wsId), authHeaders(token)),
                JsonNode.class).getBody().get("id").asLong();

        ResponseEntity<Void> deleted = rest.exchange("/api/workspaces/" + wsId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)), Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Pano da gitmiş olmalı (V8: ON DELETE CASCADE zinciri).
        ResponseEntity<JsonNode> board = rest.exchange("/api/boards/" + boardId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), JsonNode.class);
        assertThat(board.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);   // artık erişilemez
    }

    @Test
    @DisplayName("Pano özeti workspaceId, rol, kart ve üye sayısını içerir")
    void panoOzetiSayilariIcerir() {
        String token = registerAndLogin("Ozet", "Kisi", uniqueEmail("wsapi"));
        long wsId = createWorkspace(token, "Özet Şirketi").getBody().get("id").asLong();

        rest.exchange("/api/boards", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Özet Panosu", "workspaceId", wsId), authHeaders(token)),
                JsonNode.class);

        JsonNode boards = rest.exchange("/api/boards", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), JsonNode.class).getBody();

        JsonNode row = boards.get(0);
        assertThat(row.has("workspaceId")).isTrue();
        assertThat(row.get("role").asText()).isEqualTo("OWNER");
        assertThat(row.get("cardCount").asInt()).isZero();      // yeni pano, kart yok
        assertThat(row.get("memberCount").asInt()).isEqualTo(1); // sadece kurucu
    }
}
