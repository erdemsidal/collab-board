package com.collabboard.chathook;

import com.collabboard.common.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Sunucunun POST edebileceği adresler: izin listesi (SSRF koruması).
 *
 * NEDEN? Adresi kullanıcı yapıştırıyor ve isteği SUNUCU atıyor. Liste olmasa
 * bir pano yöneticisi sunucuya "http://169.254.169.254/..." (bulut sağlayıcısının
 * iç bilgi servisi), "http://localhost:6379" ya da şirket ağındaki bir adresi
 * çağırtabilirdi. Liste yalnızca üç uygulamanın gerçek webhook adreslerini kabul eder.
 *
 * Varsayılanlar kodda ve sıkı. app.integrations.allowed-urls.{slack,teams,discord}
 * ile değiştirilebilir; testler ve yerel deneme bunu yerel bir alıcıya izin
 * vermek için kullanır. Canlıda değiştirilmemeli.
 *
 * Adres kaydederken VE her gönderimden önce yeniden denetlenir: liste sonradan
 * daraltılırsa eski kayıtlar da ona uyar.
 */
@Component
public class WebhookUrlPolicy {

    public static final String DEFAULT_SLACK = "^https://hooks\\.slack\\.com/services/[A-Za-z0-9/_-]+$";
    public static final String DEFAULT_DISCORD = "^https://(discord|discordapp)\\.com/api/webhooks/\\d+/[\\w-]+$";
    /**
     * Teams "Workflows" adresleri iki biçimde gelir; ikisi de 443 portunu açıkça yazabilir:
     *   https://prod-12.westeurope.logic.azure.com:443/workflows/...
     *   https://<ortam>.environment.api.powerplatform.com:443/powerautomate/automations/direct/workflows/...
     * Ana bilgisayar kısmında yalnızca harf, rakam, nokta ve tire: "@", "#", "\" ile
     * başka bir sunucuya kaçmak (ayrıştırıcı farkı) mümkün olmasın.
     */
    public static final String DEFAULT_TEAMS =
            "^https://[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.(api\\.powerplatform\\.com|logic\\.azure\\.com)(:443)?/[^\\s#]*$";

    static final int MAX_LENGTH = 2048;

    private final Map<ChatProvider, Pattern> patterns = new EnumMap<>(ChatProvider.class);

    public WebhookUrlPolicy(@Value("${app.integrations.allowed-urls.slack:}") String slack,
                            @Value("${app.integrations.allowed-urls.teams:}") String teams,
                            @Value("${app.integrations.allowed-urls.discord:}") String discord) {
        patterns.put(ChatProvider.SLACK, Pattern.compile(orDefault(slack, DEFAULT_SLACK)));
        patterns.put(ChatProvider.TEAMS, Pattern.compile(orDefault(teams, DEFAULT_TEAMS)));
        patterns.put(ChatProvider.DISCORD, Pattern.compile(orDefault(discord, DEFAULT_DISCORD)));
    }

    /** Sıkı varsayılanlarla: canlının kuralları (birim testleri de bunu kullanır). */
    public static WebhookUrlPolicy defaults() {
        return new WebhookUrlPolicy("", "", "");
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    /** Adres bu uygulama için izinli mi? */
    public boolean allows(ChatProvider provider, String url) {
        return provider != null && url != null && structurallySafe(url) && patterns.get(provider).matcher(url).matches();
    }

    /**
     * İzinli değilse 400. Mesaj adresi tekrar etmez (loglarda gizli kalsın),
     * yalnızca hangi uygulamanın adresi beklendiğini söyler.
     */
    public URI require(ChatProvider provider, String url) {
        if (!allows(provider, url)) {
            throw new BadRequestException("error.chatHook.urlNotAllowed", provider.displayName());
        }
        return URI.create(url);
    }

    /**
     * Desenden bağımsız temel denetim: ayrıştırılabilir, mutlak, http(s), ana
     * bilgisayarı olan bir adres; kullanıcı bilgisi ("kişi@sunucu") ve parça
     * ("#...") yok, boşluk ve kontrol karakteri yok. Desen yanlışlıkla gevşek
     * yazılsa bile bu kapılar açık kalmaz.
     */
    private static boolean structurallySafe(String url) {
        if (url.isEmpty() || url.length() > MAX_LENGTH) {
            return false;
        }
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c <= 0x20 || c == 0x7f || c == '\\') {
                return false;
            }
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return uri.isAbsolute()
                    && (scheme.equals("https") || scheme.equals("http"))
                    && uri.getHost() != null
                    && uri.getRawUserInfo() == null
                    && uri.getRawFragment() == null;
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
