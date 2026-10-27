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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Testler için basit bir STOMP istemcisi — tarayıcının yaptığını Java'da yapar.
 *
 * Gelen mesajlar adrese göre bir kuyruğa (queue) düşer; test "şu adrese mesaj
 * gelmesini bekle" diyebilir. Gerçek zamanlı testlerde mesajlar ASENKRON geldiği
 * için beklemeli kuyruk şart: yoksa test mesaj gelmeden biter.
 *
 * Sunucunun ERROR çerçeveleri ve bağlantının kapanması da kaydedilir: canlı
 * bağlantının reddettiği abonelik ve gönderimler böyle sınanır (ADR 0025).
 */
public final class StompTestClient {

    private static final long TIMEOUT_SECONDS = 5;

    private final StompSession session;
    private final Recorder recorder;
    private final Map<String, BlockingQueue<JsonNode>> inbox = new ConcurrentHashMap<>();

    private StompTestClient(StompSession session, Recorder recorder) {
        this.session = session;
        this.recorder = recorder;
    }

    /** Kimlikli bağlantı: token, tarayıcıdaki gibi CONNECT frame başlığında gider (ADR 0005). */
    public static StompTestClient connect(String url, String token) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);

        Recorder recorder = new Recorder();
        StompSession session = client
                .connectAsync(url, new WebSocketHttpHeaders(), connectHeaders, recorder)
                .get(TIMEOUT_SECONDS * 2, TimeUnit.SECONDS);

        return new StompTestClient(session, recorder);
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

    /**
     * Sunucunun ERROR çerçevesini bekle; başlıklarını döner ("message", "x-reason").
     * Gelmezse testi düşürür.
     */
    public StompHeaders awaitError() throws InterruptedException {
        StompHeaders error = recorder.errors.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (error == null) {
            throw new AssertionError("Beklenen ERROR çerçevesi gelmedi");
        }
        return error;
    }

    /** ERROR gelmediğini doğrulamak için: süre dolarsa null döner. */
    public StompHeaders pollError(long seconds) throws InterruptedException {
        return recorder.errors.poll(seconds, TimeUnit.SECONDS);
    }

    /** Sunucu bağlantıyı kapattı mı? Verilen süre kadar bekler. */
    public boolean awaitClosed(long seconds) throws InterruptedException {
        return recorder.closed.await(seconds, TimeUnit.SECONDS);
    }

    public boolean isConnected() {
        return session.isConnected();
    }

    public String sessionId() {
        return session.getSessionId();
    }

    public void disconnect() {
        if (session.isConnected()) {
            session.disconnect();
        }
    }

    /**
     * Aboneliğin sunucuda kaydolması için kısa bekleme.
     * SUBSCRIBE asenkron gider; hemen ardından mesaj yollarsak abonelik henüz
     * işlenmemiş olabilir ve mesajı kaçırırız.
     */
    public static void awaitSubscriptions() throws InterruptedException {
        Thread.sleep(400);
    }

    /** ERROR çerçevelerini ve kopmayı kaydeden oturum dinleyicisi. */
    private static final class Recorder extends StompSessionHandlerAdapter {
        final BlockingQueue<StompHeaders> errors = new LinkedBlockingQueue<>();
        final CountDownLatch closed = new CountDownLatch(1);

        /** Oturum düzeyindeki çerçeve: ERROR. Gövdesi boş olduğundan payload null gelir. */
        @Override
        public Type getPayloadType(StompHeaders headers) {
            return byte[].class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            errors.add(headers);
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            closed.countDown();
        }
    }
}
