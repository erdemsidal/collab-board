package com.collabboard.board;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kartın kısa, okunur anahtarı: STR-42 (ADR 0021).
 *
 * Anahtar kartın kimliğinden türer, ayrıca saklanmaz. Kimlik hiç değişmediği için
 * anahtar da değişmez: kart kolondan kolona taşınsa, adı değişse de STR-42 kalır.
 * Pano başına sayaç (Jira'daki gibi) değil; kart başka panoya taşınamadığı için
 * buna ihtiyaç yok, sayaç ise ayrı bir sütun ve eşzamanlı artırma derdi getirirdi.
 *
 * Arayüzdeki karşılığı index.html'deki CARD_KEY_PREFIX ve aynı kurallı slug;
 * iki taraf aynı örneklerle sınanıyor (CardKeyTest, tarayıcı testi).
 */
public final class CardKey {

    /** Anahtarın ön eki. Sunucuda tek yer burası. */
    public static final String PREFIX = "STR";

    /** Dal adındaki slug'ın üst sınırı; kelime ortasından kesilmez. */
    static final int SLUG_MAX = 40;

    /**
     * Kabul edilen yazımlar: "STR-42", "str-42", "STR 42", "STR42", "#42", "42".
     * En fazla 18 basamak: Long'a her zaman sığar.
     */
    private static final Pattern KEY = Pattern.compile(
            "^(?:" + PREFIX + "\\s*-?\\s*|#)?0*(\\d{1,18})$", Pattern.CASE_INSENSITIVE);

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_ALNUM = Pattern.compile("[^a-z0-9]+");

    private CardKey() {
    }

    /** 42 → "STR-42". */
    public static String of(long cardId) {
        return PREFIX + "-" + cardId;
    }

    /**
     * Yazılan metin bir kart anahtarı mı? Öyleyse kartın kimliği.
     * Baştaki ve sondaki boşluk yok sayılır; büyük/küçük harf fark etmez.
     */
    public static Optional<Long> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher m = KEY.matcher(text.trim());
        if (!m.matches()) {
            return Optional.empty();
        }
        long id = Long.parseLong(m.group(1));
        return id > 0 ? Optional.of(id) : Optional.empty();
    }

    /** Git dalı adı: "str-42-odeme-sayfasi". Başlıktan slug çıkmazsa yalnızca "str-42". */
    public static String branchName(long cardId, String title) {
        String key = of(cardId).toLowerCase(Locale.ROOT);
        String slug = slug(title);
        return slug.isEmpty() ? key : key + "-" + slug;
    }

    /**
     * Başlığın dal adına uygun hâli.
     *
     *  1. Türkçe harfler Latin karşılığına: ç→c, ğ→g, ı→i, İ→i, ö→o, ş→s, ü→u.
     *     Küçültmeden ÖNCE: Java "İ"yi kökte "i̇" (i + nokta işareti) diye küçültür.
     *  2. Diğer aksanlar atılır (é→e): yoksa "Café" "caf" olurdu.
     *  3. Küçük harf; harf ve rakam dışındaki her dizi tek "-".
     *  4. SLUG_MAX karakteri aşarsa son kelime sınırından kesilir; tek bir uzun
     *     kelimeyse sınırda kesilir.
     */
    public static String slug(String title) {
        if (title == null) {
            return "";
        }
        String s = transliterateTurkish(title);
        s = MARKS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("");
        s = NOT_ALNUM.matcher(s.toLowerCase(Locale.ROOT)).replaceAll("-");
        s = trimDashes(s);
        if (s.length() > SLUG_MAX) {
            int cut = s.lastIndexOf('-', SLUG_MAX);
            s = trimDashes(s.substring(0, cut > 0 ? cut : SLUG_MAX));
        }
        return s;
    }

    private static String transliterateTurkish(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            out.append(switch (c) {
                case 'ç', 'Ç' -> 'c';
                case 'ğ', 'Ğ' -> 'g';
                case 'ı', 'İ' -> 'i';
                case 'ö', 'Ö' -> 'o';
                case 'ş', 'Ş' -> 's';
                case 'ü', 'Ü' -> 'u';
                default -> c;
            });
        }
        return out.toString();
    }

    private static String trimDashes(String s) {
        int start = 0, end = s.length();
        while (start < end && s.charAt(start) == '-') {
            start++;
        }
        while (end > start && s.charAt(end - 1) == '-') {
            end--;
        }
        return s.substring(start, end);
    }
}
