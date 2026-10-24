package com.collabboard.mail;

import org.springframework.stereotype.Component;

import java.util.Locale;

import static com.collabboard.mail.MailLayout.escape;

/**
 * "Seni bir yorumda andı" postası, anılan kişinin dilinde.
 *
 * Kısa tutulur: kim, hangi kart, yorumun ilk 200 karakteri ve kartı açan
 * bağlantı. Yorumun tamamı ve cevap yazmak uygulamada; posta yalnızca haber verir.
 */
@Component
public class MentionMailTemplate {

    /** Postadaki yorum alıntısının en fazla uzunluğu. */
    static final int EXCERPT_LENGTH = 200;

    private final MailText text;

    public MentionMailTemplate(MailText text) {
        this.text = text;
    }

    public String subject(Locale locale, String authorName, String cardTitle) {
        return text.get(locale, "mail.mention.subject", authorName, cardTitle);
    }

    public String html(Locale locale, String authorName, String boardName, String cardTitle, String body,
                       String link) {
        String content = MailLayout.paragraph(text.get(locale, "mail.mention.body",
                        MailLayout.strong(authorName), MailLayout.strong(boardName), MailLayout.strong(cardTitle)),
                        false)
                + MailLayout.quote(escape(excerpt(body)))
                + MailLayout.button(link, text.get(locale, "mail.mention.button"));
        return MailLayout.page(text.brand(), content, text.get(locale, "mail.mention.footer", text.brand()));
    }

    public String text(Locale locale, String authorName, String boardName, String cardTitle, String body,
                       String link) {
        return String.join("\n\n",
                text.get(locale, "mail.mention.bodyText", authorName, boardName, cardTitle),
                excerpt(body),
                text.get(locale, "mail.mention.openText", link)) + "\n";
    }

    /**
     * Yorumun en fazla 200 karakterlik önizlemesi; kesildiyse son karakter "…".
     * Satır sonları tek boşluğa iner: alıntı bir önizleme, yorumun kopyası değil.
     * Kod noktasıyla sayılır ki bir harf ya da işaret ortadan bölünmesin.
     */
    static String excerpt(String body) {
        String flat = body == null ? "" : body.strip().replaceAll("\\s+", " ");
        if (flat.codePointCount(0, flat.length()) <= EXCERPT_LENGTH) {
            return flat;
        }
        int end = flat.offsetByCodePoints(0, EXCERPT_LENGTH - 1);
        return flat.substring(0, end).stripTrailing() + "…";
    }
}
