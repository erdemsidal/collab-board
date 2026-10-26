package com.collabboard.analytics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.regex.Pattern;

/**
 * Ziyaretçi analitiği: Umami (ADR 0024). Çerez kullanmaz, kişiyi tanımaz.
 *
 * Site kimliği (ANALYTICS_UMAMI_WEBSITE_ID) boşsa analitik kapalıdır: sayfalar
 * hiçbir betik yüklemez, Umami'ye tek istek gitmez. Kimlik Umami panelinde,
 * siteyi ekleyince verilen "Website ID"dir (bir UUID).
 *
 * İki değer de sayfaya yazılacağı için açılışta denetlenir: kimlik yalnızca
 * harf, rakam ve tire; betik adresi http(s). Uymayan değerde analitik kapalı
 * kalır ve logda nedeni yazar; yanlış yapıştırılmış bir değer sayfayı bozmasın.
 */
@Component
public class UmamiSettings {

    private static final Logger log = LoggerFactory.getLogger(UmamiSettings.class);

    private static final Pattern WEBSITE_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    private final String websiteId;
    private final String scriptUrl;

    public UmamiSettings(@Value("${app.analytics.umami.website-id:}") String websiteId,
                         @Value("${app.analytics.umami.script-url:https://cloud.umami.is/script.js}") String scriptUrl) {
        String id = websiteId == null ? "" : websiteId.trim();
        String url = scriptUrl == null ? "" : scriptUrl.trim();

        if (id.isEmpty()) {
            log.info("Ziyaretçi analitiği kapalı: ANALYTICS_UMAMI_WEBSITE_ID boş.");
            this.websiteId = null;
            this.scriptUrl = null;
        } else if (!WEBSITE_ID.matcher(id).matches()) {
            log.warn("Ziyaretçi analitiği kapalı: ANALYTICS_UMAMI_WEBSITE_ID geçersiz (yalnızca harf, rakam ve tire olmalı).");
            this.websiteId = null;
            this.scriptUrl = null;
        } else if (!isHttpUrl(url)) {
            log.warn("Ziyaretçi analitiği kapalı: ANALYTICS_UMAMI_SCRIPT_URL bir http(s) adresi değil.");
            this.websiteId = null;
            this.scriptUrl = null;
        } else {
            log.info("Ziyaretçi analitiği açık (Umami): {}", URI.create(url).getHost());
            this.websiteId = id;
            this.scriptUrl = url;
        }
    }

    private static boolean isHttpUrl(String url) {
        try {
            URI uri = URI.create(url);
            return ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean enabled() {
        return websiteId != null;
    }

    /** Kapalıyken null. */
    public String websiteId() {
        return websiteId;
    }

    /** Kapalıyken null. */
    public String scriptUrl() {
        return scriptUrl;
    }
}
