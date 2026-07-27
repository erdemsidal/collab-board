package com.collabboard.auth;

import com.collabboard.auth.dto.AuthResponse;
import com.collabboard.auth.dto.LoginRequest;
import com.collabboard.auth.dto.RegisterRequest;
import com.collabboard.auth.dto.TokenRefreshRequest;
import com.collabboard.auth.dto.TokenRefreshResponse;
import com.collabboard.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j // Lombok: otomatik olarak private static final Logger log = ... üretir
@RestController // Bu sınıfın bir REST controller olduğunu belirtir; her metot JSON döner
@RequestMapping("/api/auth") // Tüm endpoint'ler /api/auth altında gruplanır
@Tag(name = "Authentication") // Swagger UI'da bu controller "Authentication" başlığı altında görünür
public class AuthController {

    private final AuthService authService;

    // Constructor injection: Spring Boot tek constructor varsa otomatik enjeksiyon yapar
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Yeni kullanıcı kaydı endpoint'i.
     * Başarılı kayıt sonrası token üretilmez — kullanıcı ayrıca login yapmalıdır.
     */
    @Operation(summary = "Yeni kullanıcı kaydı")
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Kayıt isteği alındı — /api/auth/register");

        // AuthService üzerinden kayıt işlemini gerçekleştir
        UserResponse response = authService.register(request);

        // 201 Created — yeni kaynak (kullanıcı) başarıyla oluşturuldu
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Kullanıcı girişi endpoint'i.
     * Başarılı giriş sonrası access token ve refresh token döner.
     */
    @Operation(summary = "Kullanıcı girişi")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Giriş isteği alındı — /api/auth/login");

        // AuthService üzerinden email + şifre doğrulaması ve token üretimi
        AuthResponse response = authService.login(request);

        // 200 OK — giriş başarılı, token'lar body'de döner
        return ResponseEntity.ok(response);
    }

    /**
     * Kullanıcı çıkışı endpoint'i.
     * Refresh token Redis'ten silinir; access token client tarafında temizlenmelidir.
     */
    @Operation(summary = "Kullanıcı çıkışı")
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@Valid @RequestBody TokenRefreshRequest request) {
        log.info("Çıkış isteği alındı — /api/auth/logout");

        // AuthService üzerinden refresh token'ı Redis'ten sil
        authService.logout(request.refreshToken());

        // 200 OK — basit mesaj ile başarılı çıkış bildirimi
        return ResponseEntity.ok(Map.of("message", "Çıkış başarılı"));
    }

    /**
     * Access token yenileme endpoint'i (token rotation).
     * Eski refresh token geçersiz olur, yeni access + refresh token döner.
     */
    @Operation(summary = "Access token yenileme")
    @PostMapping("/refresh")
    public ResponseEntity<TokenRefreshResponse> refresh(@Valid @RequestBody TokenRefreshRequest request) {
        log.info("Token yenileme isteği alındı — /api/auth/refresh");

        // AuthService üzerinden token rotation: eski token → yeni access + refresh token
        TokenRefreshResponse response = authService.refreshAccessToken(request.refreshToken());

        // 200 OK — yeni token'lar body'de döner
        return ResponseEntity.ok(response);
    }
}
