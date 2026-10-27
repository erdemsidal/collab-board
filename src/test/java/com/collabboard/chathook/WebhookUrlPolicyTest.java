package com.collabboard.chathook;

import com.collabboard.common.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Canlının izin listesi (SSRF koruması, ADR 0023): sunucu yalnızca üç uygulamanın
 * gerçek webhook adreslerine POST eder. Testler canlının SIKI varsayılanlarını
 * kullanır; entegrasyon testlerindeki yerel alıcı izni burada yok.
 */
class WebhookUrlPolicyTest {

    private final WebhookUrlPolicy policy = WebhookUrlPolicy.defaults();

    @Test
    @DisplayName("Üç uygulamanın gerçek biçimdeki adresleri kabul edilir")
    void gercekAdresler() {
        assertThat(policy.allows(ChatProvider.SLACK,
                "https://hooks.slack.com/services/T0001/B0002/AbCdEfGhIjKlMnOpQrStUvWx")).isTrue();
        assertThat(policy.allows(ChatProvider.DISCORD,
                "https://discord.com/api/webhooks/123456789012345678/aBc-DeF_gHi")).isTrue();
        assertThat(policy.allows(ChatProvider.DISCORD,
                "https://discordapp.com/api/webhooks/123456789012345678/aBc-DeF_gHi")).isTrue();
        assertThat(policy.allows(ChatProvider.TEAMS,
                "https://prod-12.westeurope.logic.azure.com:443/workflows/0a1b2c/triggers/manual/paths/invoke"
                        + "?api-version=2016-06-01&sp=%2Ftriggers%2Fmanual%2Frun&sv=1.0&sig=AbC-dEf_123")).isTrue();
        assertThat(policy.allows(ChatProvider.TEAMS,
                "https://default0a1b.2c.environment.api.powerplatform.com:443/powerautomate/automations/direct"
                        + "/workflows/0a1b/triggers/manual/paths/invoke?api-version=1&sig=x")).isTrue();
    }

    @ParameterizedTest(name = "Slack reddeder: {0}")
    @ValueSource(strings = {
            "http://169.254.169.254/latest/meta-data/iam/security-credentials/",
            "http://localhost/services/T/B/x",
            "http://localhost:8080/api/boards",
            "http://127.0.0.1:6379/",
            "http://hooks.slack.com/services/T0001/B0002/abc",            // https değil
            "https://hooks.slack.com.evil.example/services/T0001/B0002/abc",
            "https://evil.example/hooks.slack.com/services/T0001/B0002/abc",
            "https://evil.example@hooks.slack.com/services/T0001/B0002/abc",
            "https://hooks.slack.com@evil.example/services/T0001/B0002/abc",
            "https://hooks.slack.com:8443/services/T0001/B0002/abc",
            "https://hooks.slack.com/services/T0001/B0002/abc#frag",
            "https://hooks.slack.com/services/T0001/B0002/abc?x=1",
            "https://hooks.slack.com/api/chat.postMessage",
            "https://hooks.slack.com/services/T0001/../../admin",
            "https://hooks.slack.com/services/T0001/B0002/abc def",
            "https://hooks.slack.com/services/T0001\\B0002",
            "file:///etc/passwd",
            "gopher://hooks.slack.com/services/T/B/x",
            ""
    })
    void slackReddeder(String url) {
        assertThat(policy.allows(ChatProvider.SLACK, url)).isFalse();
    }

    @ParameterizedTest(name = "Teams reddeder: {0}")
    @ValueSource(strings = {
            "http://prod-12.westeurope.logic.azure.com/workflows/x",       // https değil
            "https://logic.azure.com.evil.example/workflows/x",
            "https://evil.example/.logic.azure.com/workflows/x",
            "https://evil.example#.logic.azure.com/workflows/x",
            "https://evil.example\\.logic.azure.com/workflows/x",
            "https://user@prod.logic.azure.com/workflows/x",
            "https://outlook.office.com/webhook/abc/IncomingWebhook/def",  // eski Office 365 bağlayıcısı
            "https://evil.example?.api.powerplatform.com/x",
            "https://prod.logic.azure.com:8080/workflows/x",
            "https://169.254.169.254/latest/meta-data"
    })
    void teamsReddeder(String url) {
        assertThat(policy.allows(ChatProvider.TEAMS, url)).isFalse();
    }

    @ParameterizedTest(name = "Discord reddeder: {0}")
    @ValueSource(strings = {
            "http://discord.com/api/webhooks/1/abc",
            "https://discord.com.evil.example/api/webhooks/1/abc",
            "https://evil.example/api/webhooks/1/abc",
            "https://discord.com/api/webhooks/abc/abc",
            "https://discord.com/api/v10/channels/1/messages",
            "https://discord.com/api/webhooks/1/abc/../../users/@me"
    })
    void discordReddeder(String url) {
        assertThat(policy.allows(ChatProvider.DISCORD, url)).isFalse();
    }

    @Test
    @DisplayName("Bir uygulamanın adresi diğeri için geçmez")
    void uygulamaKarismaz() {
        String slack = "https://hooks.slack.com/services/T0001/B0002/abc";
        assertThat(policy.allows(ChatProvider.DISCORD, slack)).isFalse();
        assertThat(policy.allows(ChatProvider.TEAMS, slack)).isFalse();
    }

    @Test
    @DisplayName("İzinli değilse 400; mesaj adresi tekrar etmez, uygulamanın adını söyler")
    void requireHataVerir() {
        assertThatThrownBy(() -> policy.require(ChatProvider.SLACK, "http://169.254.169.254/"))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> {
                    BadRequestException ex = (BadRequestException) e;
                    assertThat(ex.getCode()).isEqualTo("error.chatHook.urlNotAllowed");
                    assertThat(ex.getArgs()).containsExactly("Slack");
                });
    }

    @Test
    @DisplayName("Çok uzun adres reddedilir")
    void cokUzun() {
        String url = "https://hooks.slack.com/services/" + "a".repeat(WebhookUrlPolicy.MAX_LENGTH);
        assertThat(policy.allows(ChatProvider.SLACK, url)).isFalse();
    }

    @Test
    @DisplayName("Desen değiştirilebilir ama yapı kapıları açık kalmaz")
    void gevsekDesenBileKorur() {
        WebhookUrlPolicy loose = new WebhookUrlPolicy(".*", "", "");
        assertThat(loose.allows(ChatProvider.SLACK, "http://localhost:9999/slack/x")).isTrue();
        assertThat(loose.allows(ChatProvider.SLACK, "https://evil.example@hooks.slack.com/x")).isFalse();
        assertThat(loose.allows(ChatProvider.SLACK, "file:///etc/passwd")).isFalse();
        assertThat(loose.allows(ChatProvider.SLACK, "https://hooks.slack.com/x#y")).isFalse();
    }
}
