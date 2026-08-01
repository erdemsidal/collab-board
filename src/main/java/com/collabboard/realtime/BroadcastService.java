package com.collabboard.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Tüm gerçek zamanlı yayınların tek kapısı (ADR 0004).
 *
 * Olay doğrudan bu sunucunun istemcilerine değil, Redis'e yayınlanır; tüm
 * kopyalar dinleyip kendi istemcilerine iletir.
 *
 * Yerel gönderim bilerek yapılmaz: Redis mesajı yayınlayan sunucunun abonesine de
 * dağıttığı için, ayrıca yerelden gönderilirse olay iki kez ulaşırdı.
 */
@Service
public class BroadcastService {

    /** Tüm sunucuların dinlediği ortak Redis kanalı ("anons hattı"). */
    public static final String CHANNEL = "collabboard:broadcast";

    private static final Logger log = LoggerFactory.getLogger(BroadcastService.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public BroadcastService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /**
     * Bir olayı verilen STOMP adresine, TÜM sunucular üzerinden yayınla.
     *
     * @param destination STOMP adresi, ör. "/topic/board.42"
     * @param payload     olay nesnesi (JSON'a çevrilecek)
     */
    public void broadcast(String destination, Object payload) {
        try {
            RelayMessage relay = new RelayMessage(destination, objectMapper.valueToTree(payload));
            redis.convertAndSend(CHANNEL, objectMapper.writeValueAsString(relay));
        } catch (JsonProcessingException e) {
            // Yayın başarısız olsa bile operasyon veritabanına yazılmıştır;
            // istemciler yenilediğinde/resync ettiğinde doğruyu görür.
            log.error("Olay Redis'e yayınlanamadı: destination={}", destination, e);
        }
    }
}
