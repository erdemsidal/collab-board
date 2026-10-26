package com.collabboard.support;

import com.collabboard.auth.EmailVerificationTokenRepository;
import com.collabboard.auth.entity.EmailVerificationToken;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * Entegrasyon testleri için ortak temel.
 *
 * NEDEN TESTCONTAINERS? Testin kendi Postgres ve Redis'ini Docker'da başlatır.
 *  - "Önce docker compose up yap" gibi bir ön koşul yok; test kendi kendine yeter.
 *  - Sahte (mock) değil GERÇEK Postgres/Redis: Flyway migration'ları, JPA eşlemeleri
 *    ve Redis pub/sub gerçekten çalışır. Bizim gibi altyapıya dayanan bir sistemde
 *    mock'lamak, test ettiğimizi sandığımız şeyin çoğunu atlamak olurdu.
 *
 * SINGLETON CONTAINER DESENİ — burası önemli:
 * Container'ları static blokta bir kez başlatıp HİÇ durdurmuyoruz. Neden?
 * JUnit'in @Testcontainers eklentisi container'ları HER TEST SINIFI için açıp
 * kapatır; oysa Spring context'i sınıflar arasında ÖNBELLEKTE tutulur. İkisi
 * uyuşmaz: ilk sınıf bitince container kapanır, ikinci sınıfta YENİ portlarda
 * açılır, ama önbellekteki context hâlâ eski portlara bağlanmaya çalışır ve
 * bağlantı zaman aşımına düşer.
 *
 * Bu yüzden container'lar JVM ömrü boyunca ayakta kalır; temizliği Testcontainers'ın
 * "Ryuk" bekçi container'ı JVM kapanınca yapar. (Bu, Testcontainers'ın belgelerinde
 * önerdiği "singleton containers" yaklaşımıdır.)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestMailConfig.class)   // gerçek SMTP'ye çıkma; gönderilenleri kaydet
// Hız sınırı kapalı: testler onlarca hesap açıyor ve hepsi aynı IP'den geliyor,
// saatlik kayıt sınırı hepsini keserdi. Sınırın KENDİSİ ayrıca sınanıyor
// (RateLimitIntegrationTest bu ayarı kendi üzerinde tekrar açar).
// @TestPropertySource kullanılıyor, @DynamicPropertySource değil: ikincisi en
// yüksek önceliğe sahiptir ve alt sınıfın onu geçersiz kılmasına izin vermez.
//
// Google girişi varsayılanda KAPALI: geliştiricinin ortamında GOOGLE_CLIENT_ID
// tanımlı olsa bile testler ona bağlı olmasın. Açık hâli ayrı bir sınıfta,
// sahte kimlik bilgileriyle sınanıyor (GoogleLoginIntegrationTest).
//
// Kanal bildirimleri AÇIK, testlere ait anahtarlarla (ADR 0023). Adres izin listesi
// yalnızca yerel alıcıya (localhost, /slack/ /teams/ /discord/ yolları) izin verir;
// canlının sıkı listesi WebhookUrlPolicyTest'te sınanıyor. Desenlerde ters bölü
// yok: bu satırlar .properties gibi okunur ve "\d" oradan "d" olarak çıkardı.
@TestPropertySource(properties = {
        "app.rate-limit.enabled=false",
        "app.oauth.google.client-id=",
        "app.oauth.google.client-secret=",
        "app.integrations.enc-password=yalnizca-testlerde-kullanilan-parola",
        "app.integrations.enc-salt=5c0744940b5c369b",
        "app.integrations.allowed-urls.slack=^http://localhost:[0-9]+/slack/[A-Za-z0-9/_-]+$",
        "app.integrations.allowed-urls.teams=^http://localhost:[0-9]+/teams/[A-Za-z0-9/_?=&%-]+$",
        "app.integrations.allowed-urls.discord=^http://localhost:[0-9]+/discord/[0-9]+/[A-Za-z0-9_-]+$"
})
public abstract class IntegrationTestBase {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("collabboard_test");

    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    /**
     * Container'lar rastgele portlarda açılır; adreslerini Spring'e ÇALIŞMA ANINDA
     * bildiriyoruz (application.yml'deki sabit değerlerin üstüne yazar).
     */
    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        // Testler posta sunucusuna çıkmaz; açılış bağlantı sınamasını da kapat.
        registry.add("app.mail.startup-check", () -> false);
        // Posta her zaman SMTP yolundan, yani TestMailConfig'in kaydeden sahte
        // göndericisinden geçsin. Ortamda MAIL_PROVIDER=brevo olsa bile (bkz.
        // src/test/resources/.env.properties) testler gerçek posta gönderemez.
        registry.add("app.mail.provider", () -> "smtp");
        registry.add("app.mail.brevo.api-key", () -> "");
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected EmailVerificationTokenRepository verificationTokenRepository;

    @Autowired
    protected TestMailConfig.RecordingMailSender mailSender;

    protected static final String PASSWORD = "parola12345";

    protected String wsUrl() {
        return "ws://localhost:" + port + "/ws";
    }

    /**
     * Kayıt olup e-postayı doğrular ve giriş yapar; access token döner.
     *
     * Doğrulama adımı testlere özel bir arka kapı DEĞİL: gerçek /api/auth/verify
     * ucu çağrılıyor, tıpkı kullanıcının postadaki bağlantıya tıkladığı gibi.
     * Böylece her test aynı zamanda akışın çalıştığını da doğruluyor.
     */
    protected String registerAndLogin(String firstName, String lastName, String email) {
        ResponseEntity<JsonNode> registered = rest.postForEntity("/api/auth/register", Map.of(
                "firstName", firstName, "lastName", lastName,
                "email", email, "password", PASSWORD), JsonNode.class);

        verifyEmail(registered.getBody().get("id").asLong());

        ResponseEntity<JsonNode> login = rest.postForEntity("/api/auth/login",
                Map.of("email", email, "password", PASSWORD), JsonNode.class);
        return login.getBody().get("accessToken").asText();
    }

    /** Postadaki doğrulama bağlantısına tıklamayı taklit eder. */
    protected void verifyEmail(long userId) {
        rest.getForEntity("/api/auth/verify?token="
                + URLEncoder.encode(pendingToken(userId), StandardCharsets.UTF_8), JsonNode.class);
    }

    /** Kullanıcının henüz kullanılmamış doğrulama jetonu. */
    protected String pendingToken(long userId) {
        return verificationTokenRepository.findAll().stream()
                .filter(t -> t.getUserId().equals(userId) && !t.isUsed())
                .map(EmailVerificationToken::getToken)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Kayıt sonrası doğrulama jetonu üretilmedi — userId: " + userId));
    }

    /** Token'lı istek göndermek için hazır başlıklar. */
    protected HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /** Test için pano oluşturur ve JSON'unu döner. */
    protected JsonNode createBoard(String token, String name) {
        return rest.exchange("/api/boards", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", name), authHeaders(token)), JsonNode.class).getBody();
    }

    /** Panoya davet gönderir (yalnızca OWNER yapabilir). Yanıt: 201 + davet. */
    protected ResponseEntity<JsonNode> invite(long boardId, String inviterToken, String email, String role) {
        return rest.exchange("/api/boards/" + boardId + "/invitations", HttpMethod.POST,
                new HttpEntity<>(Map.of("email", email, "role", role), authHeaders(inviterToken)),
                JsonNode.class);
    }

    /**
     * Kişiyi panoya üye yapar: davet gönderir, kişi olarak giriş yapar, kabul eder.
     *
     * Üyelik artık davet edilenin onayını istiyor; test yardımcısı da bu yüzden
     * kestirme bir yol kullanmıyor, gerçek akışı yürüyor. Davet edilenin
     * registerAndLogin ile (PASSWORD şifresiyle) açılmış olması gerekir.
     */
    protected void addMember(long boardId, String ownerToken, String email, String role) {
        ResponseEntity<JsonNode> invitation = invite(boardId, ownerToken, email, role);
        if (invitation.getStatusCode() != org.springframework.http.HttpStatus.CREATED) {
            throw new IllegalStateException("Davet gönderilemedi: " + invitation.getStatusCode()
                    + " " + invitation.getBody());
        }
        String inviteeToken = loginToken(email);
        ResponseEntity<JsonNode> accepted = rest.exchange(
                "/api/invitations/" + invitation.getBody().get("id").asLong() + "/accept", HttpMethod.POST,
                new HttpEntity<>(authHeaders(inviteeToken)), JsonNode.class);
        if (!accepted.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Davet kabul edilemedi: " + accepted.getStatusCode());
        }
    }

    /** Var olan, doğrulanmış bir hesapla giriş yapar. */
    protected String loginToken(String email) {
        return rest.postForEntity("/api/auth/login", Map.of("email", email, "password", PASSWORD), JsonNode.class)
                .getBody().get("accessToken").asText();
    }

    /** Panonun tam hâli; erişim kontrolünü sınamak için yanıt kodu da önemli. */
    protected ResponseEntity<JsonNode> getBoard(long boardId, String token) {
        return rest.exchange("/api/boards/" + boardId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), JsonNode.class);
    }

    /** Benzersiz e-posta — testler birbirinin kullanıcısına takılmasın. */
    protected String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
    }
}
