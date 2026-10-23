package com.collabboard.support;

import com.collabboard.mail.MailTransport;
import com.collabboard.mail.SmtpMailTransport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testler geliştiricinin kişisel ayarlarından etkilenmemeli.
 *
 * Bir keresinde .env'deki MAIL_PROVIDER=brevo testlere sızdı: testler sahte
 * posta kutusu yerine gerçek Brevo'ya yöneldi. Aynı açık geri gelirse bu test
 * kırılır — testler gerçek posta göndermeden önce.
 */
class TestIsolationIntegrationTest extends IntegrationTestBase {

    @Autowired
    MailTransport transport;

    @Autowired
    Environment environment;

    @Test
    @DisplayName("Testler .env dosyasını okumaz; posta her zaman sahte kutuya gider")
    void envSizmaz() {
        assertThat(transport).isInstanceOf(SmtpMailTransport.class);
        // .env okunsaydı geliştiricinin anahtarı burada görünürdü.
        assertThat(environment.getProperty("BREVO_API_KEY")).isNull();
    }
}
