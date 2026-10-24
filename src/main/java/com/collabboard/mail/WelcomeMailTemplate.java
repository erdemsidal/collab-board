package com.collabboard.mail;

import org.springframework.stereotype.Component;

import java.util.Locale;

import static com.collabboard.mail.MailLayout.escape;

/**
 * Dış hesapla (Google) yeni açılan hesaba giden karşılama postası.
 *
 * Kayıt formundan gelen kişi doğrulama postası alıyor; Google ile gelen
 * kişinin adresini Google doğruladığı için ona doğrulama gerekmiyor. Yine de
 * hesabın açıldığını ve bundan sonra nasıl gireceğini bir postayla öğrenmeli.
 */
@Component
public class WelcomeMailTemplate {

    private final MailText text;

    public WelcomeMailTemplate(MailText text) {
        this.text = text;
    }

    public String subject(Locale locale) {
        return text.get(locale, "mail.welcome.subject", text.brand());
    }

    public String html(Locale locale, String firstName, String providerName, String link) {
        String content = MailLayout.greeting(text.hello(locale, escape(firstName)))
                + MailLayout.paragraph(text.get(locale, "mail.welcome.body",
                        escape(text.brand()), escape(providerName)), true)
                + MailLayout.button(link, text.get(locale, "mail.openApp", text.brand()));
        return MailLayout.page(text.brand(), content, text.get(locale, "mail.welcome.footer"));
    }

    public String text(Locale locale, String firstName, String providerName, String link) {
        return String.join("\n\n",
                text.hello(locale, firstName),
                text.get(locale, "mail.welcome.body", text.brand(), providerName),
                link,
                text.get(locale, "mail.welcome.footer")) + "\n";
    }
}
