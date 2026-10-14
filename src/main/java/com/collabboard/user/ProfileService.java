package com.collabboard.user;

import com.collabboard.auth.dto.TokenRefreshResponse;
import com.collabboard.common.exception.BadRequestException;
import com.collabboard.common.exception.ResourceNotFoundException;
import com.collabboard.security.CustomUserDetailsService;
import com.collabboard.security.JwtTokenProvider;
import com.collabboard.security.RefreshTokenService;
import com.collabboard.user.dto.UserResponse;
import com.collabboard.user.entity.User;
import com.collabboard.user.entity.UserAvatar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

/**
 * Kullanıcının kendi profili: ad, şifre, fotoğraf.
 *
 * Her işlem yalnızca oturumdaki kişinin kendi hesabına uygulanır; kimlik
 * istekten değil kimlik doğrulamadan gelir, başka birinin profiline dokunmanın
 * yolu yoktur.
 */
@Service
@Transactional
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    /**
     * Fotoğraf üst sınırı. Arayüz görseli yüklemeden önce 256×256'ya küçültüyor;
     * bu boyutta JPEG/WebP birkaç on kilobayt eder. Sınır, arayüzü atlayıp ham
     * dosya gönderenlere karşı.
     */
    static final int MAX_AVATAR_BYTES = 512 * 1024;

    private final UserRepository userRepository;
    private final UserAvatarRepository avatarRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;

    public ProfileService(UserRepository userRepository, UserAvatarRepository avatarRepository,
                          PasswordEncoder passwordEncoder, RefreshTokenService refreshTokenService,
                          JwtTokenProvider jwtTokenProvider, CustomUserDetailsService userDetailsService) {
        this.userRepository = userRepository;
        this.avatarRepository = avatarRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
    }

    public UserResponse updateName(String email, String firstName, String lastName) {
        User user = userByEmail(email);
        user.setFirstName(firstName.trim());
        user.setLastName(lastName.trim());
        log.info("Profil güncellendi — userId: {}", user.getId());
        return UserResponse.fromEntity(user);
    }

    /**
     * Şifreyi değiştirir ve DİĞER cihazlardaki oturumları kapatır.
     *
     * Bu cihazın oturumu da kapanır (bütün refresh token'lar silinir) ama yerine
     * hemen yenisi verilir; kişi şifresini değiştirdiği ekranda kalır. Diğer
     * cihazlar en geç erişim jetonlarının ömrü dolunca (15 dk) dışarıda kalır.
     *
     * Mevcut şifre istenir: açık unutulmuş bir oturumu ele geçiren biri, şifreyi
     * değiştirip hesabı sahibinin elinden alamasın.
     */
    public TokenRefreshResponse changePassword(String email, String currentPassword, String newPassword) {
        User user = userByEmail(email);
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BadRequestException("Mevcut şifre yanlış.");
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BadRequestException("Yeni şifre mevcut şifrenle aynı olamaz.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        int closed = refreshTokenService.revokeAll(user.getId());
        log.info("Şifre değiştirildi — userId: {}, kapatılan oturum: {}", user.getId(), closed);

        String accessToken = jwtTokenProvider.generateToken(userDetailsService.loadUserByUsername(user.getEmail()));
        String refreshToken = refreshTokenService.createRefreshToken(user.getId());
        return new TokenRefreshResponse(accessToken, refreshToken);
    }

    /**
     * Profil fotoğrafını değiştirir.
     *
     * Dosya türü istemcinin söylediğine (Content-Type) değil, dosyanın İLK
     * BAYTLARINA bakılarak belirlenir: başlık uydurulabilir, imza uydurulamaz.
     * SVG kabul edilmez — içinde betik taşıyabilir. Fotoğraf yalnızca bu üç
     * biçimde ve kendi türüyle sunulduğu için tarayıcı onu görsel dışında bir
     * şey olarak yorumlamaz.
     */
    public UserResponse setAvatar(String email, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Bir görsel seçmelisin.");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new BadRequestException("Görsel çok büyük (en fazla 512 KB).");
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Görsel okunamadı.");
        }
        String contentType = detectImageType(data);
        if (contentType == null) {
            throw new BadRequestException("Yalnızca PNG, JPEG ya da WebP yükleyebilirsin.");
        }

        User user = userByEmail(email);
        avatarRepository.deleteByUserId(user.getId());

        // Yeni anahtar: adres değişir, böylece tarayıcı önbelleğindeki eski fotoğraf gösterilmez.
        String key = UUID.randomUUID().toString();
        avatarRepository.save(UserAvatar.builder()
                .avatarKey(key).userId(user.getId()).contentType(contentType).data(data)
                .createdAt(LocalDateTime.now()).build());
        user.setAvatarKey(key);

        log.info("Profil fotoğrafı değişti — userId: {}, tür: {}, boyut: {} B", user.getId(), contentType, data.length);
        return UserResponse.fromEntity(user);
    }

    public UserResponse removeAvatar(String email) {
        User user = userByEmail(email);
        avatarRepository.deleteByUserId(user.getId());
        user.setAvatarKey(null);
        return UserResponse.fromEntity(user);
    }

    @Transactional(readOnly = true)
    public UserAvatar avatar(String key) {
        return avatarRepository.findById(key)
                .orElseThrow(() -> new ResourceNotFoundException("Fotoğraf", "anahtar", key));
    }

    /** Dosya imzasından görsel türü; tanınmıyorsa null. */
    static String detectImageType(byte[] d) {
        if (startsWith(d, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (startsWith(d, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        // WebP: "RIFF" + 4 bayt uzunluk + "WEBP"
        if (d.length >= 12 && startsWith(d, 'R', 'I', 'F', 'F')
                && Arrays.equals(Arrays.copyOfRange(d, 8, 12), new byte[]{'W', 'E', 'B', 'P'})) {
            return "image/webp";
        }
        return null;
    }

    private static boolean startsWith(byte[] data, int... signature) {
        if (data.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((data[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private User userByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı", "email", email));
    }
}
