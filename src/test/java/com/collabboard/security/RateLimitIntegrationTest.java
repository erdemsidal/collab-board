package com.collabboard.security;

import com.collabboard.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
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
 *
 * VEKİL TAKLİDİ (ADR 0025): Test istemcisi 127.0.0.1'den bağlanır; bu adres
 * Tomcat'in güvendiği vekiller arasında. Yani "X-Forwarded-For: <sahte>, <gerçek>"
 * göndermek, Railway'in vekilinin yaptığını taklit eder: istemcinin uydurduğu
 * değer solda, vekilin eklediği gerçek adres sağda. Böyle testler kendi
 * "gerçek" adreslerini kullanır; 127.0.0.1'in kovalarına dokunmazlar.
 *
 * Hesap başına penceresi burada 3 saniye: "pencere dolunca giriş yine çalışır"
 * 15 dakika beklemeden sınanabilsin.
 */
@TestPropertySource(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.login-account-window=3s"
})
class RateLimitIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("Uydurma X-Forwarded-For sınırı sıfırlamaz: vekilin eklediği gerçek adres sayılır")
    void sahteXffSiniriSifirlamaz() {
        String realIp = "203.0.113.10";
        String email = uniqueEmail("hiz-xff");
        for (int i = 1; i <= 10; i++) {
            // Her istekte başka bir "ilk değer": eski kod bunu istemcinin adresi sayıyordu.
            ResponseEntity<JsonNode> attempt = loginVia("198.51.100." + i + ", " + realIp, email, "yanlis-sifre");
            assertThat(attempt.getStatusCode()).as("%d. deneme", i).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        ResponseEntity<JsonNode> blocked = loginVia("198.51.100.99, " + realIp, email, "yanlis-sifre");

        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(blocked.getBody().get("message").asText()).contains("Çok fazla giriş denemesi");
    }

    @Test
    @DisplayName("Hesap başına sınır IP'den bağımsız; pencere dolunca doğru şifreyle giriş yine çalışır")
    void hesapBasinaSinir() throws InterruptedException {
        String email = uniqueEmail("hiz-hesap");
        registerVia("198.51.100.200", email);

        // Her deneme başka bir IP'den (dağıtık saldırı): IP sınırı hiçbirini durdurmaz.
        // Büyük/küçük harf farkı aynı hesaba sayılır.
        for (int i = 1; i <= 10; i++) {
            String variant = i % 2 == 0 ? email.toUpperCase(java.util.Locale.ROOT) : email;
            ResponseEntity<JsonNode> attempt = loginVia("203.0.113." + (100 + i), variant, "yanlis-sifre");
            assertThat(attempt.getStatusCode()).as("%d. deneme", i).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        // On birinci deneme DOĞRU şifreyle ve yeni bir IP'den: yine de bekletilir.
        ResponseEntity<JsonNode> blocked = loginVia("203.0.113.150", email, PASSWORD);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(blocked.getBody().get("message").asText()).contains("Bu hesapla çok fazla hatalı giriş");
        assertThat(Long.parseLong(blocked.getHeaders().getFirst("Retry-After"))).isBetween(1L, 3L);

        // Kalıcı bir kilit yok: pencere dolunca doğru şifre çalışır.
        Thread.sleep(3_500);
        ResponseEntity<JsonNode> afterWindow = loginVia("203.0.113.151", email, PASSWORD);
        assertThat(afterWindow.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(afterWindow.getBody().get("accessToken").asText()).isNotBlank();
    }

    @Test
    @DisplayName("Başarılı giriş hesap sayacını sıfırlar")
    void basariliGirisSayaciSifirlar() {
        String email = uniqueEmail("hiz-sifirla");
        registerVia("198.51.100.201", email);
        for (int i = 1; i <= 9; i++) {
            loginVia("203.0.113." + (160 + i), email, "yanlis-sifre");
        }
        assertThat(loginVia("203.0.113.170", email, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);

        // Sayaç sıfırlandığı için dokuz hata daha kabul edilir (429 değil, 401).
        for (int i = 1; i <= 9; i++) {
            assertThat(loginVia("203.0.113." + (180 + i), email, "yanlis-sifre").getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test
    @DisplayName("Kodlanmış yol (/api/auth/%6cogin) sınırı atlatamaz")
    void kodlanmisYolSiniriAtlatamaz() {
        String realIp = "203.0.113.20";
        String email = uniqueEmail("hiz-yol");
        registerVia("198.51.100.202", email);
        URI encoded = URI.create("http://localhost:" + port + "/api/auth/%6cogin");

        // Kodlanmış yol gerçekten giriş ucuna varıyor: doğru şifreyle oturum açılır.
        ResponseEntity<JsonNode> first = rest.exchange(RequestEntity.post(encoded)
                .header("X-Forwarded-For", realIp).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", PASSWORD)), JsonNode.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        for (int i = 2; i <= 10; i++) {
            rest.exchange(RequestEntity.post(encoded).header("X-Forwarded-For", realIp)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("email", email, "password", "yanlis-sifre")), JsonNode.class);
        }

        ResponseEntity<JsonNode> blocked = rest.exchange(RequestEntity.post(encoded)
                .header("X-Forwarded-For", realIp).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", PASSWORD)), JsonNode.class);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

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

    /** Vekilin arkasından gelen giriş isteği: xff, vekilin ilettiği X-Forwarded-For. */
    private ResponseEntity<JsonNode> loginVia(String xff, String email, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For", xff);
        return rest.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(Map.of("email", email, "password", password), headers), JsonNode.class);
    }

    /** Doğrulanmış hesap aç; kayıt da sınırlı olduğu için kendi IP'sinden. */
    private void registerVia(String ip, String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For", ip);
        ResponseEntity<JsonNode> registered = rest.exchange("/api/auth/register", HttpMethod.POST,
                new HttpEntity<>(Map.of("firstName", "Hiz", "lastName", "Testi",
                        "email", email, "password", PASSWORD), headers), JsonNode.class);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verifyEmail(registered.getBody().get("id").asLong());
    }

    private ResponseEntity<JsonNode> resend(String email) {
        return rest.postForEntity("/api/auth/resend-verification",
                Map.of("email", email), JsonNode.class);
    }
}
