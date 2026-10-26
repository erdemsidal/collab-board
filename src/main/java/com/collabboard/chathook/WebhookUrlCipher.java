package com.collabboard.chathook;

import com.collabboard.common.exception.FeatureUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

import java.util.HexFormat;

/**
 * Webhook adreslerini şifreler ve çözer (AES-256-GCM, Spring Security Encryptors.delux).
 *
 * Anahtar iki değerden türetilir: INTEGRATIONS_ENC_PASSWORD ve INTEGRATIONS_ENC_SALT
 * (onaltılık). İkisi de yoksa ya da geçersizse özellik KAPALI: uydurma bir
 * varsayılan anahtar yok. Depoda duran bir anahtarla şifrelemek, şifrelememekle
 * aynı şeydi (bkz. JwtSecretGuard'daki ders).
 *
 * Neden GCM? Her şifreleme rastgele bir başlangıç vektörü kullanır (aynı adres
 * iki kez farklı görünür) ve şifreli metin değiştirilirse çözme başarısız olur;
 * veritabanına yazabilen biri adresi fark ettirmeden başka bir adrese çeviremez.
 *
 * Anahtar değişirse eski kayıtlar çözülemez: o bağlantılar yeniden eklenmeli
 * (docs/KANAL-BILDIRIMI-KURULUMU.md).
 */
@Component
public class WebhookUrlCipher {

    private static final Logger log = LoggerFactory.getLogger(WebhookUrlCipher.class);

    /** openssl rand -hex 32 → 64 karakter. Daha kısası tahmine açık. */
    static final int MIN_PASSWORD_LENGTH = 16;
    /** 8 bayt = 16 onaltılık karakter. */
    static final int MIN_SALT_HEX_LENGTH = 16;

    private final TextEncryptor encryptor;

    public WebhookUrlCipher(@Value("${app.integrations.enc-password:}") String password,
                            @Value("${app.integrations.enc-salt:}") String salt) {
        this.encryptor = build(password == null ? "" : password.trim(), salt == null ? "" : salt.trim());
    }

    private static TextEncryptor build(String password, String salt) {
        if (password.isEmpty() && salt.isEmpty()) {
            log.info("Kanal bildirimleri kapalı: INTEGRATIONS_ENC_PASSWORD ve INTEGRATIONS_ENC_SALT tanımlı değil.");
            return null;
        }
        String problem = problemWith(password, salt);
        if (problem != null) {
            log.warn("""

                    ┌─────────────────────────────────────────────────────────────┐
                    │  KANAL BİLDİRİMLERİ KAPALI: ŞİFRELEME ANAHTARI GEÇERSİZ     │
                    ├─────────────────────────────────────────────────────────────┤
                    │  {}
                    │                                                             │
                    │  İki değeri de şöyle üret:  openssl rand -hex 32            │
                    │  Kurulum: docs/KANAL-BILDIRIMI-KURULUMU.md                  │
                    └─────────────────────────────────────────────────────────────┘""", problem);
            return null;
        }
        return Encryptors.delux(password, salt);
    }

    /** Geçersizse nedeni, geçerliyse null. Değerlerin kendisi loga yazılmaz. */
    static String problemWith(String password, String salt) {
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return "INTEGRATIONS_ENC_PASSWORD en az " + MIN_PASSWORD_LENGTH + " karakter olmalı.";
        }
        if (salt.length() < MIN_SALT_HEX_LENGTH || salt.length() % 2 != 0) {
            return "INTEGRATIONS_ENC_SALT en az " + MIN_SALT_HEX_LENGTH + " karakterlik, çift uzunlukta onaltılık bir değer olmalı.";
        }
        try {
            HexFormat.of().parseHex(salt);
        } catch (IllegalArgumentException e) {
            return "INTEGRATIONS_ENC_SALT yalnızca 0-9 ve a-f karakterlerinden oluşmalı (onaltılık).";
        }
        return null;
    }

    /** Anahtarlar tanımlı ve geçerli mi? Değilse kanal bildirimleri kapalı. */
    public boolean isAvailable() {
        return encryptor != null;
    }

    /** Özellik kapalıysa 503 ve yerelleştirilmiş, açık bir mesaj. */
    public void requireAvailable() {
        if (!isAvailable()) {
            throw new FeatureUnavailableException("error.chatHook.unavailable");
        }
    }

    public String encrypt(String url) {
        requireAvailable();
        return encryptor.encrypt(url);
    }

    /** Çözülemezse (anahtar değişmiş, kayıt bozulmuş) IllegalStateException; adres loga yazılmaz. */
    public String decrypt(String encrypted) {
        requireAvailable();
        try {
            return encryptor.decrypt(encrypted);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Webhook adresi çözülemedi (anahtar değişmiş olabilir)");
        }
    }
}
