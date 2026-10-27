package com.collabboard.i18n;

import com.collabboard.board.entity.BoardRole;
import com.collabboard.board.template.BoardTemplate;
import com.collabboard.chathook.ChatEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sunucunun iki dil dosyası (ADR 0020).
 *
 * Eksik bir çeviri derlemede yakalanmaz, ilk kez o hata oluştuğunda kullanıcı
 * anahtarın kendisini görür. Bu yüzden dosyalar burada karşılaştırılır; koddaki
 * her anahtarın dosyada olduğu da sınanır.
 */
class MessageBundlesTest {

    /** Koddaki mesaj anahtarları: "error.comment.empty", "{validation.firstName.required}". */
    private static final Pattern KEY_IN_CODE =
            Pattern.compile("\"\\{?((?:error|auth|validation|mail|role|workspace|chat)\\.[A-Za-z0-9_.]*[A-Za-z0-9_])}?\"");

    private static final Pattern ARGUMENT = Pattern.compile("\\{(\\d+)");

    private final Map<String, String> tr = load("tr");
    private final Map<String, String> en = load("en");

    @Test
    @DisplayName("İki dosyada da aynı anahtarlar var")
    void ayniAnahtarlar() {
        assertThat(en.keySet()).as("İngilizce dosyada eksik ya da fazla anahtar").isEqualTo(tr.keySet());
    }

    @Test
    @DisplayName("Her anahtar iki dilde aynı parametreleri kullanıyor")
    void ayniParametreler() {
        tr.forEach((key, value) -> assertThat(arguments(en.get(key))).as(key).isEqualTo(arguments(value)));
    }

    @Test
    @DisplayName("Kesme işaretleri iki kez yazılmış (MessageFormat tek kesmeyi yutar)")
    void kesmeIsaretleri() {
        Pattern lone = Pattern.compile("(?<!')'(?!')");
        Stream.of(tr, en).forEach(bundle -> bundle.forEach((key, value) ->
                assertThat(lone.matcher(value).find()).as("tek kesme işareti: %s", key).isFalse()));
    }

    @Test
    @DisplayName("Uzun tire yok; İngilizcede Türkçeye özgü harf yok")
    void yaziKurallari() {
        Stream.of(tr, en).forEach(bundle -> bundle.forEach((key, value) ->
                assertThat(value).as(key).doesNotContain("—")));
        en.forEach((key, value) -> assertThat(value).as(key).doesNotContainPattern("[ışğİŞĞ]"));
    }

    @Test
    @DisplayName("Her mesaj iki dilde de biçimlenebiliyor")
    void bicimlenir() {
        ResourceBundleMessageSource source = I18nConfig.newMessageSource();
        source.setUseCodeAsDefaultMessage(false);
        Object[] args = {1, 2, 3, 4};
        tr.keySet().forEach(key -> {
            assertThat(source.getMessage(key, args, AppLocale.TR)).as(key).isNotBlank();
            assertThat(source.getMessage(key, args, AppLocale.EN)).as(key).isNotBlank();
        });
        assertThat(source.getMessage("mail.openApp", new Object[]{"Sturnio"}, AppLocale.TR))
                .isEqualTo("Sturnio'yu aç");
        assertThat(source.getMessage("mail.hours", new Object[]{1}, AppLocale.EN)).isEqualTo("1 hour");
        assertThat(source.getMessage("mail.hours", new Object[]{24}, AppLocale.EN)).isEqualTo("24 hours");
    }

    @Test
    @DisplayName("Kodda geçen her anahtar dosyada var")
    void koddakiAnahtarlar() throws IOException {
        Set<String> used = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher m = KEY_IN_CODE.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (m.find()) {
                    used.add(m.group(1));
                }
            }
        }
        // Çalışma anında kurulan anahtarlar: rol adı ("role.as." + rol), şablon ve kolon adları,
        // kanal mesajı ("chat.event." + olay).
        Arrays.stream(BoardRole.values()).forEach(role -> used.add("role.as." + role.name()));
        used.addAll(BoardTemplate.messageKeys());
        Arrays.stream(ChatEvent.values()).forEach(event -> used.add("chat.event." + event.name()));

        assertThat(used).as("koddan okunan anahtarlar").hasSizeGreaterThan(50);
        assertThat(tr.keySet()).as("dosyada olmayan anahtar").containsAll(used);
    }

    private static Set<String> arguments(String value) {
        Set<String> out = new TreeSet<>();
        Matcher m = ARGUMENT.matcher(value);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private static Map<String, String> load(String lang) {
        Properties properties = new Properties();
        try (InputStream in = MessageBundlesTest.class.getResourceAsStream("/messages_" + lang + ".properties")) {
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return properties.stringPropertyNames().stream()
                .collect(Collectors.toMap(k -> k, properties::getProperty, (a, b) -> a, TreeMap::new));
    }
}
