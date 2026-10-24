package com.collabboard.mail;

import org.springframework.stereotype.Component;

import java.util.Locale;

import static com.collabboard.mail.MailLayout.escape;

/**
 * Şifre sıfırlama postasının içeriği, alıcının dilinde.
 *
 * Doğrulama postasıyla aynı görsel dil (MailLayout); metinler mail.reset.*.
 */
@Component
public class PasswordResetMailTemplate {

    private final MailText text;

    public PasswordResetMailTemplate(MailText text) {
        this.text = text;
    }

    public String subject(Locale locale) {
        return text.get(locale, "mail.reset.subject", text.brand());
    }

    public String html(Locale locale, String firstName, String link, int expiryMinutes) {
        String content = MailLayout.greeting(text.hello(locale, escape(firstName)))
                + MailLayout.paragraph(text.get(locale, "mail.reset.body",
                        "<b>" + escape(text.minutes(locale, expiryMinutes)) + "</b>"), true)
                + MailLayout.button(link, text.get(locale, "mail.reset.button"))
                + MailLayout.fallbackLink(text.get(locale, "mail.buttonFallback"), link);
        return MailLayout.page(text.brand(), content, text.get(locale, "mail.reset.footer"));
    }

    public String text(Locale locale, String firstName, String link, int expiryMinutes) {
        return String.join("\n\n",
                text.hello(locale, firstName),
                text.get(locale, "mail.reset.bodyText", text.minutes(locale, expiryMinutes)),
                link,
                text.get(locale, "mail.reset.footerText")) + "\n";
    }
}
