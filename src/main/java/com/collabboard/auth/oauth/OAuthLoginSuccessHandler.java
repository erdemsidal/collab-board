package com.collabboard.auth.oauth;

import com.collabboard.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Google kişiyi doğruladı ve geri gönderdi; Spring kimlik jetonunu (ID token)
 * doğruladı. Şimdi kişiyi hesabına çevirip arayüze tek kullanımlık kodla
 * yönlendiriyoruz: &lt;genel adres&gt;/?oauth=&lt;kod&gt;. Oturum jetonları adrese asla yazılmaz.
 *
 * Bir şey ters giderse kişi yine ana sayfaya döner, bu kez /?oauth_error=&lt;anahtar&gt;
 * ile; arayüz giriş ekranında ne olduğunu söyler. Ayrıntı yalnızca logda.
 */
@Component
public class OAuthLoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuthLoginSuccessHandler.class);

    private final OAuthClients clients;
    private final OAuthAccountService accountService;
    private final OAuthLoginCodes loginCodes;
    private final OAuthBrowserBinding binding;
    private final String appUrl;

    public OAuthLoginSuccessHandler(OAuthClients clients, OAuthAccountService accountService,
                                    OAuthLoginCodes loginCodes, OAuthBrowserBinding binding,
                                    @Value("${app.base-url}") String baseUrl) {
        this.clients = clients;
        this.accountService = accountService;
        this.loginCodes = loginCodes;
        this.binding = binding;
        this.appUrl = appUrl(baseUrl);
    }

    /**
     * Yönlendirme adresi genel adresten kurulur, göreli yazılmaz. Göreli adresi
     * Tomcat isteğin şemasıyla tamamlar; Railway'in HTTPS vekili arkasında bu
     * "http://" olur ve kod ilk adımda şifresiz yola çıkardı.
     */
    static String appUrl(String baseUrl) {
        return (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl) + "/";
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        String target;
        try {
            // Akışın bu tarayıcıda başladığı dönüşte zaten sınandı; kod da aynı tarayıcıya bağlanır.
            String fingerprint = binding.fingerprint(request).orElseThrow(() ->
                    new OAuthLoginException(OAuthLoginError.FAILED, "Tarayıcı bağı çerezi yok"));
            OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
            OAuthProvider provider = clients.find(token.getAuthorizedClientRegistrationId()).orElseThrow(() ->
                    new OAuthLoginException(OAuthLoginError.FAILED, "Bilinmeyen sağlayıcı"));

            User user = accountService.resolve(provider.profile(token.getPrincipal().getAttributes()));
            target = appUrl + "?oauth=" + loginCodes.issue(user.getId(), fingerprint);
        } catch (OAuthLoginException e) {
            log.warn("Dış hesapla giriş reddedildi — sebep: {}, ayrıntı: {}", e.error().key(), e.getMessage());
            target = appUrl + "?oauth_error=" + e.error().key();
        } catch (RuntimeException e) {
            log.error("Dış hesapla giriş tamamlanamadı", e);
            target = appUrl + "?oauth_error=" + OAuthLoginError.FAILED.key();
        }
        // Yönlendirme yanıtı önbelleğe alınmasın: kod tek kullanımlık.
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.sendRedirect(target);
    }
}
