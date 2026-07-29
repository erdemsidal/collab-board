package com.collabboard.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Köprünün diğer ucu (ADR 0004): Redis kanalını dinler ve gelen olayı
 * BU sunucuya bağlı istemcilere push eder.
 *
 * Her sunucuda bir kopyası çalışır. Böylece bir sunucuda olan olay, tüm
 * sunucuların istemcilerine ulaşır:
 *
 *   Sunucu 1 publish ──► REDIS ──┬──► Sunucu 1'in abonesi → kendi istemcileri
 *                                └──► Sunucu 2'nin abonesi → kendi istemcileri
 */
@Component
public class RedisBroadcastSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisBroadcastSubscriber.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public RedisBroadcastSubscriber(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Redis'ten mesaj geldiğinde çalışır.
     * Zarfı açıp, içindeki JSON'u olduğu gibi ilgili STOMP adresine iletiriz —
     * yerleşik broker da onu o adresi dinleyen kendi abonelerine dağıtır.
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            RelayMessage relay = objectMapper.readValue(message.getBody(), RelayMessage.class);
            messagingTemplate.convertAndSend(relay.destination(), relay.payload());
            log.debug("Redis'ten gelen olay iletildi: {}", relay.destination());
        } catch (Exception e) {
            log.error("Redis mesajı işlenemedi", e);
        }
    }
}
