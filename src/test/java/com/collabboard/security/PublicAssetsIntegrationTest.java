package com.collabboard.security;

import com.collabboard.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

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
                "/favicon.svg");
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
}
