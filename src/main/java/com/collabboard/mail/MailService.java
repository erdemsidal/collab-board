package com.collabboard.mail;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

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
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final String from;

    public MailService(JavaMailSender mailSender, @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
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
            MimeMessage message = mailSender.createMimeMessage();
            // true, UTF-8: çok parçalı mesaj (düz metin + HTML) ve Türkçe karakterler
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(textFallback, html);

            mailSender.send(message);
            log.info("E-posta gönderildi — alıcı: {}, konu: {}", to, subject);
        } catch (Exception ex) {
            // Bilinçli olarak yutuyoruz: çağıran akış (kayıt) postaya bağlı olmamalı.
            log.error("E-posta gönderilemedi — alıcı: {}, konu: {}", to, subject, ex);
        }
    }
}
