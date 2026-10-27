package com.collabboard.chathook;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

/**
 * Testlerin Slack/Teams/Discord yerine geçen yerel alıcısı (JDK'nın kendi HTTP
 * sunucusu; ek bağımlılık yok). Gelen her isteği kaydeder; yol başına dönülecek
 * durum kodu, gecikme ya da yönlendirme ayarlanabilir.
 *
 * Her test kendi benzersiz yolunu kullanır; testler birbirinin mesajını görmez.
 * close() sunucuyu ve uyuyan (gecikme) iş parçacıklarını durdurur: test bitince
 * geride çalışan bir şey kalmaz.
 */
final class LocalWebhookReceiver implements AutoCloseable {

    record Hit(String path, String body, String contentType, String userAgent) {
    }

    private final HttpServer server;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final List<Hit> hits = new CopyOnWriteArrayList<>();
    private final Map<String, Integer> statuses = new ConcurrentHashMap<>();
    private final Map<String, Long> delays = new ConcurrentHashMap<>();
    private final Map<String, String> redirects = new ConcurrentHashMap<>();

    private LocalWebhookReceiver() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(pool);
        server.start();
    }

    static LocalWebhookReceiver start() throws IOException {
        return new LocalWebhookReceiver();
    }

    int port() {
        return server.getAddress().getPort();
    }

    /** Sunucunun izin listesine uyan adres: http://localhost:PORT/slack/... */
    String url(String path) {
        return "http://localhost:" + port() + path;
    }

    void respond(String path, int status) {
        statuses.put(path, status);
    }

    void delay(String path, long millis) {
        delays.put(path, millis);
    }

    void redirect(String path, String location) {
        redirects.put(path, location);
    }

    List<Hit> hits(String path) {
        return hits.stream().filter(h -> h.path().equals(path)).toList();
    }

    /** Bu yola n istek gelene kadar bekler (en fazla 10 sn); son isteği döner. */
    Hit await(String path, int n) throws InterruptedException {
        waitUntil(() -> hits(path).size() >= n, "Alıcıya " + n + " istek gelmedi: " + path);
        List<Hit> list = hits(path);
        return list.get(list.size() - 1);
    }

    static void waitUntil(BooleanSupplier condition, String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError(message);
            }
            Thread.sleep(25);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String body;
        try (InputStream in = exchange.getRequestBody()) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        hits.add(new Hit(path, body, exchange.getRequestHeaders().getFirst("Content-Type"),
                exchange.getRequestHeaders().getFirst("User-Agent")));

        Long delay = delays.get(path);
        if (delay != null) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                exchange.close();
                return;
            }
        }
        String location = redirects.get(path);
        if (location != null) {
            exchange.getResponseHeaders().add("Location", location);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
            return;
        }
        int status = statuses.getOrDefault(path, 200);
        byte[] out = (status < 300 ? "ok" : "channel_not_found").getBytes(StandardCharsets.UTF_8);
        try {
            exchange.sendResponseHeaders(status, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        } catch (IOException e) {
            // İstemci zaman aşımında bağlantıyı çoktan kapatmış olabilir.
        } finally {
            exchange.close();
        }
    }

    @Override
    public void close() {
        server.stop(0);
        pool.shutdownNow();
    }
}
