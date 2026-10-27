package com.collabboard.chathook;

import com.collabboard.i18n.AppLocale;
import com.collabboard.i18n.Messages;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Kanala giden mesajın metni ve her uygulamanın beklediği JSON biçimi.
 *
 * Metin tek satır: kim, hangi kart, hangi kolon, hangi pano; altında kartı açan
 * bağlantı. Bağlantının dilinde (messages_tr/en.properties, "chat.*").
 *
 * KİŞİDEN GELEN METİN KAÇIŞLANIR. Kart başlığı, pano ve kişi adı herhangi bir
 * şey olabilir; kanalın biçimlendirme dili onları komut sanmasın:
 *  - Slack: &, <, > → &amp; &lt; &gt;. "<!channel>" başlıklı bir kart kimseyi dürtmez.
 *  - Discord: biçim karakterlerinin önüne ters bölü; ayrıca allowed_mentions boş,
 *    yani "@everyone" yazan başlık bile kimseye bildirim düşürmez.
 *  - Teams: kalın, eğik ve bağlantı karakterlerinin önüne ters bölü. "[Şifreni
 *    yenile](https://...)" başlıklı bir kart resmi görünen bir bağlantıya dönüşmez.
 */
@Component
public class ChatMessages {

    private final Messages messages;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String brand;

    public ChatMessages(Messages messages, ObjectMapper mapper,
                        @Value("${app.base-url}") String baseUrl,
                        @Value("${app.brand-name:Sturnio}") String brand) {
        this.messages = messages;
        this.mapper = mapper;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.brand = brand;
    }

    /** Bir olayın satırındaki parçalar, ham hâliyle (kaçışlama burada yapılır). */
    public record Parts(String actor, String card, String column, String board, String assignee) {
    }

    /**
     * Olay mesajı.
     *
     * @param key "chat.event.CARD_DONE" gibi; atamada kişi kendini atadıysa "chat.event.CARD_TAKEN"
     */
    public String event(ChatProvider provider, String locale, String key, Parts parts, Long boardId, Long cardId) {
        Locale lang = AppLocale.of(locale);
        String line = messages.get(lang, key, escape(provider, parts.actor()), escape(provider, parts.card()),
                escape(provider, parts.column()), escape(provider, parts.board()), escape(provider, parts.assignee()));
        String link = baseUrl + "/?board=" + boardId + "&card=" + cardId;
        return payload(provider, line, link, messages.get(lang, "chat.openCard"));
    }

    /** "Deneme mesajı gönder": bağlantının çalıştığını söyleyen kısa mesaj, panoyu açan bağlantıyla. */
    public String test(ChatProvider provider, String locale, String boardName, Long boardId) {
        Locale lang = AppLocale.of(locale);
        String line = messages.get(lang, "chat.test", escape(provider, brand), escape(provider, boardName));
        return payload(provider, line, baseUrl + "/?board=" + boardId, messages.get(lang, "chat.openBoard"));
    }

    private String payload(ChatProvider provider, String line, String link, String linkLabel) {
        Map<String, Object> body = switch (provider) {
            case SLACK -> slack(line, link, linkLabel);
            case DISCORD -> discord(line, link, linkLabel);
            case TEAMS -> teams(line, link, linkLabel);
        };
        try {
            return mapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Kanal mesajı JSON'a çevrilemedi", e);
        }
    }

    /** text: bildirimlerde görünen yedek; blocks: kanalda görünen, bağlantılı hâli. */
    private static Map<String, Object> slack(String line, String link, String linkLabel) {
        String slackLink = "<" + slackEscape(link) + "|" + slackEscape(linkLabel) + ">";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", line);
        body.put("blocks", List.of(Map.of(
                "type", "section",
                "text", Map.of("type", "mrkdwn", "text", line + "\n" + slackLink))));
        // Kartın bağlantısı giriş sayfasına çıkar; önizleme kartı kanalda gereksiz yer kaplardı.
        body.put("unfurl_links", false);
        body.put("unfurl_media", false);
        return body;
    }

    private static Map<String, Object> discord(String line, String link, String linkLabel) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", line + "\n[" + discordEscape(linkLabel) + "](" + link + ")");
        // Hiçbir anma bildirim düşürmez: @everyone, @here, kişi ve rol.
        body.put("allowed_mentions", Map.of("parse", List.of()));
        // SUPPRESS_EMBEDS: bağlantının önizlemesi gösterilmez (Slack'teki unfurl_links gibi).
        body.put("flags", 4);
        return body;
    }

    /** Adaptive Card 1.4: tek bir metin bloğu ve kartı açan düğme. */
    private static Map<String, Object> teams(String line, String link, String linkLabel) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("$schema", "http://adaptivecards.io/schemas/adaptive-card.json");
        card.put("type", "AdaptiveCard");
        card.put("version", "1.4");
        card.put("body", List.of(Map.of("type", "TextBlock", "text", line, "wrap", true)));
        card.put("actions", List.of(Map.of("type", "Action.OpenUrl", "title", linkLabel, "url", link)));

        Map<String, Object> attachment = new LinkedHashMap<>();
        attachment.put("contentType", "application/vnd.microsoft.card.adaptive");
        attachment.put("content", card);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "message");
        body.put("attachments", List.of(attachment));
        return body;
    }

    // ── kaçışlama ───────────────────────────────────────────────────

    static String escape(ChatProvider provider, String value) {
        if (value == null) {
            return "";
        }
        return switch (provider) {
            case SLACK -> slackEscape(value);
            case DISCORD -> discordEscape(value);
            case TEAMS -> teamsEscape(value);
        };
    }

    /** Slack'in istediği üç kaçış; başka bir şeye dokunulmaz (biçim karakterleri zararsız). */
    static String slackEscape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Discord her noktalama işaretinin ters bölüyle kaçışlanmasını kabul eder. */
    static String discordEscape(String value) {
        return backslash(value, "\\*_~`|<>[]()#-");
    }

    /** Teams'in Adaptive Card'da tanıdığı biçimler: **kalın**, _eğik_, [bağlantı](adres). */
    static String teamsEscape(String value) {
        return backslash(value, "\\*_[]");
    }

    private static String backslash(String value, String special) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (special.indexOf(c) >= 0) {
                out.append('\\');
            }
            out.append(c);
        }
        return out.toString();
    }
}
