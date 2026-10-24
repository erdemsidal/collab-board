package com.collabboard.security;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.collabboard.user.UserRepository;
import com.collabboard.user.entity.User;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    /**
     * Şifresiz hesabın (Google ile açılmış) yerine konan özet.
     *
     * Spring Security boş şifreli kullanıcı kabul etmiyor; null verilse her
     * istekte hata fırlatır. Boş dize de olmaz: BCrypt onu hiç hesaplamadan
     * reddeder ve cevap ölçülebilir biçimde hızlı döner, yani "bu hesabın
     * şifresi yok" bilgisi zamanlamadan sızar.
     *
     * Bunun yerine açılışta rastgele bir değerin özeti üretilir ve değerin
     * kendisi atılır. Hiçbir şifre bu özetle eşleşmez, karşılaştırma da gerçek
     * bir şifreninki kadar sürer. Sonuç: şifresiz hesapla şifreyle giriş,
     * yanlış şifreyle tıpatıp aynı biçimde reddedilir.
     */
    private static final String UNUSABLE_PASSWORD_HASH = unusablePasswordHash();

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(username).orElseThrow(
            () -> new UsernameNotFoundException("Kullanıcı Bulunamadı" + username));

        List<GrantedAuthority> authorities = user.getRoles().stream().map(
            role -> new SimpleGrantedAuthority("ROLE_" + role.getName())
        ).collect(Collectors.toList());

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.hasPassword() ? user.getPassword() : UNUSABLE_PASSWORD_HASH)
                .authorities(authorities)
                .disabled(!user.isEnabled())
                .build();
    }

    private static String unusablePasswordHash() {
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        return new BCryptPasswordEncoder().encode(Base64.getEncoder().encodeToString(secret));
    }
}
