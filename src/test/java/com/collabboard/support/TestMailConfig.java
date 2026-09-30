package com.collabboard.support;

import jakarta.mail.internet.MimeMessage;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Testlerde gerçek SMTP'ye bağlanmayan posta gönderici.
 *
 * Neden tam bir mock (Mockito) değil? JavaMailSenderImpl'in createMimeMessage()
 * metodu bağlantı gerektirmez ve gerçek bir MimeMessage üretir; yalnızca send()
 * ağa çıkar. Sadece onu susturunca MailService'in geri kalanı — başlık kurulumu,
 * UTF-8 kodlaması, çok parçalı gövde — testlerde GERÇEKTEN çalışır. Baştan sona
 * mock'lasaydık bu kısımların bozulduğunu fark etmezdik.
 *
 * Gönderilenleri ayrıca kaydediyoruz ki "posta gerçekten çıktı mı" doğrulanabilsin.
 */
@TestConfiguration
public class TestMailConfig {

    @Bean
    @Primary
    public RecordingMailSender testMailSender() {
        return new RecordingMailSender();
    }

    public static class RecordingMailSender extends JavaMailSenderImpl {

        // Posta gönderimi @Async: yazan iş parçacığı ile okuyan test farklı.
        private final List<MimeMessage> sent = new CopyOnWriteArrayList<>();

        @Override
        public void send(MimeMessage... messages) {
            sent.addAll(List.of(messages));
        }

        public List<MimeMessage> sent() {
            return sent;
        }

        public void clear() {
            sent.clear();
        }
    }
}
