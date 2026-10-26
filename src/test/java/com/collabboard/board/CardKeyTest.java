package com.collabboard.board;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kart anahtarı ve dal adı (ADR 0021). Spring'siz birim testi.
 *
 * Arayüz aynı kuralları index.html'de uygular; tarayıcı testi oradaki dal adını
 * buradaki örneklerden biriyle karşılaştırır. Kural değişirse ikisi birlikte değişmeli.
 */
class CardKeyTest {

    @Test
    @DisplayName("Anahtar ön ek ve kart kimliğinden oluşur")
    void anahtar() {
        assertThat(CardKey.of(42)).isEqualTo("STR-42");
        assertThat(CardKey.PREFIX).isEqualTo("STR");
    }

    @Test
    @DisplayName("Anahtarın yaygın yazımları tanınır; büyük/küçük harf ve kenar boşluğu fark etmez")
    void cozumlenir() {
        for (String text : new String[]{"STR-42", "str-42", " Str-42 ", "STR 42", "STR42", "STR - 42", "#42", "42", "STR-0042"}) {
            assertThat(CardKey.parse(text)).as(text).contains(42L);
        }
        assertThat(CardKey.parse("7")).contains(7L);
    }

    @Test
    @DisplayName("Anahtar olmayan metin tanınmaz")
    void anahtarDegil() {
        for (String text : new String[]{"", "   ", "STR", "STR-", "STR-0", "0", "-42", "STR-4a", "ABC-42",
                "42 43", "plan 42", "STR--42", "1234567890123456789"}) {
            assertThat(CardKey.parse(text)).as(text).isEmpty();
        }
        assertThat(CardKey.parse(null)).isEmpty();
    }

    @Test
    @DisplayName("Slug: Türkçe harfler Latin karşılığına, diğer her şey tek tire")
    void turkceHarfler() {
        assertThat(CardKey.slug("Çağrı merkezi: İş akışı & ölçüm")).isEqualTo("cagri-merkezi-is-akisi-olcum");
        assertThat(CardKey.slug("ŞUBAT bütçesi (ÖNEMLİ!)")).isEqualTo("subat-butcesi-onemli");
        assertThat(CardKey.slug("IŞIK ve ılık")).isEqualTo("isik-ve-ilik");
        assertThat(CardKey.slug("Güncelle: Ödeme/İade")).isEqualTo("guncelle-odeme-iade");
    }

    @Test
    @DisplayName("Slug: kenardaki ve art arda gelen ayraçlar tek tireye iner, diğer aksanlar atılır")
    void ayraclarVeAksanlar() {
        assertThat(CardKey.slug("  --Merhaba,   Dünya--  ")).isEqualTo("merhaba-dunya");
        assertThat(CardKey.slug("v2.1 sürümü #42")).isEqualTo("v2-1-surumu-42");
        assertThat(CardKey.slug("Café déjà vu")).isEqualTo("cafe-deja-vu");
        assertThat(CardKey.slug("日本語")).isEmpty();
        assertThat(CardKey.slug("")).isEmpty();
        assertThat(CardKey.slug(null)).isEmpty();
    }

    @Test
    @DisplayName("Slug 40 karakteri aşarsa kelime sınırından kesilir")
    void kelimeSinirindanKesilir() {
        String slug = CardKey.slug("Ödeme sayfasında İngilizce hata mesajlarını düzelt ve testleri güncelle");

        assertThat(slug).isEqualTo("odeme-sayfasinda-ingilizce-hata");
        assertThat(slug.length()).isLessThanOrEqualTo(40);
        // Tam 40'ta biten kelime kesilmez
        assertThat(CardKey.slug("a".repeat(19) + " " + "b".repeat(20) + " ccc")).isEqualTo("a".repeat(19) + "-" + "b".repeat(20));
        // Tek bir uzun kelime: sınırda kesilir
        assertThat(CardKey.slug("x".repeat(60))).isEqualTo("x".repeat(40));
    }

    @Test
    @DisplayName("Dal adı küçük harfli anahtar ve slug; slug çıkmazsa yalnızca anahtar")
    void dalAdi() {
        assertThat(CardKey.branchName(42, "Ödeme sayfası çöküyor")).isEqualTo("str-42-odeme-sayfasi-cokuyor");
        assertThat(CardKey.branchName(7, "!!!")).isEqualTo("str-7");
    }
}
