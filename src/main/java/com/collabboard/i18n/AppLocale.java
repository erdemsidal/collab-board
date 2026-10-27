package com.collabboard.i18n;

import com.collabboard.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Desteklenen diller ve dilin nereden okunacağı (ADR 0020).
 *
 * Liste tek yerde: üçüncü bir dil buraya eklenir, yanına messages_xx.properties
 * yazılır. Kural her yerde aynı:
 *  - Accept-Language yoksa ya da okunamıyorsa Türkçe: ürünün ana dili ve
 *    users.locale sütununun varsayılanı da bu.
 *  - Varsa, desteklenen dillerden tarayıcının en çok tercih ettiği.
 *  - Hiçbiri eşleşmiyorsa İngilizce: Türkçe bilmeyen birine Türkçe göstermek
 *    İngilizceden daha kötü bir tahmin.
 */
public final class AppLocale {

    public static final Locale TR = Locale.forLanguageTag("tr");
    public static final Locale EN = Locale.forLanguageTag("en");

    /** Dil bilinmediğinde (başlık yok, kayıtlı tercih yok). */
    public static final Locale DEFAULT = TR;

    /** Tarayıcının istediği dillerin hiçbiri desteklenmiyorsa. */
    public static final Locale FALLBACK = EN;

    public static final List<Locale> SUPPORTED = List.of(TR, EN);

    private AppLocale() {
    }

    /** "tr", "en" gibi kısa ad desteklenen bir dil mi? Büyük/küçük harf ve bölge kabul edilmez. */
    public static boolean isSupported(String tag) {
        return tag != null && SUPPORTED.stream().anyMatch(l -> l.getLanguage().equals(tag));
    }

    /** Kayıtlı kısa addan dil; tanınmıyorsa varsayılan. */
    public static Locale of(String tag) {
        return isSupported(tag) ? Locale.forLanguageTag(tag) : DEFAULT;
    }

    /** Kişinin kayıtlı tercihi (postalar, canlı kanaldaki reddetmeler). */
    public static Locale of(User user) {
        return user == null ? DEFAULT : of(user.getLocale());
    }

    /** İsteğin dili: Accept-Language başlığından. */
    public static Locale of(HttpServletRequest request) {
        return fromHeader(request.getHeader(HttpHeaders.ACCEPT_LANGUAGE));
    }

    /** Accept-Language değerinden dil ("tr-TR,tr;q=0.9,en;q=0.8" gibi). */
    public static Locale fromHeader(String header) {
        if (header == null || header.isBlank()) {
            return DEFAULT;
        }
        try {
            Locale match = Locale.lookup(Locale.LanguageRange.parse(header), SUPPORTED);
            return match != null ? match : FALLBACK;
        } catch (IllegalArgumentException e) {
            return DEFAULT;   // bozuk başlık: hiç gelmemiş say
        }
    }

    /** Veritabanına ve istemciye giden kısa ad. */
    public static String tag(Locale locale) {
        return locale.getLanguage();
    }

    /** Hata mesajında gösterilen liste: "tr, en". */
    public static String supportedTags() {
        return SUPPORTED.stream().map(Locale::getLanguage).collect(Collectors.joining(", "));
    }
}
