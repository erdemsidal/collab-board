package com.collabboard.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.stereotype.Component;

/**
 * Spring Security'nin OAuth2 girişini bu uygulamaya göre kurar.
 *
 * Hiç sağlayıcı kurulu değilse (GOOGLE_CLIENT_ID boş) hiçbir şey yapmaz:
 * /oauth2/** ve /login/oauth2/** adresleri yoktur, uygulama eskisi gibi çalışır.
 */
@Component
public class OAuthLoginSetup {

    private static final Logger log = LoggerFactory.getLogger(OAuthLoginSetup.class);

    private final OAuthClients clients;
    private final RedisAuthorizationRequestRepository authorizationRequests;
    private final OAuthLoginSuccessHandler successHandler;
    private final OAuthLoginFailureHandler failureHandler;

    public OAuthLoginSetup(OAuthClients clients, RedisAuthorizationRequestRepository authorizationRequests,
                           OAuthLoginSuccessHandler successHandler, OAuthLoginFailureHandler failureHandler) {
        this.clients = clients;
        this.authorizationRequests = authorizationRequests;
        this.successHandler = successHandler;
        this.failureHandler = failureHandler;
    }

    public void apply(HttpSecurity http) throws Exception {
        if (!clients.enabled()) {
            log.info("Dış hesapla giriş kapalı: kurulu sağlayıcı yok (GOOGLE_CLIENT_ID verilmedi)");
            return;
        }

        // PKCE: yetkilendirme kodu yolda yakalansa bile, akışı başlatan sunucunun
        // elindeki doğrulayıcı olmadan jetona çevrilemez. Gizli anahtarı olan
        // (confidential) istemcide zorunlu değil ama güncel öneri (RFC 9700).
        DefaultOAuth2AuthorizationRequestResolver resolver = new DefaultOAuth2AuthorizationRequestResolver(
                clients.repository(), OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());

        http.oauth2Login(oauth -> oauth
                .clientRegistrationRepository(clients.repository())
                .authorizedClientRepository(new DiscardingAuthorizedClientRepository())
                .authorizationEndpoint(endpoint -> endpoint
                        .authorizationRequestResolver(resolver)
                        .authorizationRequestRepository(authorizationRequests))
                // Giriş ekranı uygulamanın kendisi; Spring'in hazır /login sayfası üretilmesin.
                .loginPage("/")
                .successHandler(successHandler)
                .failureHandler(failureHandler));
    }

    /**
     * Google'ın verdiği erişim jetonunu SAKLAMAZ.
     *
     * Girişten sonra Google adına hiçbir şey yapmıyoruz (takvim, Drive yok);
     * Google'ın jetonu kimliği öğrenmek için bir kez kullanılır ve atılır.
     * Spring'in varsayılanı onu bellekte ya da oturumda tutardı: biri gereksiz
     * yere hassas veri biriktirir, öteki oturum açar.
     */
    static final class DiscardingAuthorizedClientRepository implements OAuth2AuthorizedClientRepository {

        @Override
        public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String clientRegistrationId,
                                                                         Authentication principal,
                                                                         HttpServletRequest request) {
            return null;
        }

        @Override
        public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal,
                                         HttpServletRequest request, HttpServletResponse response) {
        }

        @Override
        public void removeAuthorizedClient(String clientRegistrationId, Authentication principal,
                                           HttpServletRequest request, HttpServletResponse response) {
        }
    }
}
