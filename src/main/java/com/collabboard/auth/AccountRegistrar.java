package com.collabboard.auth;

import com.collabboard.common.exception.ConflictException;
import com.collabboard.common.exception.ResourceNotFoundException;
import com.collabboard.i18n.AppLocale;
import com.collabboard.user.RoleRepository;
import com.collabboard.user.UserRepository;
import com.collabboard.user.entity.Role;
import com.collabboard.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

/**
 * Yeni hesap açmanın tek yolu.
 *
 * İki kapıdan gelinir: kayıt formu (şifreli, doğrulanana kadar pasif) ve
 * "Google ile devam et" (şifresiz, hemen etkin). Hesabın nasıl kurulduğu
 * (varsayılan rol, şifrenin özetlenmesi, aynı adresin ikinci kez kaydını
 * engellemek) burada bir kez yazılı; iki kapı ayrışmasın.
 *
 * Hesap açılırken başka bir şey yapılmıyor, bilinçli olarak: kişisel çalışma
 * alanı ilk ihtiyaçta açılır (WorkspaceService.personalWorkspaceFor) ve bu
 * adrese önceden gönderilmiş pano davetleri kişi zil menüsünü ilk açtığında
 * bildirime dönüşür (NotificationService.backfillInvites). İkisi de hesabın
 * kendisine ve e-posta adresine bakar, hesabın hangi kapıdan açıldığına değil.
 */
@Service
public class AccountRegistrar {

    private static final Logger log = LoggerFactory.getLogger(AccountRegistrar.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountRegistrar(UserRepository userRepository, RoleRepository roleRepository,
                            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * @param rawPassword ham şifre; null ise hesap şifresiz açılır (yalnızca dış hesapla girilir)
     * @param enabled     false: e-posta doğrulanana kadar giriş yapılamaz
     * @param locale      arayüzün ve postaların dili; kayıt isteğinin dilinden gelir (ADR 0020)
     */
    @Transactional
    public User create(String firstName, String lastName, String email, String rawPassword, boolean enabled,
                       Locale locale) {
        // Email daha önce kayıtlı mı kontrol et; varsa ConflictException fırlat (409)
        if (userRepository.existsByEmail(email)) {
            log.warn("Kayıt başarısız — email zaten mevcut: {}", email);
            throw new ConflictException("error.email.taken", email);
        }

        // Varsayılan ROLE_USER rolünü veritabanından bul
        Role userRole = roleRepository.findByName(Role.ROLE_USER)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound.role", Role.ROLE_USER));

        User user = User.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                // Ham şifre asla DB'ye yazılmaz; şifresiz hesapta alan boş kalır.
                .password(rawPassword == null ? null : passwordEncoder.encode(rawPassword))
                // Pasif hesabı Spring Security girişte kendiliğinden reddeder
                // (CustomUserDetailsService.disabled).
                .enabled(enabled)
                .locale(AppLocale.tag(locale))
                .roles(Set.of(userRole))
                .build();

        return userRepository.save(user);
    }
}
