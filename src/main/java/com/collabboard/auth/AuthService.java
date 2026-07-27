package com.collabboard.auth;

import com.collabboard.auth.dto.AuthResponse;
import com.collabboard.auth.dto.LoginRequest;
import com.collabboard.auth.dto.RegisterRequest;
import com.collabboard.auth.dto.TokenRefreshResponse;
import com.collabboard.common.exception.ConflictException;
import com.collabboard.common.exception.ResourceNotFoundException;
import com.collabboard.security.CustomUserDetailsService;
import com.collabboard.security.JwtTokenProvider;
import com.collabboard.security.RefreshTokenService;
import com.collabboard.user.RoleRepository;
import com.collabboard.user.UserRepository;
import com.collabboard.user.dto.UserResponse;
import com.collabboard.user.entity.Role;
import com.collabboard.user.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Slf4j // Lombok: otomatik olarak private static final Logger log = ... üretir
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService customUserDetailsService;

    // Constructor injection: tüm bağımlılıklar Spring tarafından otomatik enjekte edilir
    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       RefreshTokenService refreshTokenService,
                       AuthenticationManager authenticationManager,
                       CustomUserDetailsService customUserDetailsService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.authenticationManager = authenticationManager;
        this.customUserDetailsService = customUserDetailsService;
    }

    /**
     * Yeni kullanıcı kaydı oluşturur.
     * Token üretmez — kullanıcı kayıt olduktan sonra ayrıca login yapmalıdır.
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        // Email daha önce kayıtlı mı kontrol et; varsa ConflictException fırlat (409)
        if (userRepository.existsByEmail(request.email())) {
            log.warn("Kayıt başarısız — email zaten mevcut: {}", request.email());
            throw new ConflictException("Bu email adresi zaten kayıtlı: " + request.email());
        }

        // Varsayılan ROLE_USER rolünü veritabanından bul
        Role userRole = roleRepository.findByName(Role.ROLE_USER)
                .orElseThrow(() -> new ResourceNotFoundException("Varsayılan rol bulunamadı: " + Role.ROLE_USER));

        // Yeni kullanıcı entity'sini oluştur; şifreyi BCrypt ile hashle
        User user = User.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .password(passwordEncoder.encode(request.password())) // Ham şifre asla DB'ye yazılmaz
                .enabled(true)
                .roles(Set.of(userRole)) // Varsayılan olarak ROLE_USER atanır
                .build();

        // Kullanıcıyı veritabanına kaydet
        User savedUser = userRepository.save(user);

        log.info("Yeni kullanıcı kaydedildi — id: {}, email: {}", savedUser.getId(), savedUser.getEmail());

        // Entity → DTO dönüşümü; entity asla doğrudan client'a expose edilmez
        return UserResponse.fromEntity(savedUser);
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

        // JWT access token üret (kısa ömürlü — 15 dk)
        String accessToken = jwtTokenProvider.generateToken(userDetails);

        // Kullanıcıyı DB'den çek — refresh token için userId ve response için kullanıcı bilgisi lazım
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı bulunamadı: " + request.email()));

        // Redis'te refresh token oluştur (uzun ömürlü — 7 gün, UUID tabanlı)
        String refreshToken = refreshTokenService.createRefreshToken(user.getId());

        log.info("Kullanıcı giriş yaptı — email: {}", request.email());

        // Access token, refresh token ve kullanıcı bilgisini döndür
        return new AuthResponse(accessToken, refreshToken, UserResponse.fromEntity(user));
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
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı bulunamadı — id: " + result.userId()));

        // CustomUserDetailsService üzerinden UserDetails al — DRY: UserDetails üretim mantığı tek yerde
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(user.getEmail());

        String newAccessToken = jwtTokenProvider.generateToken(userDetails);

        log.info("Token yenilendi — userId: {}", result.userId());

        // Yeni access token ve yeni refresh token'ı döndür
        return new TokenRefreshResponse(newAccessToken, result.newToken());
    }
}
