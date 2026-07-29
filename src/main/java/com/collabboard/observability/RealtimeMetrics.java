package com.collabboard.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gerçek zamanlı katmanın "gösterge paneli" — ölçümler (metrikler).
 *
 * Neden gerekli? Loglar "ne oldu"yu anlatır, metrikler "genel gidişat"ı: kaç kişi
 * bağlı, kaç operasyon işlendi, kaçı çakışma yüzünden reddedildi. Ölçmediğin şeyi
 * yönetemezsin.
 *
 * Ölçümler /actuator/metrics altında görünür, ör.:
 *   /actuator/metrics/collabboard.ws.connections
 *   /actuator/metrics/collabboard.operations
 *
 * İKİ ÖLÇÜM TİPİ:
 *  - Gauge (gösterge): ANLIK değer, artar ve azalır → açık bağlantı sayısı.
 *  - Counter (sayaç): sadece ARTAN toplam → işlenen operasyon sayısı.
 *    (Sayaçtan "saniyede kaç" bilgisi türetilir; bunu Prometheus/Grafana yapar.)
 */
@Component
public class RealtimeMetrics {

    /** Açık WebSocket bağlantısı sayısı. Gauge bu nesneyi okuyarak anlık değeri verir. */
    private final AtomicInteger activeConnections = new AtomicInteger();

    private final MeterRegistry registry;

    public RealtimeMetrics(MeterRegistry registry) {
        this.registry = registry;

        Gauge.builder("collabboard.ws.connections", activeConnections, AtomicInteger::get)
                .description("Şu anda açık olan WebSocket bağlantısı sayısı")
                .register(registry);
    }

    public void connectionOpened() {
        activeConnections.incrementAndGet();
    }

    public void connectionClosed() {
        // Negatife düşmesin: kimlik doğrulaması reddedilen bağlantılarda
        // "kapandı" olayı "açıldı" olmadan gelebilir.
        activeConnections.updateAndGet(current -> Math.max(0, current - 1));
    }

    /**
     * Başarıyla uygulanan bir operasyonu say.
     * "type" bir ETİKET (tag): tek metrik adı altında tipe göre kırılım verir
     * → collabboard.operations{type="MOVE_CARD"} gibi.
     */
    public void operationApplied(String type) {
        registry.counter("collabboard.operations", "type", type).increment();
    }

    /** Reddedilen operasyonu say (ADR 0003). Bu sayaç çakışma baskısını gösterir. */
    public void operationRejected(String reason) {
        registry.counter("collabboard.operations.rejected", "reason", reason).increment();
    }
}
