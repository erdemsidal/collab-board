package com.collabboard.auth;

import com.collabboard.auth.entity.EmailVerificationToken;
import com.collabboard.common.exception.BadRequestException;
import com.collabboard.mail.MailService;
import com.collabboard.mail.VerificationMailTemplate;
import com.collabboard.user.UserRepository;
import com.collabboard.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

/**
 * E-posta doğrulama akışı: jeton üret → posta yolla → bağlantıyı karşıla.
 *
 * Hesap kayıtta PASİF açılır (users.enabled = false). Spring Security devre dışı
 * kullanıcıyı giriş aşamasında kendiliğinden reddeder (CustomUserDetailsService
 * .disabled(...) diyor), dolayısıyla girişe ayrıca bir kontrol eklemek gerekmez —
 * kural tek yerde durur.
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    /**
     * Jeton uzunluğu (bayt). 32 bayt = 256 bit rastgelelik; kaba kuvvetle
     * tahmin edilemez. Base64-URL ile 43 karakterlik bir dizeye dönüşür.
     */
    private static final int TOKEN_BYTES = 32;

    /** SecureRandom, Random'dan farklı olarak tahmin edilemez üretir — jeton için şart. */
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final MailService mailService;
    private final VerificationMailTemplate template;
    private final String baseUrl;
    private final int expiryHours;

    public EmailVerificationService(UserRepository userRepository,
                                    EmailVerificationTokenRepository tokenRepository,
                                    MailService mailService,
                                    VerificationMailTemplate template,
                                    @Value("${app.base-url}") String baseUrl,
                                    @Value("${app.mail.verification-token-hours}") int expiryHours) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.mailService = mailService;
        this.template = template;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.expiryHours = expiryHours;
    }

    /**
     * Kullanıcıya yeni bir doğrulama bağlantısı üretip yollar.
     *
     * Önceki jetonları geçersiz kılar: aynı anda birden fazla geçerli bağlantı
     * dolaşmamalı, kullanıcı en son gelen postaya güvenir.
     */
    @Transactional
    public void issue(User user) {
        tokenRepository.invalidateAllForUser(user.getId(), LocalDateTime.now());

        EmailVerificationToken token = EmailVerificationToken.builder()
                .userId(user.getId())
                .token(generateToken())
                .expiresAt(LocalDateTime.now().plusHours(expiryHours))
                .build();
        tokenRepository.save(token);

        String link = baseUrl + "/?verify=" + URLEncoder.encode(token.getToken(), StandardCharsets.UTF_8);
        mailService.send(user.getEmail(), template.subject(),
                template.html(user.getFirstName(), link, expiryHours),
                template.text(user.getFirstName(), link, expiryHours));

        log.info("Doğrulama bağlantısı üretildi — userId: {}", user.getId());
    }

    /**
     * Bağlantıdaki jetonu karşılar ve hesabı etkinleştirir.
     *
     * Zaten doğrulanmış bir hesap için tekrar tıklanırsa hata vermez: kullanıcı
     * postayı ikinci kez açmış olabilir ve sonuç istediği gibidir. Hata göstermek
     * doğru davranışı cezalandırmak olurdu.
     */
    @Transactional
    public void verify(String rawToken) {
        EmailVerificationToken token = tokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new BadRequestException(
                        "Doğrulama bağlantısı geçersiz. Giriş ekranından yeni bir bağlantı isteyebilirsin."));

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new BadRequestException("Doğrulama bağlantısı geçersiz."));

        if (user.isEnabled()) {
            log.info("Zaten doğrulanmış hesap için doğrulama isteği — userId: {}", user.getId());
            return;
        }
        if (token.isUsed()) {
            throw new BadRequestException(
                    "Bu bağlantı daha önce kullanılmış. Giriş ekranından yeni bir bağlantı isteyebilirsin.");
        }
        if (token.isExpired()) {
            throw new BadRequestException(
                    "Bu bağlantının süresi dolmuş. Giriş ekranından yeni bir bağlantı isteyebilirsin.");
        }

        token.setUsedAt(LocalDateTime.now());
        user.setEnabled(true);
        log.info("Hesap doğrulandı — userId: {}", user.getId());
    }

    /**
     * Doğrulama postasını yeniden yollar.
     *
     * Adres kayıtlı değilse ya da hesap zaten doğrulanmışsa da SESSİZCE başarılı
     * döner. Aksi hâlde bu uç, "bu e-posta sistemde kayıtlı mı" sorusunu herkese
     * cevaplayan bir araca dönüşürdü (hesap sayımı / kullanıcı listeleme).
     */
    @Transactional
    public void resend(String email) {
        Optional<User> found = userRepository.findByEmail(email);
        if (found.isEmpty() || found.get().isEnabled()) {
            log.info("Yeniden gönderim isteği karşılıksız kaldı (kayıt yok ya da zaten doğrulanmış)");
            return;
        }
        issue(found.get());
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        // URL'de taşınacağı için URL-güvenli alfabe; dolgu (=) karakteri gereksiz.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
