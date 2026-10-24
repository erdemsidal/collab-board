package com.collabboard.mail;

import org.springframework.stereotype.Component;

import java.util.Locale;

import static com.collabboard.mail.MailLayout.escape;

/**
 * Doğrulama postasının içeriği, alıcının dilinde.
 *
 * Metinler messages_*.properties içinde (mail.verification.*); iskelet MailLayout'ta.
 */
@Component
public class VerificationMailTemplate {

    private final MailText text;

    public VerificationMailTemplate(MailText text) {
        this.text = text;
    }

    public String subject(Locale locale) {
        return text.get(locale, "mail.verification.subject", text.brand());
    }

    public String html(Locale locale, String firstName, String link, int expiryHours) {
        String content = MailLayout.greeting(text.hello(locale, escape(firstName)))
                + MailLayout.paragraph(text.get(locale, "mail.verification.body",
                        "<b>" + escape(text.hours(locale, expiryHours)) + "</b>"), true)
                + MailLayout.button(link, text.get(locale, "mail.verification.button"))
                + MailLayout.fallbackLink(text.get(locale, "mail.buttonFallback"), link);
        return MailLayout.page(text.brand(), content, text.get(locale, "mail.verification.footer"));
    }

    public String text(Locale locale, String firstName, String link, int expiryHours) {
        return String.join("\n\n",
                text.hello(locale, firstName),
                text.get(locale, "mail.verification.bodyText", text.brand(), text.hours(locale, expiryHours)),
                link,
                text.get(locale, "mail.verification.footerText")) + "\n";
    }
}
