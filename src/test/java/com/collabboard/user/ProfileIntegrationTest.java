package com.collabboard.user;

import com.collabboard.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Profil: ad, şifre ve fotoğraf. Her işlem yalnızca oturumdaki kişinin kendi hesabına. */
class ProfileIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("Ad ve soyad değiştirilir; e-posta değiştirilemez")
    void adDegisirEpostaDegismez() {
        String email = uniqueEmail("profil");
        String token = registerAndLogin("Eski", "Ad", email);

        ResponseEntity<JsonNode> updated = rest.exchange("/api/users/me", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("firstName", "  Yeni ", "lastName", "Soyad"), authHeaders(token)),
                JsonNode.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().get("firstName").asText()).isEqualTo("Yeni");

        // E-postayı değiştirme girişimi sessizce yok sayılmaz, açıkça reddedilir.
        ResponseEntity<JsonNode> sneaky = rest.exchange("/api/users/me", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("firstName", "Yeni", "lastName", "Soyad", "email", "baska@ornek.com"),
                        authHeaders(token)), JsonNode.class);
        assertThat(sneaky.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        JsonNode me = rest.exchange("/api/users/me", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), JsonNode.class).getBody();
        assertThat(me.get("email").asText()).isEqualTo(email);
    }

    @Test
    @DisplayName("Boş ad kabul edilmez")
    void bosAdReddedilir() {
        String token = registerAndLogin("Dolu", "Ad", uniqueEmail("profil"));

        ResponseEntity<JsonNode> response = rest.exchange("/api/users/me", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("firstName", "", "lastName", "Soyad"), authHeaders(token)), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Şifre değişir: yeni şifreyle girilir, diğer oturumlar kapanır, bu cihaz yeni jetonla devam eder")
    void sifreDegisir() {
        String email = uniqueEmail("profil");
        String token = registerAndLogin("Sifre", "Degistiren", email);
        String otherDeviceRefresh = rest.postForEntity("/api/auth/login",
                Map.of("email", email, "password", PASSWORD), JsonNode.class).getBody().get("refreshToken").asText();

        ResponseEntity<JsonNode> changed = changePassword(token, PASSWORD, "yepyeniSifre42");

        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
        String newRefresh = changed.getBody().get("refreshToken").asText();

        // Diğer cihazın oturumu kapandı...
        assertThat(refresh(otherDeviceRefresh).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // ...bu cihazınki yeni jetonla sürüyor.
        assertThat(refresh(newRefresh).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(loginStatus(email, PASSWORD)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(loginStatus(email, "yepyeniSifre42")).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Mevcut şifre yanlışsa şifre değişmez")
    void yanlisMevcutSifre() {
        String email = uniqueEmail("profil");
        String token = registerAndLogin("Yanlis", "Sifre", email);

        ResponseEntity<JsonNode> response = changePassword(token, "bu-degil", "yepyeniSifre42");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("message").asText()).contains("Mevcut şifre yanlış");
        assertThat(loginStatus(email, PASSWORD)).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Yüklenen fotoğraf herkese açık adresinden kendi türüyle sunulur; değişince adres de değişir")
    void fotografYuklenir() throws Exception {
        String token = registerAndLogin("Foto", "Grafli", uniqueEmail("profil"));

        ResponseEntity<JsonNode> first = uploadAvatar(token, png(), "ben.png");
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        String url = first.getBody().get("avatarUrl").asText();
        assertThat(url).startsWith("/api/avatars/");

        // Oturum jetonu olmadan: <img src> böyle istek atar.
        ResponseEntity<byte[]> image = rest.getForEntity(url, byte[].class);
        assertThat(image.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(image.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(image.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");

        String secondUrl = uploadAvatar(token, png(), "yeni.png").getBody().get("avatarUrl").asText();
        assertThat(secondUrl).isNotEqualTo(url);
        // Eski adres artık yok — önbellekte kalan eski fotoğraf yeniden istenemez.
        assertThat(rest.getForEntity(url, byte[].class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<JsonNode> removed = rest.exchange("/api/users/me/avatar", HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)), JsonNode.class);
        assertThat(removed.getBody().path("avatarUrl").asText(null)).isNull();
    }

    @Test
    @DisplayName("Görsel olmayan dosya, uzantısı ve türü görsel gibi gösterilse de reddedilir")
    void gorselOlmayanReddedilir() {
        String token = registerAndLogin("Kotu", "Niyet", uniqueEmail("profil"));

        // Uzantı .png, istemcinin söylediği tür image/png — ama içerik bir betik.
        byte[] script = "<svg onload=alert(1)></svg>".getBytes();
        ResponseEntity<JsonNode> response = uploadAvatar(token, script, "zararsiz.png");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("message").asText()).contains("PNG, JPEG ya da WebP");
    }

    @Test
    @DisplayName("Fotoğraf üye listesinde görünür")
    void fotografUyeListesinde() throws Exception {
        String email = uniqueEmail("profil");
        String token = registerAndLogin("Liste", "Fotolu", email);
        String url = uploadAvatar(token, png(), "ben.png").getBody().get("avatarUrl").asText();
        long boardId = createBoard(token, "Fotoğraflı").get("id").asLong();

        JsonNode members = rest.exchange("/api/boards/" + boardId + "/members", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), JsonNode.class).getBody();
        assertThat(members.get(0).get("avatarUrl").asText()).isEqualTo(url);
    }

    // ── yardımcılar ──────────────────────────────────────────────────

    private ResponseEntity<JsonNode> changePassword(String token, String current, String next) {
        return rest.exchange("/api/users/me/password", HttpMethod.POST,
                new HttpEntity<>(Map.of("currentPassword", current, "newPassword", next), authHeaders(token)),
                JsonNode.class);
    }

    private ResponseEntity<JsonNode> refresh(String refreshToken) {
        return rest.postForEntity("/api/auth/refresh", Map.of("refreshToken", refreshToken), JsonNode.class);
    }

    private HttpStatus loginStatus(String email, String password) {
        return HttpStatus.valueOf(rest.postForEntity("/api/auth/login",
                Map.of("email", email, "password", password), JsonNode.class).getStatusCode().value());
    }

    private ResponseEntity<JsonNode> uploadAvatar(String token, byte[] bytes, String filename) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.IMAGE_PNG);
        body.add("file", new HttpEntity<>(new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        }, partHeaders));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange("/api/users/me/avatar", HttpMethod.PUT, new HttpEntity<>(body, headers), JsonNode.class);
    }

    /** Gerçek, geçerli bir PNG — imzası ProfileService'in tanıdığı biçimde. */
    private static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
