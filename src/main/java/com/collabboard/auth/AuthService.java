package com.collabboard.auth;

import com.collabboard.auth.dto.AuthResponse;
import com.collabboard.auth.dto.LoginRequest;
import com.collabboard.auth.dto.RegisterRequest;
import com.collabboard.auth.dto.TokenRefreshResponse;
import com.collabboard.common.exception.ResourceNotFoundException;
import com.collabboard.security.CustomUserDetailsService;
import com.collabboard.security.JwtTokenProvider;
import com.collabboard.security.RefreshTokenService;
import com.collabboard.user.LastSeenTracker;
import com.collabboard.user.UserRepository;
import com.collabboard.user.UserResponseMapper;
import com.collabboard.user.dto.UserResponse;
import com.collabboard.user.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Slf4j // Lombok: otomatik olarak private static final Logger log = ... üretir
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final AccountRegistrar accountRegistrar;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService customUserDetailsService;
    private final EmailVerificationService emailVerificationService;
    private final UserResponseMapper userResponseMapper;
    private final LastSeenTracker lastSeenTracker;

    // Constructor injection: tüm bağımlılıklar Spring tarafından otomatik enjekte edilir
    public AuthService(UserRepository userRepository,
                       AccountRegistrar accountRegistrar,
                       JwtTokenProvider jwtTokenProvider,
                       RefreshTokenService refreshTokenService,
                       AuthenticationManager authenticationManager,
                       CustomUserDetailsService customUserDetailsService,
                       EmailVerificationService emailVerificationService,
                       UserResponseMapper userResponseMapper,
                       LastSeenTracker lastSeenTracker) {
        this.userRepository = userRepository;
        this.accountRegistrar = accountRegistrar;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.authenticationManager = authenticationManager;
        this.customUserDetailsService = customUserDetailsService;
        this.emailVerificationService = emailVerificationService;
        this.userResponseMapper = userResponseMapper;
        this.lastSeenTracker = lastSeenTracker;
    }

    /**
     * Yeni kullanıcı kaydı oluşturur.
     *
     * Hesap PASİF açılır ve e-postaya doğrulama bağlantısı gider; kullanıcı
     * bağlantıya tıklayana kadar giriş yapamaz. Token üretmiyoruz — kayıt
     * tek başına içeri girmeye yetmez.
     */
    @Transactional
    public UserResponse register(RegisterRequest request, Locale locale) {
        // PASİF açılır: e-posta doğrulanana kadar giriş yapılamaz.
        // Dil kayıt isteğinden gelir; doğrulama postası da o dilde gider.
        User savedUser = accountRegistrar.create(request.firstName(), request.lastName(),
                request.email(), request.password(), false, locale);

        log.info("Yeni kullanıcı kaydedildi (doğrulama bekliyor) — id: {}, email: {}",
                savedUser.getId(), savedUser.getEmail());

        // Posta gönderimi asenkron ve hataya toleranslı: SMTP kesintisi kaydı
        // geri almaz, kullanıcı "yeniden gönder" diyebilir.
        emailVerificationService.issue(savedUser);

        // Entity → DTO dönüşümü; entity asla doğrudan client'a expose edilmez
        return userResponseMapper.toResponse(savedUser);
    }

    /**
     * Kullanıcı girişi yapar, access token ve refresh token üretir.
     */
    public AuthResponse login(LoginRequest request) {
        // AuthenticationManager ile email + şifre doğrulaması yap
        // Başarısız olursa Spring Security otomatik olarak AuthenticationException fırlatır
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        // Doğrulanmış kullanıcının detaylarını al (UserDetails nesnesi)
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        // Kullanıcıyı DB'den çek — refresh token için userId ve response için kullanıcı bilgisi lazım
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound.user", request.email()));

        log.info("Kullanıcı giriş yaptı — email: {}", request.email());
        return tokensFor(userDetails, user);
    }

    /**
     * Kimliği başka bir yoldan zaten doğrulanmış kişiye oturum açar.
     *
     * "Google ile devam et" bunu kullanır: kimliği Google doğruladı, şifre
     * sorulmaz. Yanıt şifreyle girişinkiyle birebir aynıdır; istemci iki yolu
     * ayırt etmek zorunda kalmaz.
     */
    public AuthResponse issueTokens(User user) {
        return tokensFor(customUserDetailsService.loadUserByUsername(user.getEmail()), user);
    }

    private AuthResponse tokensFor(UserDetails userDetails, User user) {
        // JWT access token üret (kısa ömürlü — 15 dk)
        String accessToken = jwtTokenProvider.generateToken(userDetails);

        // Redis'te refresh token oluştur (uzun ömürlü — varsayılan 30 gün, UUID tabanlı)
        String refreshToken = refreshTokenService.createRefreshToken(user.getId());

        // Access token, refresh token ve kullanıcı bilgisini döndür
        return new AuthResponse(accessToken, refreshToken, userResponseMapper.toResponse(user));
    }

    /**
     * Kullanıcı çıkışı — refresh token'ı Redis'ten siler.
     * Access token client tarafında silinir (stateless yapıda server-side invalidation yok).
     */
    public void logout(String refreshToken) {
        // Refresh token'ı Redis'ten sil; artık bu token ile yeni access token alınamaz
        refreshTokenService.deleteRefreshToken(refreshToken);

        log.info("Kullanıcı çıkış yaptı — refresh token silindi");
    }

    /**
     * Refresh token ile yeni access token ve yeni refresh token üretir (token rotation).
     * Eski refresh token geçersiz hale gelir — çalınmış token tekrar kullanılamaz.
     */
    public TokenRefreshResponse refreshAccessToken(String refreshToken) {
        // Tek çağrıda: eski token doğrulanır → silinir → yeni token üretilir → userId + newToken döner
        // Geçersiz token ise TokenRefreshException fırlatılır (403)
        RefreshTokenService.RotationResult result = refreshTokenService.rotateRefreshToken(refreshToken);

        // userId ile kullanıcıyı bul — yeni access token üretmek için email gerekli
        User user = userRepository.findById(result.userId())
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound.user", result.userId()));

        // CustomUserDetailsService üzerinden UserDetails al — DRY: UserDetails üretim mantığı tek yerde
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(user.getEmail());

        String newAccessToken = jwtTokenProvider.generateToken(userDetails);

        log.info("Token yenilendi — userId: {}", result.userId());

        // Sayfayı açık bırakıp yalnızca canlı bağlantıyla çalışan kişi REST isteği
        // atmayabilir; jetonunu ise yeniler. Son görülme bu yüzden burada da yazılır.
        lastSeenTracker.seen(user.getEmail());

        // Yeni access token ve yeni refresh token'ı döndür
        return new TokenRefreshResponse(newAccessToken, result.newToken());
    }
}
