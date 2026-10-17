package com.collabboard.mail;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Postayı Brevo'nun HTTPS API'sine verir (MAIL_PROVIDER=brevo).
 *
 * Alan adı gerekmez: Brevo panelinde gönderen adresi (Senders) eklenip
 * onaylanınca o adresten gönderilir. Kendi alan adı doğrulanmadığı sürece
 * posta "Brevo üzerinden" gittiği için bazı kutularda spam'e düşebilir;
 * kalıcı çözüm alan adı almak ve Brevo'da doğrulamaktır (DKIM/DMARC).
 */
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "provider", havingValue = "brevo")
public class BrevoMailTransport implements MailTransport {

    private static final Logger log = LoggerFactory.getLogger(BrevoMailTransport.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final RestClient client;
    private final String apiKey;
    private final String from;
    private final boolean startupCheck;

    public BrevoMailTransport(@Value("${app.mail.brevo.base-url:https://api.brevo.com/v3}") String baseUrl,
                              @Value("${app.mail.brevo.api-key:}") String apiKey,
                              @Value("${app.mail.from:}") String from,
                              @Value("${app.mail.startup-check:true}") boolean startupCheck) {
        // Panodan yapıştırılan anahtarın sonunda satır sonu kalabiliyor; Brevo onu "yanlış anahtar" sayar.
        apiKey = apiKey == null ? "" : apiKey.trim();
        // Brevo yanıt vermezse gönderen iş parçacığı sonsuza dek beklemesin.
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
        factory.setReadTimeout(TIMEOUT);
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("api-key", apiKey)
                .build();
        this.apiKey = apiKey;
        this.from = from;
        this.startupCheck = startupCheck;
    }

    @Override
    public void send(String from, String to, String subject, String html, String text) {
        MailFrom sender = MailFrom.parse(from);
        Map<String, Object> senderJson = new LinkedHashMap<>();
        if (sender.name() != null) {
            senderJson.put("name", sender.name());
        }
        senderJson.put("email", sender.address());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sender", senderJson);
        body.put("to", List.of(Map.of("email", to)));
        body.put("subject", subject);
        body.put("htmlContent", html);
        if (text != null) {
            body.put("textContent", text);
        }

        client.post().uri("/smtp/email")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                // Brevo neden reddettiğini gövdede yazar ("sender not valid" gibi);
                // yalnızca durum kodu loglansaydı sebebi tahmin etmek gerekirdi.
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new IllegalStateException("Brevo postayı kabul etmedi (" + response.getStatusCode().value()
                            + "): " + new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8));
                })
                .toBodilessEntity();
    }

    /**
     * Açılışta anahtarı ve gönderen adresini sınar. Uygulamanın açılmasını
     * engellemez — sorun loglarda ilk kullanıcı denemeden görünür.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void check() {
        if (!startupCheck) {
            return;
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("""

                    ┌─────────────────────────────────────────────────────────────┐
                    │  BREVO ANAHTARI EKSİK                                       │
                    ├─────────────────────────────────────────────────────────────┤
                    │  MAIL_PROVIDER=brevo ama BREVO_API_KEY tanımlı değil.       │
                    │  Doğrulama postaları gönderilemeyecek ve YENİ KAYITLAR      │
                    │  GİRİŞ YAPAMAYACAK.                                         │
                    │                                                             │
                    │  Brevo > SMTP & API > API Keys > Generate a new API key     │
                    └─────────────────────────────────────────────────────────────┘""");
            return;
        }

        try {
            client.get().uri("/account").retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.Unauthorized ex) {
            log.error("""

                    ┌─────────────────────────────────────────────────────────────┐
                    │  BREVO ANAHTARI REDDEDİLDİ (401)                            │
                    ├─────────────────────────────────────────────────────────────┤
                    │  Sebep: {}
                    │                                                             │
                    │  1. BREVO_API_KEY doğru kopyalandı mı? ("xkeysib-" ile      │
                    │     başlar; SMTP anahtarı "xsmtpsib-" BURADA ÇALIŞMAZ)      │
                    │  2. Brevo > Security > Authorised IPs açıksa, sunucunun     │
                    │     IP'si engellenmiştir. Barındırma platformlarında IP     │
                    │     sabit değil: bu korumayı kapatmak gerekir.              │
                    └─────────────────────────────────────────────────────────────┘""",
                    ex.getResponseBodyAsString().trim());
            return;
        } catch (Exception ex) {
            log.error("Brevo'ya ulaşılamadı — postalar gönderilemeyebilir: {}", ex.getMessage());
            return;
        }

        log.info("Brevo bağlantısı doğrulandı.");
        warnIfSenderNotVerified();
    }

    /**
     * Brevo yalnızca panelde eklenip onaylanmış adreslerden gönderir. Onaysız
     * adresle her gönderim reddedilir; bunu ilk kayıt denemesinden önce söyleyelim.
     */
    private void warnIfSenderNotVerified() {
        String address = MailFrom.parse(from).address();
        try {
            JsonNode senders = client.get().uri("/senders").retrieve().body(JsonNode.class);
            for (JsonNode sender : senders.path("senders")) {
                if (address.equalsIgnoreCase(sender.path("email").asText()) && sender.path("active").asBoolean()) {
                    log.info("Brevo gönderen adresi onaylı — {}", address);
                    return;
                }
            }
        } catch (Exception ex) {
            log.debug("Brevo gönderen listesi okunamadı", ex);
            return;
        }
        log.warn("""

                ┌─────────────────────────────────────────────────────────────┐
                │  GÖNDEREN ADRESİ BREVO'DA ONAYLI DEĞİL                      │
                ├─────────────────────────────────────────────────────────────┤
                │  MAIL_FROM : {}
                │                                                             │
                │  Brevo bu adresten gönderimi reddedecek. Brevo > Senders,   │
                │  Domains & Dedicated IPs > Senders > Add a sender; gelen    │
                │  onay postasındaki bağlantıya tıkla.                        │
                └─────────────────────────────────────────────────────────────┘""", address);
    }
}
