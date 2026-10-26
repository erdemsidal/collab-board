package com.collabboard.board.template;

import com.collabboard.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pano şablonları (ADR 0021).
 *
 * Kolon adları açan kişinin kayıtlı dilinde yazılır. Bu, eski bir hatayı da
 * kapatıyor: Türkçe kullanıcının yeni panosu "To Do / In Progress / Done" ile
 * açılıyordu.
 */
class BoardTemplateIntegrationTest extends IntegrationTestBase {

    private static final List<String> IDS = List.of("kanban", "software", "content", "personal", "blank");

    /** Şablon kimliği → beklenen kolonlar ("Ad" ya da "Ad|WIP"), dile göre. */
    private static final Map<String, List<String>> TR = Map.of(
            "kanban", List.of("Yapılacak", "Devam ediyor", "Bitti"),
            "software", List.of("Yapılacak", "Yapılıyor|3", "İncelemede|2", "Bitti"),
            "content", List.of("Fikirler", "Yazılıyor", "Onayda", "Yayında"),
            "personal", List.of("Bugün", "Bu hafta", "Sonra", "Bitti"),
            "blank", List.of());

    private static final Map<String, List<String>> EN = Map.of(
            "kanban", List.of("To Do", "In Progress", "Done"),
            "software", List.of("To Do", "In Progress|3", "In Review|2", "Done"),
            "content", List.of("Ideas", "Writing", "Awaiting Approval", "Published"),
            "personal", List.of("Today", "This Week", "Later", "Done"),
            "blank", List.of());

    @Test
    @DisplayName("Şablon listesi kişinin dilinde; varsayılan Basit Kanban ilk sırada")
    void listeIkiDilde() {
        String tr = registerAndLogin("Şablon", "Türkçe", uniqueEmail("sablon"));
        String en = englishUser("Template", "English");

        JsonNode trList = templates(tr);
        assertThat(ids(trList)).containsExactlyElementsOf(IDS);
        assertThat(names(trList)).containsExactly("Basit Kanban", "Yazılım geliştirme", "İçerik planı", "Kişisel", "Boş");
        assertThat(trList.get(0).get("isDefault").asBoolean()).isTrue();
        assertThat(trList.get(1).get("isDefault").asBoolean()).isFalse();
        trList.forEach(t -> assertThat(columns(t.get("columns"))).as(t.get("id").asText())
                .containsExactlyElementsOf(TR.get(t.get("id").asText())));

        JsonNode enList = templates(en);
        assertThat(names(enList)).containsExactly("Simple Kanban", "Software development", "Content plan", "Personal", "Blank");
        enList.forEach(t -> assertThat(columns(t.get("columns"))).as(t.get("id").asText())
                .containsExactlyElementsOf(EN.get(t.get("id").asText())));

        assertThat(rest.getForEntity("/api/boards/templates", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Her şablon iki dilde de panoyu kendi kolonları ve WIP limitleriyle açar; örnek kart yok")
    void herSablonIkiDilde() {
        String tr = registerAndLogin("Pano", "Türkçe", uniqueEmail("sablon"));
        String en = englishUser("Board", "English");

        for (String id : IDS) {
            JsonNode trBoard = create(tr, "TR " + id, id).getBody();
            assertThat(columns(trBoard.get("columns"))).as("tr " + id).containsExactlyElementsOf(TR.get(id));
            JsonNode enBoard = create(en, "EN " + id, id).getBody();
            assertThat(columns(enBoard.get("columns"))).as("en " + id).containsExactlyElementsOf(EN.get(id));

            // Sıra soldan sağa 0, 1, 2...; kartsız
            for (int i = 0; i < trBoard.get("columns").size(); i++) {
                assertThat(trBoard.get("columns").get(i).get("position").asInt()).isEqualTo(i);
                assertThat(trBoard.get("columns").get(i).get("cards")).isEmpty();
            }
            // Yeniden okunan pano da aynı (kolonlar gerçekten kaydedildi)
            assertThat(columns(getBoard(trBoard.get("id").asLong(), tr).getBody().get("columns")))
                    .containsExactlyElementsOf(TR.get(id));
        }
        // Kimlik büyük/küçük harf ve kenar boşluğuna duyarsız
        assertThat(columns(create(tr, "Büyük harf", " SOFTWARE ").getBody().get("columns")))
                .containsExactlyElementsOf(TR.get("software"));
    }

    @Test
    @DisplayName("Şablon seçilmezse Basit Kanban, açanın dilinde: Türkçe kullanıcıya artık To Do değil")
    void varsayilanYerelKolonlar() {
        String tr = registerAndLogin("Varsayılan", "Türkçe", uniqueEmail("sablon"));
        String en = englishUser("Default", "English");

        assertThat(columns(createBoard(tr, "Şablonsuz").get("columns")))
                .containsExactly("Yapılacak", "Devam ediyor", "Bitti");
        assertThat(columns(create(tr, "Boş kimlik", "").getBody().get("columns")))
                .containsExactly("Yapılacak", "Devam ediyor", "Bitti");
        assertThat(columns(createBoard(en, "No template").get("columns")))
                .containsExactly("To Do", "In Progress", "Done");
    }

    @Test
    @DisplayName("Bilinmeyen şablon 400 ve geçerli değerleri söyler; pano açılmaz")
    void bilinmeyenSablon() {
        String token = registerAndLogin("Hatalı", "Şablon", uniqueEmail("sablon"));
        int before = myBoards(token).size();

        ResponseEntity<JsonNode> tr = create(token, "Olmayacak", "scrum");
        assertThat(tr.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(tr.getBody().get("message").asText())
                .isEqualTo("Bilinmeyen pano şablonu. Geçerli değerler: kanban, software, content, personal, blank");

        HttpHeaders english = authHeaders(token);
        english.set(HttpHeaders.ACCEPT_LANGUAGE, "en");
        ResponseEntity<JsonNode> en = rest.exchange("/api/boards", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Nope", "templateId", "scrum"), english), JsonNode.class);
        assertThat(en.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(en.getBody().get("message").asText())
                .isEqualTo("Unknown board template. Valid values: kanban, software, content, personal, blank");

        assertThat(myBoards(token)).hasSize(before);
    }

    @Test
    @DisplayName("Boş şablonla açılan kolonsuz pano: pano, akış ve ana ekran özeti çalışır")
    void kolonsuzPano() {
        String token = registerAndLogin("Kolonsuz", "Pano", uniqueEmail("sablon"));
        long boardId = create(token, "Sıfırdan", "blank").getBody().get("id").asLong();

        assertThat(getBoard(boardId, token).getBody().get("columns")).isEmpty();
        assertThat(get("/api/boards/" + boardId + "/flow", token).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/me/overview?today=" + LocalDate.now(), token).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── Yardımcılar ─────────────────────────────────────────

    /** Hesabı İngilizceye alınmış kullanıcı (profilden dil seçmek gibi). */
    private String englishUser(String firstName, String lastName) {
        String token = registerAndLogin(firstName, lastName, uniqueEmail("template"));
        ResponseEntity<JsonNode> patched = rest.exchange("/api/users/me", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("locale", "en"), authHeaders(token)), JsonNode.class);
        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        return token;
    }

    private JsonNode templates(String token) {
        ResponseEntity<JsonNode> response = get("/api/boards/templates", token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private ResponseEntity<JsonNode> create(String token, String name, String templateId) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("templateId", templateId);
        return rest.exchange("/api/boards", HttpMethod.POST, new HttpEntity<>(body, authHeaders(token)), JsonNode.class);
    }

    private JsonNode myBoards(String token) {
        return get("/api/boards", token).getBody();
    }

    private ResponseEntity<JsonNode> get(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(authHeaders(token)), JsonNode.class);
    }

    private static List<String> ids(JsonNode list) {
        List<String> out = new ArrayList<>();
        list.forEach(t -> out.add(t.get("id").asText()));
        return out;
    }

    private static List<String> names(JsonNode list) {
        List<String> out = new ArrayList<>();
        list.forEach(t -> out.add(t.get("name").asText()));
        return out;
    }

    /** Kolonlar "Ad" ya da WIP limiti varsa "Ad|limit" biçiminde. */
    private static List<String> columns(JsonNode columns) {
        List<String> out = new ArrayList<>();
        columns.forEach(c -> out.add(c.hasNonNull("wipLimit")
                ? c.get("name").asText() + "|" + c.get("wipLimit").asInt()
                : c.get("name").asText()));
        return out;
    }
}
