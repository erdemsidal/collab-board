package com.collabboard.mail;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

import static com.collabboard.mail.MailLayout.escape;

/**
 * Var olan hesaba dış hesapla (Google) giriş bağlandığında giden güvenlik postası.
 *
 * Bağlama sessiz olmamalı: aynı adresin Google hesabına erişen biri artık bu
 * hesaba da girebilir. Hesabın sahibi bunu kendisi yapmadıysa hemen öğrenmeli
 * ve ne yapacağını bilmeli.
 */
@Component
public class AccountLinkedMailTemplate {

    /**
     * Kullanıcılar Türkiye'de, sunucu UTC'de çalışıyor. Postadaki saat kişinin
     * kendi saati olmalı; aksi hâlde "ben o saatte bir şey yapmadım" diye
     * gereksiz yere endişelenir. Metin de saat dilimini söyler ("Türkiye saati").
     */
    private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");

    private final MailText text;

    public AccountLinkedMailTemplate(MailText text) {
        this.text = text;
    }

    public String subject(Locale locale, String providerName) {
        return text.get(locale, "mail.linked.subject", text.brand(), providerName);
    }

    public String html(Locale locale, String firstName, String providerName, Instant linkedAt, String link) {
        String content = MailLayout.greeting(text.hello(locale, escape(firstName)))
                + MailLayout.paragraph(text.get(locale, "mail.linked.body",
                        escape(text.brand()), escape(providerName), when(locale, linkedAt)), false)
                + MailLayout.paragraph(text.get(locale, "mail.linked.warning",
                        MailLayout.strong(text.get(locale, "mail.linked.warningLead")), escape(providerName)), true)
                + MailLayout.button(link, text.get(locale, "mail.openApp", text.brand()));
        return MailLayout.page(text.brand(), content, text.get(locale, "mail.linked.footer"));
    }

    public String text(Locale locale, String firstName, String providerName, Instant linkedAt, String link) {
        return String.join("\n\n",
                text.hello(locale, firstName),
                text.get(locale, "mail.linked.body", text.brand(), providerName, when(locale, linkedAt)),
                text.get(locale, "mail.linked.warning", text.get(locale, "mail.linked.warningLead"), providerName),
                link,
                text.get(locale, "mail.linked.footer")) + "\n";
    }

    /** Tarih ve saat alıcının dilindeki yazımla: "24 Ekim 2026 14:05" / "October 24, 2026 at 2:05 PM". */
    private static String when(Locale locale, Instant at) {
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT)
                .withLocale(locale).withZone(ZONE).format(at);
    }
}
