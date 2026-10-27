package com.collabboard.i18n;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Locale;

/**
 * Sunucunun kullanıcıya söylediği her şeyin dili (ADR 0020).
 *
 * Metinler messages_tr.properties ve messages_en.properties içinde (UTF-8).
 * Hata mesajları, alan doğrulamaları (@NotBlank(message = "{...}")), hız sınırı
 * ve postalar aynı kaynaktan okunur.
 */
@Configuration
public class I18nConfig {

    @Bean
    public MessageSource messageSource() {
        return newMessageSource();
    }

    /**
     * Uygulamanın kullandığı mesaj kaynağı; Spring'siz birim testleri de aynısını kurar.
     *
     * Her mesaj MessageFormat'tan geçer (alwaysUseMessageFormat): kesme işareti
     * dosyada her zaman '' yazılır. Yalnızca parametreli mesajlarda geçseydi
     * "CollabBoard'da" bir yerde tek, bir yerde çift tırnak isterdi.
     * Sistem diline düşülmez: sunucu İngilizce bir makinede çalışsa da dil
     * yalnızca istekten ya da kişinin tercihinden gelir.
     */
    public static ResourceBundleMessageSource newMessageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        source.setDefaultLocale(AppLocale.DEFAULT);
        source.setAlwaysUseMessageFormat(true);
        return source;
    }

    /**
     * REST isteğinin dili. Spring'in varsayılanı başlık yokken sunucunun kendi
     * dilini verirdi; burada kural AppLocale'deki: başlık yoksa Türkçe.
     * Alan doğrulama mesajları da bu dille üretilir.
     */
    @Bean(name = DispatcherServlet.LOCALE_RESOLVER_BEAN_NAME)
    public LocaleResolver localeResolver() {
        return new LocaleResolver() {
            @Override
            public Locale resolveLocale(HttpServletRequest request) {
                return AppLocale.of(request);
            }

            @Override
            public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
                throw new UnsupportedOperationException("Dil her istekte Accept-Language başlığından okunur");
            }
        };
    }
}
