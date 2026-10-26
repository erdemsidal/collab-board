package com.collabboard.admin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * APP_ADMIN_EMAILS'in okunması (ADR 0024). Değer Railway'in kutusuna elle
 * yapıştırılıyor; boşluk, büyük harf ya da sonda kalan virgül kimseyi panelden
 * etmemeli, boş bir değer de kimseyi içeri almamalı.
 */
class AdminAccessTest {

    @Test
    @DisplayName("Virgülle ayrılmış liste; boşluklar, noktalı virgül ve satır sonu da ayırır, boş parçalar atılır")
    void listeOkunur() {
        assertThat(AdminAccess.parse(" Erdem@Ornek.com , ,ikinci@ornek.com;\n ucuncu@ornek.com ,"))
                .containsExactlyInAnyOrder("erdem@ornek.com", "ikinci@ornek.com", "ucuncu@ornek.com");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", " , ;\n"})
    @DisplayName("Boş değer: kimse sahip değil")
    void bosListe(String raw) {
        assertThat(AdminAccess.parse(raw)).isEmpty();
        AdminAccess access = new AdminAccess(raw);
        assertThat(access.isOwner("erdem@ornek.com")).isFalse();
        assertThat(access.isOwner("")).isFalse();
        assertThat(access.isOwner(null)).isFalse();
    }

    @Test
    @DisplayName("Karşılaştırma büyük/küçük harfe bakmaz, ama adresin tamamı eşleşmeli")
    void harfFarkiOnemsiz() {
        AdminAccess access = new AdminAccess("erdem@ornek.com");
        assertThat(access.isOwner("ERDEM@ORNEK.COM")).isTrue();
        assertThat(access.isOwner(" erdem@ornek.com ")).isTrue();
        assertThat(access.isOwner("erdem@ornek.co")).isFalse();
        assertThat(access.isOwner("xerdem@ornek.com")).isFalse();
        assertThat(access.isOwner("erdem@ornek.com.tr")).isFalse();
    }

    /**
     * Türkçe yerelde "I".toLowerCase() noktasız "ı" verir: "ADMIN" → "admın".
     * Sunucunun dili Türkçe olsa bile büyük harfle yazılmış adres eşleşmeli.
     */
    @Test
    @DisplayName("Sunucunun yereli Türkçe olsa da 'I' harfi doğru küçülür")
    void turkceYereldeDeEslesir() {
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            AdminAccess access = new AdminAccess("ADMIN@SITE.IO");
            assertThat(access.isOwner("admin@site.io")).isTrue();
            assertThat(access.isOwner("Admin@Site.Io")).isTrue();
        } finally {
            Locale.setDefault(before);
        }
    }
}
