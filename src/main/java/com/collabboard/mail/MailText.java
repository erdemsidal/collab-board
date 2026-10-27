package com.collabboard.mail;

import com.collabboard.i18n.Messages;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Postaların metinleri: alıcının dilinde, messages_*.properties içinden (ADR 0020).
 *
 * Ürünün görünen adı da buradan gelir (app.brand-name). Ad değişince postaların
 * başlığı, konusu ve düğmeleri kendiliğinden değişir; Türkçe metinlerde adın
 * arkasındaki ekler ("{0}''da") yeni ada göre gözden geçirilmeli.
 */
@Component
public class MailText {

    private final Messages messages;
    private final String brand;

    public MailText(Messages messages, @Value("${app.brand-name}") String brand) {
        this.messages = messages;
        this.brand = brand;
    }

    public String brand() {
        return brand;
    }

    public String get(Locale locale, String key, Object... args) {
        return messages.get(locale, key, args);
    }

    /** "Merhaba Ada," */
    public String hello(Locale locale, String firstName) {
        return get(locale, "mail.hello", firstName);
    }

    /** "24 saat" / "24 hours"; tekil-çoğul ayrımı dosyadaki choice biçiminde. */
    public String hours(Locale locale, int hours) {
        return get(locale, "mail.hours", hours);
    }

    public String minutes(Locale locale, int minutes) {
        return get(locale, "mail.minutes", minutes);
    }
}
