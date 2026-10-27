package com.collabboard.mail;

import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Henüz hesabı olmayan birine giden davet postası.
 *
 * Hesabı olan kişiye posta gönderilmiyor: daveti uygulamanın içinde, canlı
 * olarak görüyor. Hesabı olmayan kişinin ise davetten haberdar olmasının başka
 * yolu yok — bağlantı kayıt ekranını bu adres dolu olarak açar.
 *
 * Alıcının hesabı, dolayısıyla kayıtlı dili yok: posta davet edenin dilinde gider
 * (ADR 0020). Kayıt ekranı ise kişinin kendi tarayıcısının dilinde açılır.
 */
@Component
public class InvitationMailTemplate {

    private final MailText text;

    public InvitationMailTemplate(MailText text) {
        this.text = text;
    }

    public String subject(Locale locale, String inviterName, String boardName) {
        return text.get(locale, "mail.invite.subject", inviterName, text.brand(), boardName);
    }

    /** @param roleCode OWNER / EDITOR / VIEWER; cümledeki karşılığı role.as.* */
    public String html(Locale locale, String inviterName, String boardName, String roleCode, String link) {
        String content = MailLayout.paragraph(text.get(locale, "mail.invite.body",
                        MailLayout.strong(inviterName), MailLayout.strong(boardName),
                        MailLayout.strong(role(locale, roleCode))), true)
                + MailLayout.button(link, text.get(locale, "mail.invite.button"));
        return MailLayout.page(text.brand(), content, text.get(locale, "mail.invite.footer"));
    }

    public String text(Locale locale, String inviterName, String boardName, String roleCode, String link) {
        return String.join("\n\n",
                text.get(locale, "mail.invite.bodyText", inviterName, text.brand(), boardName, role(locale, roleCode)),
                text.get(locale, "mail.invite.signupText") + "\n" + link,
                text.get(locale, "mail.invite.footerText")) + "\n";
    }

    private String role(Locale locale, String roleCode) {
        return text.get(locale, "role.as." + roleCode);
    }
}
