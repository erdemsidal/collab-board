package com.collabboard.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Google akışı yarıda kaldı: kişi izin ekranında vazgeçti, akışın süresi doldu,
 * dönüş başka bir tarayıcıdan geldi ya da Google'a ulaşılamadı.
 *
 * Kişi her durumda ana sayfaya döner: &lt;genel adres&gt;/?oauth_error=cancelled ya da failed.
 * Spring'in varsayılanı /login?error sayfasıdır; o sayfa bu uygulamada yok.
 */
@Component
public class OAuthLoginFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuthLoginFailureHandler.class);

    private final OAuthBrowserBinding binding;
    private final String appUrl;

    public OAuthLoginFailureHandler(OAuthBrowserBinding binding, @Value("${app.base-url}") String baseUrl) {
        this.binding = binding;
        this.appUrl = OAuthLoginSuccessHandler.appUrl(baseUrl);
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        OAuthLoginError error = OAuthLoginError.FAILED;
        String code = exception.getClass().getSimpleName();
        if (exception instanceof OAuth2AuthenticationException oauth) {
            code = oauth.getError().getErrorCode();
            // Google, izin ekranında "İptal"e basılınca bu kodla döner.
            if (OAuth2ErrorCodes.ACCESS_DENIED.equals(code)) {
                error = OAuthLoginError.CANCELLED;
            }
        }
        log.warn("Dış hesapla giriş yarıda kaldı — kod: {}", code);

        binding.clear(response);   // akış bitti
        response.sendRedirect(appUrl + "?oauth_error=" + error.key());
    }
}
