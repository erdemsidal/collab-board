package com.collabboard.mail;

import com.collabboard.i18n.AppLocale;
import com.collabboard.i18n.I18nConfig;
import com.collabboard.i18n.Messages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Bahsetme postasındaki yorum önizlemesi. */
class MentionMailTemplateTest {

    private final MentionMailTemplate template = new MentionMailTemplate(
            new MailText(new Messages(I18nConfig.newMessageSource()), "CollabBoard"));

    @Test
    @DisplayName("Kısa yorum olduğu gibi kalır, satır sonları tek boşluğa iner")
    void kisaYorum() {
        assertThat(MentionMailTemplate.excerpt("  Merhaba\n\n  dünya  ")).isEqualTo("Merhaba dünya");
    }

    @Test
    @DisplayName("Uzun yorum 200 karakteri geçmez ve '…' ile biter")
    void uzunYorum() {
        String excerpt = MentionMailTemplate.excerpt("a".repeat(500));
        assertThat(excerpt.codePointCount(0, excerpt.length())).isEqualTo(200);
        assertThat(excerpt).endsWith("…");
    }

    @Test
    @DisplayName("Kesme bir karakteri ortadan bölmez (iki parçalı karakterler)")
    void karakterBolunmez() {
        String wide = "𝔸";   // U+1D538, iki UTF-16 birimi
        String excerpt = MentionMailTemplate.excerpt(wide.repeat(300));
        assertThat(excerpt.codePointCount(0, excerpt.length())).isEqualTo(200);
        assertThat(excerpt).endsWith(wide + "…");
    }

    @Test
    @DisplayName("HTML gövdede yorum ve adlar kaçışlanır")
    void htmlKacislanir() {
        String html = template.html(AppLocale.TR, "<b>Ali</b>", "Pano", "Kart", "<script>x</script>",
                "http://localhost:8080/?board=1&card=2");
        assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;").contains("&lt;b&gt;Ali");
    }

    @Test
    @DisplayName("Posta alıcının dilinde; ürün adı ve kesme işareti doğru")
    void dileGore() {
        String tr = template.text(AppLocale.TR, "Ayşe", "Pazarlama", "Giriş sayfası", "Bakar mısın?", "http://x");
        String en = template.text(AppLocale.EN, "Ayşe", "Marketing", "Login page", "Can you check?", "http://x");

        assertThat(template.subject(AppLocale.TR, "Ayşe", "Giriş sayfası")).isEqualTo("Ayşe seni bir yorumda andı: Giriş sayfası");
        assertThat(template.subject(AppLocale.EN, "Ayşe", "Login page")).isEqualTo("Ayşe mentioned you in a comment: Login page");
        assertThat(tr).contains("\"Pazarlama\" panosundaki \"Giriş sayfası\" kartına", "Kartı aç: http://x");
        assertThat(en).contains("on the card \"Login page\" in \"Marketing\"", "Open card: http://x");
        assertThat(template.html(AppLocale.TR, "A", "B", "C", "D", "http://x")).contains("CollabBoard'da");
        assertThat(template.html(AppLocale.EN, "A", "B", "C", "D", "http://x")).contains("You're getting this email");
    }
}
