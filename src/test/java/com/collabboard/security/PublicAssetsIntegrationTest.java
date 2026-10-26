package com.collabboard.security;

import com.collabboard.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Oturum açmadan görülen arayüz dosyaları kimliksiz sunulmalı.
 *
 * SecurityConfig izin listesine eklenmeyen yeni bir statik dosya 401 alır;
 * tarayıcıda bu, tanıtım sayfasının hiç açılmaması (ya da stilsiz açılması)
 * demektir ve derleme bunu yakalamaz. Liste burada sabitlenir.
 */
class PublicAssetsIntegrationTest extends IntegrationTestBase {

    static Stream<String> arayuzDosyalari() {
        return Stream.of("/", "/index.html", "/collab-api.js", "/i18n.js", "/landing.js", "/landing.css",
                "/analytics.js", "/favicon.svg", "/apple-touch-icon.png");
    }

    @ParameterizedTest
    @MethodSource("arayuzDosyalari")
    @DisplayName("Giriş öncesi arayüz dosyaları kimliksiz sunulur")
    void kimliksizSunulur(String path) {
        assertThat(rest.getForEntity(path, String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * Yeni sürüm yayınlanınca sıradan bir yenileme (F5) onu getirmeli; tarayıcı
     * sayfayı ve yüklediği betikleri sunucuya sormadan önbellekten kullanmamalı.
     *
     * Bunu bugün Spring Security'nin varsayılan başlığı sağlıyor
     * ("no-cache, no-store, max-age=0, must-revalidate"). Statik dosyalara bir
     * önbellek süresi verilirse (ör. spring.web.resources.cache.period) dosyayı
     * sunan katman kendi başlığını yazar, Security geri çekilir ve tarayıcı eski
     * arayüzü o süre boyunca göstermeye devam eder. Bu test onu yakalar.
     */
    @ParameterizedTest
    @MethodSource("arayuzDosyalari")
    @DisplayName("Arayüz dosyaları sunucuya sorulmadan önbellekten kullanılmaz")
    void onbellektenSorulmadanKullanilmaz(String path) {
        assertThat(rest.getForEntity(path, String.class).getHeaders().getCacheControl()).contains("no-cache");
    }

    /**
     * Sayfaların okuduğu ayarlar da kimliksiz (tanıtım sayfasının ziyaretçisi).
     * Umami kimliği verilmemişse analitik kapalı: kimlik boş döner, sayfa betik
     * yüklemez (ADR 0024). Açık hâli AdminDashboardIntegrationTest'te.
     */
    @Test
    @DisplayName("Ayarlar kimliksiz okunur; Umami kimliği yoksa analitik kapalı")
    void ayarlarKimliksizAnalitikKapali() {
        ResponseEntity<JsonNode> config = rest.getForEntity("/api/public/config", JsonNode.class);
        assertThat(config.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(config.getBody().at("/analytics/umamiWebsiteId").isNull()).isTrue();
        assertThat(config.getBody().at("/analytics/scriptUrl").isNull()).isTrue();
        assertThat(config.getHeaders().getCacheControl()).contains("max-age=300");
    }
}
