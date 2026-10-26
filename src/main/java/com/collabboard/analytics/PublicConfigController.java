package com.collabboard.analytics;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Sayfaların sunucudan öğrenmesi gereken, gizli olmayan ayarlar. KİMLİKSİZ:
 * tanıtım sayfası da okur.
 *
 * Neden index.html'i sunucuda doldurmuyoruz? Sayfalar düz statik dosya; şablon
 * motoru eklemek hepsini sunucuya bağlardı. Tek küçük bir JSON yetiyor.
 *
 * Yanıt 5 dakika önbellekte kalabilir: ayar ancak yeniden yayınla değişir,
 * her sayfa açılışında sorulmasına gerek yok.
 */
@RestController
public class PublicConfigController {

    /** umamiWebsiteId boşsa (null) analitik kapalı; sayfa betik yüklemez. */
    public record AnalyticsConfig(String umamiWebsiteId, String scriptUrl) {}

    public record PublicConfig(AnalyticsConfig analytics) {}

    private final UmamiSettings umami;

    public PublicConfigController(UmamiSettings umami) {
        this.umami = umami;
    }

    @GetMapping("/api/public/config")
    public ResponseEntity<PublicConfig> config() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(new PublicConfig(new AnalyticsConfig(umami.websiteId(), umami.scriptUrl())));
    }
}
