package com.collabboard.security;

import com.collabboard.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Oturum açmadan görülen arayüz dosyaları kimliksiz sunulmalı.
 *
 * SecurityConfig izin listesine eklenmeyen yeni bir statik dosya 401 alır;
 * tarayıcıda bu, tanıtım sayfasının hiç açılmaması (ya da stilsiz açılması)
 * demektir ve derleme bunu yakalamaz. Liste burada sabitlenir.
 */
class PublicAssetsIntegrationTest extends IntegrationTestBase {

    @ParameterizedTest
    @ValueSource(strings = {"/", "/index.html", "/collab-api.js", "/landing.js", "/landing.css", "/favicon.svg"})
    @DisplayName("Giriş öncesi arayüz dosyaları kimliksiz sunulur")
    void kimliksizSunulur(String path) {
        assertThat(rest.getForEntity(path, String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
