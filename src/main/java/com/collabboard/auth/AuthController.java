package com.collabboard.auth;

import com.collabboard.auth.dto.AuthResponse;
import com.collabboard.auth.dto.ForgotPasswordRequest;
import com.collabboard.auth.dto.LoginRequest;
import com.collabboard.auth.dto.RegisterRequest;
import com.collabboard.auth.dto.ResendVerificationRequest;
import com.collabboard.auth.dto.ResetPasswordRequest;
import com.collabboard.auth.dto.TokenRefreshRequest;
import com.collabboard.auth.dto.TokenRefreshResponse;
import com.collabboard.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, EmailVerificationService emailVerificationService,
                          PasswordResetService passwordResetService) {
        this.authService = authService;
        this.emailVerificationService = emailVerificationService;
        this.passwordResetService = passwordResetService;
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

    /**
     * E-posta doğrulama.
     *
     * Bağlantı postadan gelir ve tarayıcıda açılır; bu yüzden GET ve kimliksiz.
     * Jeton sorgu dizesinde taşınıyor — bu bilinçli bir ödünç: tek kullanımlık,
     * kısa ömürlü ve yalnızca hesabı etkinleştirmeye yarıyor. Kalıcı bir kimlik
     * jetonu asla böyle taşınmazdı (bkz. ADR 0005).
     */
    @Operation(summary = "E-posta doğrulama")
    @GetMapping("/verify")
    public ResponseEntity<Map<String, String>> verify(@RequestParam String token) {
        log.info("Doğrulama isteği alındı — /api/auth/verify");
        emailVerificationService.verify(token);
        return ResponseEntity.ok(Map.of("message", "E-posta adresin doğrulandı. Artık giriş yapabilirsin."));
    }

    /**
     * Doğrulama postasını yeniden gönderir.
     *
     * Adres kayıtlı olmasa da başarılı döner — aksi hâlde bu uç, "bu e-posta
     * sistemde var mı" sorusunu herkese cevaplayan bir araca dönüşürdü.
     */
    @Operation(summary = "Doğrulama postasını yeniden gönder")
    @PostMapping("/resend-verification")
    public ResponseEntity<Map<String, String>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        log.info("Yeniden gönderim isteği alındı — /api/auth/resend-verification");
        emailVerificationService.resend(request.email());
        return ResponseEntity.ok(Map.of("message",
                "Adres kayıtlıysa doğrulama bağlantısı yeniden gönderildi."));
    }

    /**
     * Şifre sıfırlama bağlantısı ister.
     *
     * Adres kayıtlı olsa da olmasa da AYNI cevap döner; aksi hâlde bu uç kayıtlı
     * adresleri tek tek sorgulamaya yarayan bir araca dönüşürdü.
     */
    @Operation(summary = "Şifre sıfırlama bağlantısı iste")
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        log.info("Şifre sıfırlama isteği alındı — /api/auth/forgot-password");
        passwordResetService.request(request.email());
        return ResponseEntity.ok(Map.of("message",
                "Adres kayıtlıysa şifre sıfırlama bağlantısı gönderildi. Gelen kutunu kontrol et."));
    }

    /**
     * Bağlantıdaki jetonla yeni şifreyi belirler.
     *
     * POST, GET değil: doğrulama bağlantısından farklı olarak burada bir form
     * gönderiliyor ve yeni şifre gövdede taşınıyor — asla URL'de değil.
     */
    @Operation(summary = "Yeni şifreyi belirle")
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        log.info("Şifre sıfırlama tamamlama isteği alındı — /api/auth/reset-password");
        passwordResetService.reset(request.token(), request.newPassword());
        return ResponseEntity.ok(Map.of("message",
                "Şifren güncellendi. Tüm cihazlardaki oturumların kapatıldı; yeni şifrenle giriş yap."));
    }
}