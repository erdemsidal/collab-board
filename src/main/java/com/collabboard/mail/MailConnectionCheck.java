package com.collabboard.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

/**
 * Açılışta posta sunucusuna bağlanmayı dener ve sonucu açıkça bildirir.
 *
 * NEDEN GEREKLİ? Kayıt olan kullanıcı e-postasını doğrulayana kadar giriş
 * yapamıyor. Posta ayarları yanlışsa — eksik uygulama şifresi, yanlış port —
 * uygulama sorunsuz açılır, kayıt da başarılı görünür, ama HİÇ KİMSE İÇERİ
 * GİREMEZ. Üstelik gönderim asenkron ve hataya toleranslı olduğu için sorun
 * yalnızca bir log satırında kalır.
 *
 * Bu sınıf o sessizliği bozar: yanlış yapılandırma ilk kullanıcı denemeden,
 * açılışta anlaşılır.
 *
 * Uygulamayı DURDURMAZ. Panolar postadan bağımsız çalışıyor ve zaten kayıtlı
 * kullanıcılar giriş yapabiliyor; açılışı engellemek sorunu büyütmek olurdu.
 */
@Component
public class MailConnectionCheck {

    private static final Logger log = LoggerFactory.getLogger(MailConnectionCheck.class);

    private final JavaMailSender mailSender;
    private final String host;
    private final String username;
    private final String from;
    private final boolean enabled;

    public MailConnectionCheck(JavaMailSender mailSender,
                               @Value("${spring.mail.host:}") String host,
                               @Value("${spring.mail.username:}") String username,
                               @Value("${app.mail.from:}") String from,
                               @Value("${app.mail.startup-check:true}") boolean enabled) {
        this.mailSender = mailSender;
        this.host = host;
        this.username = username;
        this.from = from;
        this.enabled = enabled;
    }

    /**
     * Gönderen adresi, kimlik doğrulanan hesapla eşleşiyor mu?
     *
     * Eşleşmiyorsa çoğu sağlayıcı postayı ya reddeder ya da gönderen adresini
     * SESSİZCE kendi hesabıyla değiştirir (Gmail bunu yapar). İkisi de fark
     * edilmesi zor durumlar: ayar dosyasında yazan adres ile alıcının gördüğü
     * adres farklı olur ve kimse nedenini anlamaz.
     *
     * Başka bir adresten göndermek için o alan adının sahibi olmak ve DNS
     * kayıtlarıyla (SPF/DKIM) yetki vermek gerekir — bkz. docs/MAIL-KURULUMU.md.
     */
    private void warnIfFromMismatch() {
        String fromAddress = extractAddress(from);
        if (fromAddress.isBlank() || username.equalsIgnoreCase(fromAddress)) {
            return;
        }
        log.warn("""

                ┌─────────────────────────────────────────────────────────────┐
                │  GÖNDEREN ADRESİ EŞLEŞMİYOR                                 │
                ├─────────────────────────────────────────────────────────────┤
                │  MAIL_FROM     : {}
                │  MAIL_USERNAME : {}
                │
                │  Sunucu bu adresten göndermeye yetkin olmadığın için        │
                │  postayı ya reddeder ya da gönderen adresini sessizce       │
                │  kendi hesabınla değiştirir. Alıcının göreceği adres        │
                │  MAIL_FROM değil, MAIL_USERNAME olacak.                     │
                │                                                             │
                │  Kendi alan adından göndermek için: docs/MAIL-KURULUMU.md   │
                └─────────────────────────────────────────────────────────────┘""",
                fromAddress, username);
    }

    /** "CollabBoard <info@site.com>" → "info@site.com" */
    private String extractAddress(String value) {
        if (value == null) {
            return "";
        }
        int open = value.indexOf('<');
        int close = value.lastIndexOf('>');
        if (open >= 0 && close > open) {
            return value.substring(open + 1, close).trim();
        }
        return value.trim();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void check() {
        if (!enabled) {
            return;
        }

        if (username == null || username.isBlank()) {
            log.warn("""

                    ┌─────────────────────────────────────────────────────────────┐
                    │  POSTA AYARLARI EKSİK                                       │
                    ├─────────────────────────────────────────────────────────────┤
                    │  MAIL_USERNAME tanımlı değil. Doğrulama postaları           │
                    │  gönderilemeyecek ve YENİ KAYITLAR GİRİŞ YAPAMAYACAK.       │
                    │                                                             │
                    │  Kurulum: docs/MAIL-KURULUMU.md                             │
                    │  Şablon denemesi için: --spring.profiles.active=dev,mailpit │
                    └─────────────────────────────────────────────────────────────┘""");
            return;
        }

        if (!(mailSender instanceof JavaMailSenderImpl sender)) {
            return;   // testlerdeki sahte gönderici gibi durumlar
        }

        try {
            sender.testConnection();
            log.info("Posta sunucusu bağlantısı doğrulandı — {} ({})", host, username);
            warnIfFromMismatch();
        } catch (Exception ex) {
            log.error("""

                    ┌─────────────────────────────────────────────────────────────┐
                    │  POSTA SUNUCUSUNA BAĞLANILAMADI                             │
                    ├─────────────────────────────────────────────────────────────┤
                    │  Sunucu : {}
                    │  Kullanıcı: {}
                    │  Sebep  : {}
                    │                                                             │
                    │  Gmail kullanıyorsan: normal hesap şifresi ÇALIŞMAZ.        │
                    │  2 adımlı doğrulamayı açıp 16 haneli bir Uygulama Şifresi   │
                    │  üretmen gerekir. Ayrıntı: docs/MAIL-KURULUMU.md            │
                    │                                                             │
                    │  Doğrulama postaları gönderilemeyecek; yeni kayıtlar        │
                    │  giriş yapamaz. Uygulamanın geri kalanı çalışmaya devam     │
                    │  ediyor.                                                    │
                    └─────────────────────────────────────────────────────────────┘""",
                    host, username, ex.getMessage());
        }
    }
}
