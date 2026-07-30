package com.collabboard.support;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Testler için basit bir STOMP istemcisi — tarayıcının yaptığını Java'da yapar.
 *
 * Gelen mesajlar adrese göre bir kuyruğa (queue) düşer; test "şu adrese mesaj
 * gelmesini bekle" diyebilir. Gerçek zamanlı testlerde mesajlar ASENKRON geldiği
 * için beklemeli kuyruk şart: yoksa test mesaj gelmeden biter.
 */
public final class StompTestClient {

    private static final long TIMEOUT_SECONDS = 5;

    private final StompSession session;
    private final Map<String, BlockingQueue<JsonNode>> inbox = new ConcurrentHashMap<>();

    private StompTestClient(StompSession session) {
        this.session = session;
    }

    /** Kimlikli bağlantı: token, tarayıcıdaki gibi CONNECT frame başlığında gider (ADR 0005). */
    public static StompTestClient connect(String url, String token) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);

        StompSession session = client
                .connectAsync(url, new WebSocketHttpHeaders(), connectHeaders,
                        new StompSessionHandlerAdapter() { })
                .get(TIMEOUT_SECONDS * 2, TimeUnit.SECONDS);

        return new StompTestClient(session);
    }

    /** Bir adrese abone ol; gelen mesajlar o adresin kuyruğuna yazılır. */
    public StompTestClient subscribe(String destination) {
        BlockingQueue<JsonNode> queue = inbox.computeIfAbsent(destination, d -> new LinkedBlockingQueue<>());
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return JsonNode.class;   // olayı ham JSON olarak al, tipe bağlanma
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add((JsonNode) payload);
            }
        });
        return this;
    }

    public void send(String destination, Object payload) {
        session.send(destination, payload);
    }

    /** Mesaj bekle; süre dolarsa testi anlamlı bir mesajla düşür. */
    public JsonNode awaitMessage(String destination) throws InterruptedException {
        JsonNode message = poll(destination, TIMEOUT_SECONDS);
        if (message == null) {
            throw new AssertionError("Beklenen mesaj gelmedi: " + destination);
        }
        return message;
    }

    /** Mesaj gelmemesini doğrulamak için: süre dolarsa null döner (hata fırlatmaz). */
    public JsonNode poll(String destination, long seconds) throws InterruptedException {
        BlockingQueue<JsonNode> queue = inbox.get(destination);
        if (queue == null) {
            throw new IllegalStateException("Bu adrese abone olunmadı: " + destination);
        }
        return queue.poll(seconds, TimeUnit.SECONDS);
    }

    public void disconnect() {
        session.disconnect();
    }

    /**
     * Aboneliğin sunucuda kaydolması için kısa bekleme.
     * SUBSCRIBE asenkron gider; hemen ardından mesaj yollarsak abonelik henüz
     * işlenmemiş olabilir ve mesajı kaçırırız.
     */
    public static void awaitSubscriptions() throws InterruptedException {
        Thread.sleep(400);
    }
}
