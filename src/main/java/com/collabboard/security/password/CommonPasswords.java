package com.collabboard.security.password;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * En yaygın şifrelerin listesi (ADR 0025, güvenlik incelemesi Y3).
 *
 * NEDEN? Kural yalnızca "en az 8 karakter" idi; "12345678", "password1" ya da
 * "galatasaray" kabul ediliyordu. Saldırganlar önce tam bu listeleri dener.
 * Güncel öneri (NIST 800-63B) karmaşıklık kuralı (büyük harf + rakam + sembol)
 * DEĞİL, uzunluk ve "bilinen bir şifre olmasın" denetimidir; burada o yapılıyor.
 *
 * Liste uygulamanın içinde gelir (resources/security/common-passwords.txt):
 * dışarıya istek yok, kişinin şifresi hiçbir yere gönderilmez.
 * Karşılaştırma büyük/küçük harfe duyarsız: "Password1" de reddedilir.
 */
public final class CommonPasswords {

    private static final String RESOURCE = "/security/common-passwords.txt";

    private CommonPasswords() {
    }

    /** Şifre listede mi? Boş değer için false (o kuralı @NotBlank uygular). */
    public static boolean contains(String password) {
        if (password == null || password.isEmpty()) {
            return false;
        }
        return Holder.LIST.contains(password.toLowerCase(Locale.ROOT))
                || Holder.LIST.contains(password.toLowerCase(Locale.forLanguageTag("tr")));
    }

    /** Liste ilk kullanımda bir kez okunur (~10.000 satır, birkaç milisaniye). */
    private static final class Holder {
        static final Set<String> LIST = load();

        private static Set<String> load() {
            Set<String> list = new HashSet<>(16_384);
            try (InputStream in = CommonPasswords.class.getResourceAsStream(RESOURCE)) {
                if (in == null) {
                    throw new IllegalStateException("Yaygın şifre listesi bulunamadı: " + RESOURCE);
                }
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    if (!line.isBlank() && !line.startsWith("#")) {
                        list.add(line.strip());
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return Set.copyOf(list);
        }
    }
}
