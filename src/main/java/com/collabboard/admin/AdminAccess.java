package com.collabboard.admin;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Yönetim panelini kimin açabileceği: site sahipleri (ADR 0024).
 *
 * Sahipler bir ortam değişkeninde listelenir, veritabanında değil:
 * {@code APP_ADMIN_EMAILS=erdem@ornek.com,ikinci@ornek.com}. Ayrı bir rol tablosu
 * ya da "yönetici yap" ekranı yok; sahibi değiştirmek sunucuya erişimi olan
 * birinin işi olsun. Veritabanına yazabilen biri (ör. bir SQL açığıyla) kendini
 * sahip yapamaz.
 *
 * Adresler büyük/küçük harf farkı gözetmeden karşılaştırılır. Küçültme
 * Locale.ROOT ile: Türkçe yerelde "ADMIN".toLowerCase() "admın" olurdu ve
 * eşleşme sessizce bozulurdu.
 *
 * Sahip olmayana panelin varlığı söylenmez: uç, olmayan bir adresle aynı 404'ü
 * döner (bkz. {@link #requireOwner}).
 */
@Component
public class AdminAccess {

    private static final Logger log = LoggerFactory.getLogger(AdminAccess.class);

    private final Set<String> owners;

    public AdminAccess(@Value("${app.admin.emails:}") String emails) {
        this.owners = parse(emails);
        if (owners.isEmpty()) {
            log.info("Yönetim paneli kapalı: APP_ADMIN_EMAILS boş.");
        } else {
            log.info("Yönetim paneli açık: {} sahip adresi tanımlı.", owners.size());
        }
    }

    /**
     * Virgülle ayrılmış liste. Railway'in kutusuna yapıştırırken araya giren
     * boşluk, noktalı virgül ya da satır sonu da ayırıcı sayılır; boş parçalar atılır.
     */
    static Set<String> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split("[,;\\s]+"))
                .filter(part -> !part.isBlank())
                .map(AdminAccess::normalize)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isOwner(String email) {
        return email != null && owners.contains(normalize(email));
    }

    /**
     * Sahip değilse, olmayan bir adres istenmiş gibi davranır.
     *
     * NoResourceFoundException, Spring'in eşleşmeyen adreslerde fırlattığı
     * hatanın ta kendisi; GlobalExceptionHandler ikisine de aynı gövdeyi yazar.
     * 403 dönseydik "burada bir şey var ama sana yasak" demiş olurduk.
     */
    public void requireOwner(String email, HttpServletRequest request) throws NoResourceFoundException {
        if (!isOwner(email)) {
            log.debug("Yönetim paneli isteği sahip olmayan bir hesaptan geldi; 404 dönüldü.");
            throw new NoResourceFoundException(HttpMethod.valueOf(request.getMethod()), request.getRequestURI());
        }
    }
}
