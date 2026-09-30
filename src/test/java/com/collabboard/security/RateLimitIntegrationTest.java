package com.collabboard.security;

import com.collabboard.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hız sınırı: kimliksiz uçlara IP başına kota.
 *
 * Diğer testlerde sınır KAPALI (bkz. IntegrationTestBase) çünkü hepsi aynı
 * IP'den onlarca hesap açıyor. Burada tekrar açılıyor — alt sınıfın kendi
 * @TestPropertySource'u üsttekini geçersiz kılar.
 *
 * Her test AYRI bir politikayı deniyor. Politikaların kovaları birbirinden
 * bağımsız olduğu için testler birbirinin kotasını tüketmiyor; aksi hâlde
 * çalışma sırasına bağımlı, kırılgan testler olurdu.
 */
@TestPropertySource(properties = "app.rate-limit.enabled=true")
class RateLimitIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("Kayıt sınırı aşılınca 429 döner ve ne kadar bekleneceği bildirilir")
    void kayitSiniriAsilir() {
        // Politika: saatte 5. Altıncı istek reddedilmeli.
        for (int i = 1; i <= 5; i++) {
            ResponseEntity<JsonNode> ok = register(uniqueEmail("hiz-kayit"));
            assertThat(ok.getStatusCode())
                    .as("%d. kayıt sınırın altında olmalı", i)
                    .isEqualTo(HttpStatus.CREATED);
        }

        ResponseEntity<JsonNode> blocked = register(uniqueEmail("hiz-kayit"));

        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(blocked.getBody().get("message").asText()).contains("Çok fazla kayıt isteği");
        // Retry-After standarttır; istemci ne zaman tekrar deneyeceğini buradan öğrenir.
        assertThat(blocked.getHeaders().getFirst("Retry-After")).isEqualTo("3600");
        // Gövde, uygulamanın her yerdeki hata biçimiyle aynı olmalı.
        assertThat(blocked.getBody().get("status").asInt()).isEqualTo(429);
        assertThat(blocked.getBody().get("path").asText()).isEqualTo("/api/auth/register");
    }

    @Test
    @DisplayName("Doğrulama postası sınırı, bir gelen kutusunun postaya boğulmasını engeller")
    void yenidenGonderimSiniriAsilir() {
        // Politika: saatte 3. En sıkı sınır burada, çünkü bedeli bizim değil,
        // saldırganın seçtiği kişinin gelen kutusu.
        String email = uniqueEmail("hiz-resend");
        for (int i = 1; i <= 3; i++) {
            assertThat(resend(email).getStatusCode())
                    .as("%d. istek sınırın altında olmalı", i)
                    .isEqualTo(HttpStatus.OK);
        }

        ResponseEntity<JsonNode> blocked = resend(email);

        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(blocked.getBody().get("message").asText()).contains("çok sık istendi");
    }

    @Test
    @DisplayName("Giriş sınırı, şifre deneme saldırısını kesintiye uğratır")
    void girisSiniriAsilir() {
        // Politika: dakikada 10. Yanlış şifreyle deneniyor — sınır, girişin
        // başarılı olup olmamasından bağımsız çalışmalı.
        String email = uniqueEmail("hiz-giris");
        for (int i = 1; i <= 10; i++) {
            assertThat(login(email, "yanlis-sifre").getStatusCode())
                    .as("%d. deneme sınıra takılmamalı", i)
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        ResponseEntity<JsonNode> blocked = login(email, "yanlis-sifre");

        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(blocked.getBody().get("message").asText()).contains("Çok fazla giriş denemesi");
        assertThat(blocked.getHeaders().getFirst("Retry-After")).isEqualTo("60");
    }

    @Test
    @DisplayName("Sınırlanmayan uçlar etkilenmez")
    void sinirlanmayanUclarEtkilenmez() {
        // Sağlık ucu GET; filtre yalnızca korunan POST uçlarına bakar. Onlarca
        // çağrı bile 429 üretmemeli, yoksa sağlık kontrolü kendini kilitlerdi.
        for (int i = 0; i < 40; i++) {
            assertThat(rest.getForEntity("/actuator/health", JsonNode.class).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
        }
    }

    // ── yardımcılar ──────────────────────────────────────────────────

    private ResponseEntity<JsonNode> register(String email) {
        return rest.postForEntity("/api/auth/register", Map.of(
                "firstName", "Hiz", "lastName", "Testi",
                "email", email, "password", PASSWORD), JsonNode.class);
    }

    private ResponseEntity<JsonNode> login(String email, String password) {
        return rest.postForEntity("/api/auth/login",
                Map.of("email", email, "password", password), JsonNode.class);
    }

    private ResponseEntity<JsonNode> resend(String email) {
        return rest.postForEntity("/api/auth/resend-verification",
                Map.of("email", email), JsonNode.class);
    }
}
