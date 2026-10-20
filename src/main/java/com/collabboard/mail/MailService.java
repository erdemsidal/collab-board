package com.collabboard.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * E-posta gönderimi.
 *
 * İKİ KARAR BURADA ÖNEMLİ:
 *
 * 1. ASENKRON. Kayıt isteği SMTP sunucusunu beklemez. Posta sunucusu yavaşsa
 *    kullanıcı üç saniye "Kaydol" düğmesine bakmak zorunda kalırdı; oysa hesap
 *    çoktan açılmış oluyor.
 *
 * 2. HATAYA TOLERANSLI. Posta gidemezse kayıt geri alınmaz. Kullanıcı zaten
 *    kayıtlıdır ve "yeniden gönder" diyebilir. Postayı kritik yola koymak,
 *    SMTP kesintisini kayıt kesintisine çevirirdi.
 *
 * Postanın nasıl çıktığı (SMTP ya da Brevo) MailTransport'ta; burası yalnızca
 * bu iki kuralı uygular.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final MailTransport transport;
    private final String from;

    public MailService(MailTransport transport, @Value("${app.mail.from}") String from) {
        this.transport = transport;
        this.from = from;
    }

    /**
     * HTML bir posta gönderir. Hata fırlatmaz — yalnızca loglar.
     *
     * @param textFallback HTML göremeyen istemciler için düz metin karşılığı
     */
    @Async
    public void send(String to, String subject, String html, String textFallback) {
        try {
            transport.send(from, to, subject, html, textFallback);
            log.info("E-posta gönderildi — alıcı: {}, konu: {}", to, subject);
        } catch (Exception ex) {
            // Bilinçli olarak yutuyoruz: çağıran akış (kayıt) postaya bağlı olmamalı.
            log.error("E-posta gönderilemedi — alıcı: {}, konu: {}", to, subject, ex);
        }
    }
}
