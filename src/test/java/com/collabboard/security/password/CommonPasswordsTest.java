package com.collabboard.security.password;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CommonPasswordsTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"12345678", "password1", "Password1", "PASSWORD1", "qwertyuiop", "iloveyou",
            "galatasaray", "Fenerbahce1907", "sifre123", "Şifre123", "parola123", "istanbul"})
    @DisplayName("Yaygın şifreler (büyük/küçük harf fark etmeksizin) listede")
    void yayginlarListede(String password) {
        assertThat(CommonPasswords.contains(password)).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"parola12345", "mavi kalem yesil defter", "Kanban-ile-is-akisi-2026", "x7#Qp!2vLm"})
    @DisplayName("Yaygın olmayan şifreler kabul edilir")
    void digerleriKabul(String password) {
        assertThat(CommonPasswords.contains(password)).isFalse();
    }

    @Test
    @DisplayName("Boş değer listede sayılmaz; o kuralı @NotBlank uygular")
    void bosDeger() {
        assertThat(CommonPasswords.contains(null)).isFalse();
        assertThat(CommonPasswords.contains("")).isFalse();
    }
}
