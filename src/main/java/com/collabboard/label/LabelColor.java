package com.collabboard.label;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Etiketin rengi: sabit, sekiz renklik palet (ADR 0022).
 *
 * Renk kodu (hex) burada değil arayüzde (index.html, --lb-* değişkenleri).
 * Sunucu yalnızca adı bilir; palet değişirse kayıtlar değişmez.
 *
 * Renkler yalnızca tonla ayrılmaz, açıklıkları da farklıdır (L* ~7-10 adım):
 * renk ayırt edemeyen biri de iki etiketi gri tonundan ayırabilir.
 *
 * trName: Türkçe yedek açıklamada geçen ad ("'Kart' kartına yeşil etiketi ekledi").
 * Arayüz cümleyi kendi dilinde kurar; bu yalnızca eski istemciler içindir.
 */
public enum LabelColor {
    GREEN("yeşil"),
    YELLOW("sarı"),
    ORANGE("turuncu"),
    RED("kırmızı"),
    PINK("pembe"),
    PURPLE("mor"),
    BLUE("mavi"),
    SKY("açık mavi");

    private final String trName;

    LabelColor(String trName) {
        this.trName = trName;
    }

    public String trName() {
        return trName;
    }

    /** "green" ya da "GREEN" kabul edilir; tanınmayan renk boş döner. */
    public static Optional<LabelColor> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String key = raw.strip().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(c -> c.name().equals(key)).findFirst();
    }

    /** Hata mesajı için: "GREEN, YELLOW, ...". */
    public static String allNames() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));
    }
}
