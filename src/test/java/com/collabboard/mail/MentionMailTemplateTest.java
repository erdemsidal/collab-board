package com.collabboard.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Bahsetme postasındaki yorum önizlemesi. */
class MentionMailTemplateTest {

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
        String html = new MentionMailTemplate().html("<b>Ali</b>", "Pano", "Kart", "<script>x</script>",
                "http://localhost:8080/?board=1&card=2");
        assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;").contains("&lt;b&gt;Ali");
    }
}
