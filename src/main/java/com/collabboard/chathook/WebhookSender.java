package com.collabboard.chathook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Webhook adresine tek bir JSON POST'u. Yalnızca gönderir ve sonucu sınıflar;
 * bağlantının durumunu (sayaç, durdurma) ChatHookService yazar.
 *
 * Kurallar:
 *  - Yönlendirme İZLENMEZ (Redirect.NEVER): izin listesindeki bir adres bizi
 *    listede olmayan bir yere yönlendiremesin. 3xx başarısız sayılır.
 *  - Bağlantı 3 sn, yanıt 5 sn içinde gelmeli; yavaş bir kanal iş parçacığını tutmasın.
 *  - HTTP/1.1: üç uygulamanın da desteklediği, en sade yol.
 *  - Adres hiçbir loga yazılmaz (bir paroladır); yalnızca bağlantının numarası.
 */
@Component
public class WebhookSender {

    private static final Logger log = LoggerFactory.getLogger(WebhookSender.class);

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(CONNECT_TIMEOUT)
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private final String userAgent;

    public WebhookSender(@Value("${app.brand-name:Sturnio}") String brand) {
        this.userAgent = brand + "-Webhooks/1.0";
    }

    /** Sonuç ve (varsa) HTTP durum kodu. */
    public record Result(DeliveryOutcome outcome, Integer status) {
        public boolean ok() {
            return outcome == DeliveryOutcome.OK;
        }
    }

    /**
     * @param ref loglarda adresin yerine geçen kimlik ("hook 12", "deneme")
     */
    public Result post(URI url, String json, String ref) {
        HttpRequest request = HttpRequest.newBuilder(url)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("User-Agent", userAgent)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        CompletableFuture<HttpResponse<String>> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        try {
            // Yanıtın gövdesi de yavaş gelebilir; isteğin zaman aşımı yalnızca başlıkları kapsar.
            HttpResponse<String> response = future.get(REQUEST_TIMEOUT.toMillis() + 1000, TimeUnit.MILLISECONDS);
            DeliveryOutcome outcome = DeliveryOutcome.ofStatus(response.statusCode());
            if (outcome != DeliveryOutcome.OK) {
                log.warn("Kanal mesajı kabul edilmedi: {}, durum={}, yanıt={}", ref, response.statusCode(),
                        snippet(response.body()));
            }
            return new Result(outcome, response.statusCode());
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("Kanal mesajı zaman aşımına uğradı: {}", ref);
            return new Result(DeliveryOutcome.TIMEOUT, null);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof HttpTimeoutException) {
                log.warn("Kanal mesajı zaman aşımına uğradı: {}", ref);
                return new Result(DeliveryOutcome.TIMEOUT, null);
            }
            log.warn("Kanala bağlanılamadı: {}, {}", ref, cause == null ? e.getClass().getSimpleName()
                    : cause.getClass().getSimpleName());
            return new Result(DeliveryOutcome.NETWORK, null);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return new Result(DeliveryOutcome.NETWORK, null);
        }
    }

    /** Uygulamanın neden reddettiği ("channel_not_found"): kısa, tek satır. */
    private static String snippet(String body) {
        if (body == null || body.isBlank()) {
            return "-";
        }
        String flat = body.replaceAll("\\s+", " ").trim();
        return flat.length() > 200 ? flat.substring(0, 200) + "…" : flat;
    }

    /** Birim testi için: istemcinin kuralları (yönlendirme, zaman aşımı). */
    HttpClient client() {
        return client;
    }
}
