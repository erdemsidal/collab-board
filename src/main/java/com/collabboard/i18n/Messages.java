package com.collabboard.i18n;

import com.collabboard.common.exception.LocalizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Mesaj anahtarını verilen dilde metne çevirir.
 *
 * Anahtar İngilizce dosyada yoksa Türkçesi kullanılır, o da yoksa anahtarın
 * kendisi döner ve loglanır. Eksik bir çeviri yüzünden kullanıcı 500 almasın;
 * iki dosyanın aynı anahtarları taşıdığını ayrıca bir test sabitliyor
 * (MessageBundlesTest).
 */
@Component
public class Messages {

    private static final Logger log = LoggerFactory.getLogger(Messages.class);

    private final MessageSource source;

    public Messages(MessageSource source) {
        this.source = source;
    }

    public String get(Locale locale, String code, Object... args) {
        try {
            return source.getMessage(code, args, locale);
        } catch (NoSuchMessageException e) {
            if (!AppLocale.DEFAULT.equals(locale)) {
                log.warn("Mesaj {} dilinde yok, varsayılan dil kullanılıyor: {}", locale, code);
                try {
                    return source.getMessage(code, args, AppLocale.DEFAULT);
                } catch (NoSuchMessageException ignored) {
                    // aşağıda loglanır
                }
            }
            log.error("Mesaj anahtarı bulunamadı: {}", code);
            return code;
        }
    }

    /** Hatanın taşıdığı anahtar ve parametrelerle. */
    public String get(Locale locale, LocalizedException ex) {
        return get(locale, ex.getCode(), ex.getArgs());
    }
}
