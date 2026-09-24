package com.collabboard.auth;

import com.collabboard.auth.entity.PasswordResetToken;
import com.collabboard.common.exception.BadRequestException;
import com.collabboard.mail.MailService;
import com.collabboard.mail.PasswordResetMailTemplate;
import com.collabboard.security.RefreshTokenService;
import com.collabboard.user.UserRepository;
import com.collabboard.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Şifre sıfırlama: bağlantı iste → postadan gel → yeni şifreyi belirle.
 *
 * E-posta doğrulamayla (EmailVerificationService) aynı iskelet, üç farkla:
 *  1. Jeton özetlenerek saklanır — sızarsa hesap ele geçer, o kadar ucuz değil.
 *  2. Ömür 30 dakika, 24 saat değil — kişi bu postayı hemen bekliyor.
 *  3. Başarılı sıfırlama TÜM oturumları kapatır.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final PasswordResetMailTemplate template;
    private final String baseUrl;
    private final int expiryMinutes;

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository tokenRepository,
                                RefreshTokenService refreshTokenService,
                                PasswordEncoder passwordEncoder,
                                MailService mailService,
                                PasswordResetMailTemplate template,
                                @Value("${app.base-url}") String baseUrl,
                                @Value("${app.mail.password-reset-minutes:30}") int expiryMinutes) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.template = template;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.expiryMinutes = expiryMinutes;
    }

    /**
     * Sıfırlama bağlantısı gönderir.
     *
     * Adres kayıtlı değilse de SESSİZCE başarılı döner. Aksi hâlde bu uç,
     * "bu e-posta sistemde var mı" sorusunu herkese cevaplardı — saldırgan önce
     * hangi adreslerin kayıtlı olduğunu toplar, sonra onlara odaklanırdı.
     */
    @Transactional
    public void request(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            log.info("Şifre sıfırlama isteği karşılıksız kaldı (kayıt yok)");
            return;
        }

        tokenRepository.invalidateAllForUser(user.getId(), LocalDateTime.now());

        String rawToken = generateToken();
        tokenRepository.save(PasswordResetToken.builder()
                .userId(user.getId())
                .tokenHash(hash(rawToken))
                .expiresAt(LocalDateTime.now().plusMinutes(expiryMinutes))
                .build());

        // Ham jeton yalnızca postada yaşar; veritabanında yalnızca özeti var.
        String link = baseUrl + "/?reset=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        mailService.send(user.getEmail(), template.subject(),
                template.html(user.getFirstName(), link, expiryMinutes),
                template.text(user.getFirstName(), link, expiryMinutes));

        log.info("Şifre sıfırlama bağlantısı üretildi — userId: {}", user.getId());
    }

    /**
     * Yeni şifreyi belirler.
     *
     * Yan etkiler bilinçli:
     *  - Tüm oturumlar kapatılır. Şifre çalındığı için sıfırlanıyorsa, çalan
     *    kişinin açık oturumu da kapanmalı; yoksa sıfırlamanın anlamı kalmaz.
     *  - Hesap doğrulanmamışsa doğrulanır. Bu postayı açabilmek, adresin sahibi
     *    olmanın kanıtı — doğrulama postasının kanıtladığı şeyin aynısı.
     *
     * Hata mesajları kasıtlı olarak ayrışık: "süresi dolmuş" ile "kullanılmış"
     * kullanıcıya farklı şeyler söyler ve ikisi de saldırgana bilgi vermez
     * (jetonu zaten bilmeden bu mesajları göremez).
     */
    @Transactional
    public void reset(String rawToken, String newPassword) {
        PasswordResetToken token = tokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new BadRequestException(
                        "Sıfırlama bağlantısı geçersiz. Yeni bir bağlantı isteyebilirsin."));

        if (token.isUsed()) {
            throw new BadRequestException(
                    "Bu bağlantı daha önce kullanılmış. Yeni bir bağlantı isteyebilirsin.");
        }
        if (token.isExpired()) {
            throw new BadRequestException(
                    "Bu bağlantının süresi dolmuş. Yeni bir bağlantı isteyebilirsin.");
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new BadRequestException("Sıfırlama bağlantısı geçersiz."));

        token.setUsedAt(LocalDateTime.now());
        user.setPassword(passwordEncoder.encode(newPassword));
        if (!user.isEnabled()) {
            user.setEnabled(true);
            log.info("Şifre sıfırlama adresi doğruladı, hesap etkinleştirildi — userId: {}", user.getId());
        }

        int closed = refreshTokenService.revokeAll(user.getId());
        log.info("Şifre sıfırlandı — userId: {}, kapatılan oturum: {}", user.getId(), closed);
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 — tuzsuz (salt) ve tek tur, şifreler için KULLANILMAZDI.
     *
     * Burada yeterli çünkü girdi insan seçimi bir şifre değil, 256 bit rastgele
     * bir dize: sözlük saldırısı ya da ön hesaplanmış tablo işe yaramaz. BCrypt
     * gibi kasten yavaş bir algoritma yalnızca her istekte boşa işlemci harcardı.
     */
    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 her JVM'de bulunur", e);
        }
    }
}
