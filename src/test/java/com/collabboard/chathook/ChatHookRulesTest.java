package com.collabboard.chathook;

import com.collabboard.security.ratelimit.RateLimitPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Kanal bildirimlerinin küçük kuralları; Spring'siz (ADR 0023). */
class ChatHookRulesTest {

    @Test
    @DisplayName("Gönderici yönlendirme izlemez; bağlantı 3, yanıt 5 saniyeyle sınırlı")
    void gondericiKurallari() {
        HttpClient client = new WebhookSender("Sturnio").client();
        assertThat(client.followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
        assertThat(client.connectTimeout()).contains(Duration.ofSeconds(3));
        assertThat(WebhookSender.REQUEST_TIMEOUT).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("HTTP durumu sonuca çevrilir; yalnızca 404, 410 ve 403 bağlantıyı hemen durdurur")
    void durumKodlari() {
        assertThat(DeliveryOutcome.ofStatus(200)).isEqualTo(DeliveryOutcome.OK);
        assertThat(DeliveryOutcome.ofStatus(202)).isEqualTo(DeliveryOutcome.OK);   // Teams
        assertThat(DeliveryOutcome.ofStatus(204)).isEqualTo(DeliveryOutcome.OK);   // Discord
        assertThat(DeliveryOutcome.ofStatus(404).permanentReason()).isEqualTo(DisabledReason.GONE);
        assertThat(DeliveryOutcome.ofStatus(410).permanentReason()).isEqualTo(DisabledReason.GONE);
        assertThat(DeliveryOutcome.ofStatus(403).permanentReason()).isEqualTo(DisabledReason.FORBIDDEN);
        assertThat(DeliveryOutcome.ofStatus(429)).isEqualTo(DeliveryOutcome.RATE_LIMITED);
        assertThat(DeliveryOutcome.ofStatus(503)).isEqualTo(DeliveryOutcome.SERVER_ERROR);
        assertThat(DeliveryOutcome.ofStatus(302)).isEqualTo(DeliveryOutcome.REDIRECT);
        assertThat(DeliveryOutcome.ofStatus(400)).isEqualTo(DeliveryOutcome.REJECTED);
        for (DeliveryOutcome o : new DeliveryOutcome[]{DeliveryOutcome.RATE_LIMITED, DeliveryOutcome.SERVER_ERROR,
                DeliveryOutcome.TIMEOUT, DeliveryOutcome.NETWORK, DeliveryOutcome.REDIRECT, DeliveryOutcome.REJECTED}) {
            assertThat(o.permanentReason()).as(o.name()).isNull();
        }
    }

    @Test
    @DisplayName("Deneme mesajı sınırı iki deneme ucunu da kapsar, diğer uçları değil")
    void denemeSiniriYolu() {
        AntPathMatcher paths = new AntPathMatcher();
        String pattern = RateLimitPolicy.CHAT_HOOK_TEST.path();
        assertThat(paths.match(pattern, "/api/boards/12/chat-hooks/test")).isTrue();
        assertThat(paths.match(pattern, "/api/boards/12/chat-hooks/7/test")).isTrue();
        assertThat(paths.match(pattern, "/api/boards/12/chat-hooks")).isFalse();
        assertThat(paths.match(pattern, "/api/boards/12/chat-hooks/7")).isFalse();
    }

    @Test
    @DisplayName("Seçili olaylar virgülle saklanır; tanınmayan ad okurken düşülür")
    void olaylarSaklanir() {
        ChatEventsConverter converter = new ChatEventsConverter();
        Set<ChatEvent> events = EnumSet.of(ChatEvent.COMMENT_ADDED, ChatEvent.CARD_CREATED);
        assertThat(converter.convertToDatabaseColumn(events)).isEqualTo("CARD_CREATED,COMMENT_ADDED");
        assertThat(converter.convertToEntityAttribute("CARD_CREATED,ESKI_OLAY, CARD_DONE"))
                .containsExactlyInAnyOrder(ChatEvent.CARD_CREATED, ChatEvent.CARD_DONE);
        assertThat(converter.convertToEntityAttribute("")).isEmpty();
    }

    @Test
    @DisplayName("Kişiden gelen metin kaçışlanır: Slack üç karakter, Discord ve Teams ters bölü")
    void kacislama() {
        assertThat(ChatMessages.slackEscape("<!channel> & <@U123>")).isEqualTo("&lt;!channel&gt; &amp; &lt;@U123&gt;");
        assertThat(ChatMessages.discordEscape("**x** [a](b) <@1>")).isEqualTo("\\*\\*x\\*\\* \\[a\\]\\(b\\) \\<@1\\>");
        assertThat(ChatMessages.teamsEscape("**x** [a](https://e)")).isEqualTo("\\*\\*x\\*\\* \\[a\\](https://e)");
        assertThat(ChatMessages.escape(ChatProvider.SLACK, null)).isEmpty();
    }

    @Test
    @DisplayName("Arayüzde gösterilen tek parça adresin son 4 karakteri")
    void ipucu() {
        assertThat(ChatHookService.hintOf("https://hooks.slack.com/services/T/B/abcda9F2")).isEqualTo("a9F2");
    }
}
