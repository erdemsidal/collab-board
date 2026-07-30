package com.collabboard.support;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

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
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate rest;

    protected static final String PASSWORD = "parola12345";

    protected String wsUrl() {
        return "ws://localhost:" + port + "/ws";
    }

    /** Kayıt olup giriş yapar ve access token döner. Testlerin çoğu kimlik ister. */
    protected String registerAndLogin(String firstName, String lastName, String email) {
        rest.postForEntity("/api/auth/register", Map.of(
                "firstName", firstName, "lastName", lastName,
                "email", email, "password", PASSWORD), JsonNode.class);

        ResponseEntity<JsonNode> login = rest.postForEntity("/api/auth/login",
                Map.of("email", email, "password", PASSWORD), JsonNode.class);
        return login.getBody().get("accessToken").asText();
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

    /** Benzersiz e-posta — testler birbirinin kullanıcısına takılmasın. */
    protected String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
    }
}
