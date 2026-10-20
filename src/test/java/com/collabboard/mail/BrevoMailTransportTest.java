package com.collabboard.mail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Brevo'ya giden istek. Gerçek Brevo yerine makinede açılan küçük bir HTTP
 * sunucusu dinler: istek gerçekten ağdan geçer, JSON gerçekten yazılır.
 */
class BrevoMailTransportTest {

    private HttpServer server;
    private final AtomicReference<String> apiKey = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private volatile int status = 201;
    private volatile String reply = "{\"messageId\":\"<1@relay>\"}";

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/smtp/email", exchange -> {
            apiKey.set(exchange.getRequestHeaders().getFirst("api-key"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = reply.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private BrevoMailTransport transport() {
        return new BrevoMailTransport("http://127.0.0.1:" + server.getAddress().getPort() + "/v3",
                "xkeysib-deneme", "CollabBoard <gonderen@ornek.com>", false);
    }

    @Test
    @DisplayName("Posta Brevo'nun beklediği biçimde gider: anahtar başlıkta, gönderen adıyla, Türkçe bozulmadan")
    void dogruBicimdeGider() throws Exception {
        transport().send("CollabBoard <gonderen@ornek.com>", "alici@ornek.com",
                "Hesabını doğrula", "<p>Merhaba Şule</p>", "Merhaba Şule");

        assertThat(apiKey.get()).isEqualTo("xkeysib-deneme");
        JsonNode json = new ObjectMapper().readTree(body.get());
        assertThat(json.at("/sender/name").asText()).isEqualTo("CollabBoard");
        assertThat(json.at("/sender/email").asText()).isEqualTo("gonderen@ornek.com");
        assertThat(json.at("/to/0/email").asText()).isEqualTo("alici@ornek.com");
        assertThat(json.get("subject").asText()).isEqualTo("Hesabını doğrula");
        assertThat(json.get("htmlContent").asText()).isEqualTo("<p>Merhaba Şule</p>");
        assertThat(json.get("textContent").asText()).isEqualTo("Merhaba Şule");
    }

    @Test
    @DisplayName("Brevo reddederse sebebi hata mesajında görünür")
    void retSebebiGorunur() {
        status = 400;
        reply = "{\"code\":\"invalid_parameter\",\"message\":\"sender is not valid\"}";

        assertThatThrownBy(() -> transport().send("gonderen@ornek.com", "alici@ornek.com", "Konu", "<p>x</p>", "x"))
                .hasMessageContaining("400")
                .hasMessageContaining("sender is not valid");
    }
}
