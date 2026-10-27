package com.collabboard.support;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Çerçeveleri elle yazan, kütüphanesiz bir STOMP istemcisi.
 *
 * Saldırganın yapacağını taklit etmek için: Spring'in istemcisi her zaman
 * kurallara uygun bir CONNECT gönderir; bu ise "STOMP" adlı bağlanma çerçevesi,
 * CONNECT'siz SUBSCRIBE ya da ACK gibi uygulamanın kullanmadığı çerçeveleri
 * de yollayabilir.
 */
public final class RawStompClient {

    private final WebSocketSession session;
    private final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
    private final CountDownLatch closed = new CountDownLatch(1);

    private RawStompClient(String url) throws Exception {
        this.session = new StandardWebSocketClient().execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession s, TextMessage message) {
                frames.add(message.getPayload());
            }

            @Override
            public void afterConnectionClosed(WebSocketSession s, CloseStatus status) {
                closed.countDown();
            }
        }, url).get(10, TimeUnit.SECONDS);
    }

    public static RawStompClient open(String url) throws Exception {
        return new RawStompClient(url);
    }

    /** Bir çerçeve gönder: KOMUT, başlıklar, boş satır, gövde ve NUL. */
    public RawStompClient send(String command, Map<String, String> headers, String body) throws IOException {
        StringBuilder frame = new StringBuilder(command).append('\n');
        headers.forEach((k, v) -> frame.append(k).append(':').append(v).append('\n'));
        frame.append('\n').append(body == null ? "" : body).append('\0');
        session.sendMessage(new TextMessage(frame.toString()));
        return this;
    }

    /** Verilen komutla başlayan ilk çerçeveyi bekle (ör. "CONNECTED", "ERROR", "MESSAGE"); yoksa null. */
    public String awaitFrame(String command, long seconds) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (true) {
            long left = deadline - System.nanoTime();
            if (left <= 0) {
                return null;
            }
            String frame = frames.poll(left, TimeUnit.NANOSECONDS);
            if (frame == null) {
                return null;
            }
            if (frame.startsWith(command + "\n")) {
                return frame;
            }
        }
    }

    public boolean awaitClosed(long seconds) throws InterruptedException {
        return closed.await(seconds, TimeUnit.SECONDS);
    }

    public void close() throws IOException {
        if (session.isOpen()) {
            session.close();
        }
    }
}
