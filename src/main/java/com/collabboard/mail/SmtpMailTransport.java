package com.collabboard.mail;

import jakarta.mail.internet.MimeMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** Posta sunucusuna doğrudan SMTP ile bağlanır. Yerel geliştirmenin varsayılanı. */
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "provider", havingValue = "smtp", matchIfMissing = true)
public class SmtpMailTransport implements MailTransport {

    private final JavaMailSender mailSender;

    public SmtpMailTransport(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void send(String from, String to, String subject, String html, String text) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        // true, UTF-8: çok parçalı mesaj (düz metin + HTML) ve Türkçe karakterler
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(from);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(text, html);
        mailSender.send(message);
    }
}
