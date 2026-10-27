package com.collabboard.chathook;

import com.collabboard.common.exception.FeatureUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Webhook adreslerinin şifrelenmesi (ADR 0023): anahtar yoksa özellik kapalı, uydurma anahtar yok. */
class WebhookUrlCipherTest {

    private static final String PASSWORD = "bir-test-parolasi-yeterince-uzun";
    private static final String SALT = "0123456789abcdef0123456789abcdef";
    private static final String URL = "https://hooks.slack.com/services/T0001/B0002/GizliJetonAbCdEf";

    @Test
    @DisplayName("Anahtarlar yoksa özellik kapalı ve 503 anahtarıyla reddedilir")
    void anahtarYoksaKapali() {
        WebhookUrlCipher cipher = new WebhookUrlCipher("", "");
        assertThat(cipher.isAvailable()).isFalse();
        assertThatThrownBy(() -> cipher.encrypt(URL))
                .isInstanceOf(FeatureUnavailableException.class)
                .hasMessageContaining("error.chatHook.unavailable");
    }

    @Test
    @DisplayName("Geçersiz anahtar özelliği kapatır: kısa parola, onaltılık olmayan ya da tek uzunlukta tuz")
    void gecersizAnahtar() {
        assertThat(new WebhookUrlCipher("kisa", SALT).isAvailable()).isFalse();
        assertThat(new WebhookUrlCipher(PASSWORD, "bu-onaltilik-degil-zzzz").isAvailable()).isFalse();
        assertThat(new WebhookUrlCipher(PASSWORD, "0123456789abcdef0").isAvailable()).isFalse();
        assertThat(new WebhookUrlCipher(PASSWORD, "").isAvailable()).isFalse();
        assertThat(new WebhookUrlCipher("", SALT).isAvailable()).isFalse();
    }

    @Test
    @DisplayName("Şifreli metin adresi içermez, her seferinde farklıdır ve geri çözülür")
    void sifrelerVeCozer() {
        WebhookUrlCipher cipher = new WebhookUrlCipher(PASSWORD, SALT);
        assertThat(cipher.isAvailable()).isTrue();

        String a = cipher.encrypt(URL);
        String b = cipher.encrypt(URL);
        assertThat(a).doesNotContain("hooks.slack.com").doesNotContain("GizliJeton");
        assertThat(a).isNotEqualTo(b);   // rastgele başlangıç vektörü (GCM)
        assertThat(cipher.decrypt(a)).isEqualTo(URL);
        assertThat(cipher.decrypt(b)).isEqualTo(URL);
    }

    @Test
    @DisplayName("Başka anahtarla ya da değiştirilmiş şifreli metinle çözme başarısız olur")
    void yanlisAnahtarCozemez() {
        String encrypted = new WebhookUrlCipher(PASSWORD, SALT).encrypt(URL);
        WebhookUrlCipher other = new WebhookUrlCipher("baska-bir-parola-yeterince-uzun", SALT);
        assertThatThrownBy(() -> other.decrypt(encrypted)).isInstanceOf(IllegalStateException.class);

        WebhookUrlCipher same = new WebhookUrlCipher(PASSWORD, SALT);
        char last = encrypted.charAt(encrypted.length() - 1);
        String tampered = encrypted.substring(0, encrypted.length() - 1) + (last == '0' ? '1' : '0');
        assertThatThrownBy(() -> same.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }
}
