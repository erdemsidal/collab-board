package com.collabboard.auth.oauth;

import com.collabboard.auth.AccountRegistrar;
import com.collabboard.common.tx.AfterCommit;
import com.collabboard.i18n.AppLocale;
import com.collabboard.mail.AccountLinkedMailTemplate;
import com.collabboard.mail.MailService;
import com.collabboard.mail.WelcomeMailTemplate;
import com.collabboard.user.UserIdentityRepository;
import com.collabboard.user.UserRepository;
import com.collabboard.user.entity.User;
import com.collabboard.user.entity.UserIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dış hesaptan gelen kişiyi bir CollabBoard hesabına çevirir: bulur, bağlar ya da açar.
 *
 * Sağlayıcıdan bağımsızdır; yalnızca OAuthProfile tanır. Sırası önemli:
 *
 *  1. Adres doğrulanmamışsa dur. Doğrulanmamış bir adresle hesap bağlamak,
 *     başkasının adresini yazıp onun hesabına girmenin yolu olurdu.
 *  2. Bu dış hesap daha önce bağlandıysa o hesap. Eşleştirme sağlayıcının kalıcı
 *     kimliğiyle (subject), e-postayla değil.
 *  3. Aynı adresle (büyük/küçük harf fark etmez) bir hesap varsa ona bağla ve
 *     sahibine güvenlik postası gönder.
 *  4. Hiçbiri yoksa yeni hesap aç: kayıt formuyla aynı yoldan (AccountRegistrar),
 *     ama şifresiz ve hemen etkin. Karşılama postası gönder.
 *
 * Hepsi tek işlemde (transaction): yarıda kalan bir bağlama, ardında sahipsiz bir
 * hesap ya da hesapsız bir bağ bırakmaz.
 */
@Service
public class OAuthAccountService {

    private static final Logger log = LoggerFactory.getLogger(OAuthAccountService.class);

    /** users.first_name / last_name sütunlarının uzunluğu (V1). */
    private static final int NAME_MAX = 100;

    private final UserRepository userRepository;
    private final UserIdentityRepository identityRepository;
    private final AccountRegistrar accountRegistrar;
    private final MailService mailService;
    private final WelcomeMailTemplate welcomeMail;
    private final AccountLinkedMailTemplate linkedMail;
    private final Map<String, String> providerNames;
    private final String appLink;

    public OAuthAccountService(UserRepository userRepository, UserIdentityRepository identityRepository,
                               AccountRegistrar accountRegistrar, MailService mailService,
                               WelcomeMailTemplate welcomeMail, AccountLinkedMailTemplate linkedMail,
                               List<OAuthProvider> providers, @Value("${app.base-url}") String baseUrl) {
        this.userRepository = userRepository;
        this.identityRepository = identityRepository;
        this.accountRegistrar = accountRegistrar;
        this.mailService = mailService;
        this.welcomeMail = welcomeMail;
        this.linkedMail = linkedMail;
        this.providerNames = providers.stream()
                .collect(Collectors.toMap(OAuthProvider::id, OAuthProvider::displayName));
        this.appLink = (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl) + "/";
    }

    /**
     * @param locale dönüş isteğinin dili (Accept-Language); yalnızca YENİ açılan hesaba
     *               yazılır. Var olan hesabın dili ve postası onun kayıtlı tercihinde kalır.
     */
    @Transactional
    public User resolve(OAuthProfile profile, Locale locale) {
        if (!profile.emailVerified() || isBlank(profile.email())) {
            throw new OAuthLoginException(OAuthLoginError.EMAIL_UNVERIFIED,
                    "Sağlayıcı adresi doğrulamamış — sağlayıcı: " + profile.provider());
        }
        if (isBlank(profile.subject())) {
            throw new OAuthLoginException(OAuthLoginError.FAILED,
                    "Sağlayıcı kalıcı kimlik (subject) vermedi — sağlayıcı: " + profile.provider());
        }

        // 2. Bu dış hesap zaten bağlı.
        Optional<UserIdentity> known = identityRepository.findByProviderAndSubject(profile.provider(), profile.subject());
        if (known.isPresent()) {
            User user = userRepository.findById(known.get().getUserId())
                    .orElseThrow(() -> new OAuthLoginException(OAuthLoginError.FAILED, "Bağlı hesap bulunamadı"));
            log.info("Dış hesapla giriş — sağlayıcı: {}, userId: {}", profile.provider(), user.getId());
            return user;
        }

        String email = profile.email().trim();
        String providerName = providerNames.getOrDefault(profile.provider(), profile.provider());

        // 3. Aynı adresle açılmış bir hesap var: ona bağla.
        Optional<User> existing = userRepository.findByEmailIgnoreCase(email);
        if (existing.isPresent()) {
            User user = existing.get();
            if (!user.isEnabled()) {
                // Hesap kayıt formuyla açılmış ama adres hiç doğrulanmamış. Şimdi
                // sağlayıcı adresin bu kişiye ait olduğunu doğruladı: hesap etkinleşir.
                //
                // Şifre ise SİLİNİR. O şifreyi koyan kişi adresin sahibi olduğunu hiç
                // kanıtlamadı; başkasının adresiyle önceden kayıt olup bekleyen biri
                // olabilir. Şifre kalsaydı, gerçek sahip Google ile girdiği an o kişi
                // de aynı hesaba şifreyle girebilirdi. Sahibi isterse "Şifremi
                // unuttum" ile kendi şifresini belirler.
                user.setEnabled(true);
                user.setPassword(null);
                log.info("Doğrulanmamış hesap dış hesapla doğrulandı, kayıttaki şifre kaldırıldı — userId: {}",
                        user.getId());
            }
            link(user, profile, email);
            Instant linkedAt = Instant.now();
            Locale userLocale = AppLocale.of(user);
            AfterCommit.run(() -> sendQuietly(user.getEmail(), linkedMail.subject(userLocale, providerName),
                    linkedMail.html(userLocale, user.getFirstName(), providerName, linkedAt, appLink),
                    linkedMail.text(userLocale, user.getFirstName(), providerName, linkedAt, appLink)));
            log.info("Dış hesap var olan hesaba bağlandı — sağlayıcı: {}, userId: {}", profile.provider(), user.getId());
            return user;
        }

        // 4. Yeni hesap: kayıt formuyla aynı yol, şifresiz ve hemen etkin.
        String[] names = names(profile, email);
        User user = accountRegistrar.create(names[0], names[1], email, null, true, locale);
        link(user, profile, email);
        AfterCommit.run(() -> sendQuietly(user.getEmail(), welcomeMail.subject(locale),
                welcomeMail.html(locale, user.getFirstName(), providerName, appLink),
                welcomeMail.text(locale, user.getFirstName(), providerName, appLink)));
        log.info("Dış hesapla yeni hesap açıldı — sağlayıcı: {}, userId: {}", profile.provider(), user.getId());
        return user;
    }

    private void link(User user, OAuthProfile profile, String email) {
        identityRepository.save(UserIdentity.builder()
                .userId(user.getId())
                .provider(profile.provider())
                .subject(profile.subject())
                .email(email)
                .createdAt(LocalDateTime.now())
                .build());
    }

    /**
     * Posta gidemezse giriş bozulmaz. MailService zaten hataları yutuyor; bu
     * sarmal, işlem kaydedildikten SONRA çalıştığı için ayrıca gerekli: burada
     * fırlayan bir hata kaydedilmiş girişi kullanıcıya hata olarak gösterirdi.
     */
    private void sendQuietly(String to, String subject, String html, String text) {
        try {
            mailService.send(to, subject, html, text);
        } catch (RuntimeException e) {
            log.error("Dış hesap postası gönderilemedi — alıcı: {}", to, e);
        }
    }

    /**
     * Ad ve soyad. Sırayla: sağlayıcının ayrı verdiği ad/soyad, tam adın
     * bölünmesi (son kelime soyad: "Mehmet Ali Yılmaz" → "Mehmet Ali" + "Yılmaz"),
     * son çare olarak adresin @ öncesi. Soyad bulunamazsa boş kalır; kişi
     * profilinden ekleyebilir.
     */
    static String[] names(OAuthProfile profile, String email) {
        Function<String, String> clean = s -> s == null || s.isBlank() ? null : s.trim();
        String given = clean.apply(profile.givenName());
        String family = clean.apply(profile.familyName());
        String full = clean.apply(profile.fullName());

        String first;
        String last;
        if (given != null) {
            first = given;
            if (family != null) {
                last = family;
            } else if (full != null && full.startsWith(given + " ")) {
                last = full.substring(given.length()).trim();   // "Ada" + "Ada Lovelace" → "Lovelace"
            } else {
                last = "";
            }
        } else if (full != null) {
            int cut = full.lastIndexOf(' ');
            first = cut > 0 ? full.substring(0, cut).trim() : full;
            last = cut > 0 ? full.substring(cut + 1).trim() : (family != null ? family : "");
        } else {
            int at = email.indexOf('@');
            first = at > 0 ? email.substring(0, at) : email;
            last = family != null ? family : "";
        }
        return new String[]{truncate(first), truncate(last)};
    }

    private static String truncate(String value) {
        return value.length() <= NAME_MAX ? value : value.substring(0, NAME_MAX);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
