package com.collabboard.auth.oauth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Kurulu (yapılandırılmış) dış hesap sağlayıcıları.
 *
 * NEDEN SPRING BOOT'UN KENDİ AYARLARI DEĞİL? spring.security.oauth2.client.
 * registration.* boş bir client-id ile açılışta hata verir. Biz ise Google
 * ayarlanmamışken uygulamanın sorunsuz açılmasını, yalnızca düğmenin
 * görünmemesini istiyoruz (yerelde, testlerde, ayarlar girilmeden önce canlıda).
 * Bu yüzden kayıtlar burada, yalnızca kimlik bilgileri verilmiş sağlayıcılar
 * için kurulur. Hiçbiri yoksa Google girişi hiç açılmaz (SecurityConfig).
 *
 * DÖNÜŞ ADRESİ (redirect URI) isteğe bakılarak değil, uygulamanın genel
 * adresinden (app.base-url) kurulur. Canlıda uygulama Railway'in HTTPS vekili
 * arkasında; istekten kurulan adres "http://..." çıkabilir ve Google onu
 * reddeder (redirect_uri_mismatch). Postalardaki bağlantılar da aynı adresten
 * kuruluyor; Google Cloud Console'a yazılan adres her zaman bununla aynı olur.
 */
@Component
public class OAuthClients {

    private static final Logger log = LoggerFactory.getLogger(OAuthClients.class);

    /** Google'ın kişiyi geri gönderdiği adresin yolu; Spring Security'nin varsayılanı. */
    public static final String CALLBACK_PATH = "/login/oauth2/code/";

    private final Map<String, OAuthProvider> configured = new LinkedHashMap<>();
    private final ClientRegistrationRepository repository;

    public OAuthClients(List<OAuthProvider> providers, Environment environment,
                        @Value("${app.base-url}") String baseUrl) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        List<ClientRegistration> registrations = new ArrayList<>();

        for (OAuthProvider provider : providers) {
            String prefix = "app.oauth." + provider.id() + ".";
            String clientId = environment.getProperty(prefix + "client-id", "").trim();
            String clientSecret = environment.getProperty(prefix + "client-secret", "").trim();
            if (clientId.isEmpty() && clientSecret.isEmpty()) {
                continue;   // ayarlanmamış: bu sağlayıcı yok sayılır
            }
            if (clientId.isEmpty() || clientSecret.isEmpty()) {
                log.warn("{} girişi kapalı: client-id ve client-secret birlikte verilmeli.", provider.displayName());
                continue;
            }
            String redirectUri = base + CALLBACK_PATH + provider.id();
            registrations.add(provider.registration()
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .redirectUri(redirectUri)
                    .build());
            configured.put(provider.id(), provider);
            log.info("{} girişi açık — dönüş adresi: {}", provider.displayName(), redirectUri);
        }

        // InMemoryClientRegistrationRepository boş liste kabul etmiyor; hiç
        // sağlayıcı yoksa depo da yok.
        this.repository = registrations.isEmpty() ? null : new InMemoryClientRegistrationRepository(registrations);
    }

    /** En az bir sağlayıcı kurulu mu? Değilse dış hesapla giriş tamamen kapalı. */
    public boolean enabled() {
        return repository != null;
    }

    /** Spring Security'nin OAuth2 girişine verilen kayıtlar. Yalnızca enabled() iken çağrılır. */
    public ClientRegistrationRepository repository() {
        if (repository == null) {
            throw new IllegalStateException("Kurulu dış hesap sağlayıcısı yok");
        }
        return repository;
    }

    /** Kurulu sağlayıcılar, giriş ekranındaki düğmeler için. */
    public List<OAuthProvider> list() {
        return List.copyOf(configured.values());
    }

    public Optional<OAuthProvider> find(String id) {
        return Optional.ofNullable(configured.get(id));
    }
}
