package com.collabboard.auth.oauth;

import com.collabboard.auth.AuthService;
import com.collabboard.auth.dto.AuthResponse;
import com.collabboard.auth.dto.OAuthExchangeRequest;
import com.collabboard.auth.dto.ProvidersResponse;
import com.collabboard.common.exception.BadRequestException;
import com.collabboard.user.UserRepository;
import com.collabboard.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dış hesapla girişin arayüze bakan iki ucu.
 *
 * Akışın kendisi (Google'a yönlendirme ve dönüş) Spring Security'nin
 * filtrelerinde: /oauth2/authorization/google ve /login/oauth2/code/google.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class OAuthController {

    private static final Logger log = LoggerFactory.getLogger(OAuthController.class);

    /** Mesaj anahtarı: "Giriş kodu geçersiz ya da süresi dolmuş..." */
    private static final String INVALID_CODE = "error.oauth.invalidCode";

    private final OAuthClients clients;
    private final OAuthLoginCodes loginCodes;
    private final OAuthBrowserBinding binding;
    private final UserRepository userRepository;
    private final AuthService authService;

    public OAuthController(OAuthClients clients, OAuthLoginCodes loginCodes, OAuthBrowserBinding binding,
                           UserRepository userRepository, AuthService authService) {
        this.clients = clients;
        this.loginCodes = loginCodes;
        this.binding = binding;
        this.userRepository = userRepository;
        this.authService = authService;
    }

    /** Giriş ekranında hangi "... ile devam et" düğmeleri görünsün? Hiçbiri kurulu değilse boş liste. */
    @Operation(summary = "Kurulu dış hesap sağlayıcıları")
    @GetMapping("/providers")
    public ProvidersResponse providers() {
        return new ProvidersResponse(clients.list().stream()
                .map(p -> new ProvidersResponse.Provider(p.id(), p.displayName()))
                .toList());
    }

    /**
     * Google dönüşündeki tek kullanımlık kodu oturum jetonlarına çevirir.
     * Yanıt şifreyle girişinkiyle (/api/auth/login) birebir aynıdır.
     */
    @Operation(summary = "Dış hesap giriş kodunu oturuma çevir")
    @PostMapping("/oauth/exchange")
    public ResponseEntity<AuthResponse> exchange(@Valid @RequestBody OAuthExchangeRequest body,
                                                 HttpServletRequest request, HttpServletResponse response) {
        Long userId = loginCodes.redeem(body.code(), binding.fingerprint(request)).orElse(null);
        binding.clear(response);   // akış her durumda burada biter
        if (userId == null) {
            throw new BadRequestException(INVALID_CODE);
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new BadRequestException(INVALID_CODE));

        log.info("Dış hesapla giriş tamamlandı — userId: {}", userId);
        return ResponseEntity.ok(authService.issueTokens(user));
    }
}
