package com.collabboard.board.template;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Yeni panonun hangi kolonlarla açılacağı (ADR 0021).
 *
 * Şablonlar kodda durur, veritabanında değil: kullanıcı kendi şablonunu
 * kaydetmiyor, liste de sürümle birlikte değişiyor. Kolon adları burada değil,
 * messages_*.properties içinde ("template.{id}.col.{kolon}"); pano, açan
 * kişinin dilinde kurulur.
 *
 * Örnek kart eklenmez: boş bir panoyu kişinin kendi işiyle doldurması,
 * silinmesi gereken örneklerle uğraşmasından iyidir.
 */
public enum BoardTemplate {

    KANBAN("kanban",
            column("todo"), column("doing"), column("done")),

    /** İş kolonlarında WIP limiti: aynı anda çok iş açmak yerine bitirmeye zorlar (ADR 0007). */
    SOFTWARE("software",
            column("todo"), column("doing", 3), column("review", 2), column("done")),

    CONTENT("content",
            column("ideas"), column("writing"), column("approval"), column("published")),

    PERSONAL("personal",
            column("today"), column("week"), column("later"), column("done")),

    /** Kolonsuz: kişi kendi sürecini sıfırdan kurar. */
    BLANK("blank");

    /** Şablon seçilmezse. */
    public static final BoardTemplate DEFAULT = KANBAN;

    private final String id;
    private final List<ColumnSpec> columns;

    BoardTemplate(String id, ColumnSpec... columns) {
        this.id = id;
        this.columns = List.of(columns);
    }

    /** API'de görünen kimlik: "kanban", "software"... */
    public String id() {
        return id;
    }

    public List<ColumnSpec> columns() {
        return columns;
    }

    public String nameKey() {
        return "template." + id + ".name";
    }

    public String columnKey(ColumnSpec column) {
        return "template." + id + ".col." + column.key();
    }

    /** Kimlikten şablon; büyük/küçük harf ve kenardaki boşluk fark etmez. */
    public static Optional<BoardTemplate> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String wanted = id.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(t -> t.id.equals(wanted)).findFirst();
    }

    /** Hata mesajındaki liste: "kanban, software, content, personal, blank". */
    public static String ids() {
        return Arrays.stream(values()).map(BoardTemplate::id).collect(Collectors.joining(", "));
    }

    /** Çalışma anında kurulan mesaj anahtarlarının tamamı (MessageBundlesTest bunları dosyada arar). */
    public static List<String> messageKeys() {
        List<String> keys = new ArrayList<>();
        for (BoardTemplate template : values()) {
            keys.add(template.nameKey());
            template.columns.forEach(c -> keys.add(template.columnKey(c)));
        }
        return keys;
    }

    private static ColumnSpec column(String key) {
        return new ColumnSpec(key, null);
    }

    private static ColumnSpec column(String key, int wipLimit) {
        return new ColumnSpec(key, wipLimit);
    }

    /** Şablondaki bir kolon: adının anahtarı ve varsa WIP limiti. */
    public record ColumnSpec(String key, Integer wipLimit) {
    }
}
