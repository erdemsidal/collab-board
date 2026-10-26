package com.collabboard.security;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Canlı bağlantıda istemcinin kullanabileceği adreslerin TAM listesi (ADR 0025).
 *
 * VARSAYILAN: REDDET. Eskiden yalnızca "/topic/board.42" biçimi denetleniyor,
 * geri kalan her adres serbest bırakılıyordu. Spring'in bellek içi broker'ı ise
 * içinde "*", "**", "?" ya da "{..}" olan adresi KALIP sayar: "/topic/**"a abone
 * olan biri bütün panoların yayınını, "/queue/**"a abone olan biri herkesin
 * bildirimlerini alıyordu. Artık yalnızca aşağıdaki adresler geçer; geri kalan
 * her şey (kalıplar, sayı olmayan pano numarası, bilinmeyen adres) reddedilir.
 *
 * Liste uygulamanın gerçekten kullandığı adreslerden çıkarıldı:
 *  - Abonelik (SUBSCRIBE): collab-api.js'teki client.subscribe çağrıları.
 *  - Gönderim (SEND): sunucudaki @MessageMapping uçları.
 * Yeni bir canlı adres eklenince buraya da eklenmeli; eklenmezse arayüz bağlanır
 * ama o adres reddedilir (StompAccessIntegrationTest bunu yakalar).
 */
final class StompDestinations {

    /** Pano numarası: 1'den başlar, başında sıfır yok, Long'a sığar. */
    private static final String BOARD_ID = "([1-9][0-9]{0,17})";

    /**
     * Panonun yayınları: olaylar, çevrimiçi listesi, imleçler.
     * Abone olmak için panonun üyesi olmak gerekir.
     */
    private static final Pattern BOARD_TOPIC =
            Pattern.compile("/topic/board\\." + BOARD_ID + "(?:/presence|/cursors)?");

    /**
     * Kişisel kuyruklar. Spring bunları bağlanan oturuma özel bir adrese kendisi
     * çevirir ("/queue/errors-user<oturum>"); başkasının kuyruğunu adlandırmanın
     * yolu yoktur. Yalnızca bu iki tam biçim geçer.
     */
    private static final Set<String> PERSONAL_QUEUES = Set.of(
            "/user/queue/errors",           // reddedilen operasyonlar (@SendToUser)
            "/user/queue/notifications");   // davetler ve bildirimler (NotificationService)

    /**
     * Sunucunun kendi uçları (@MessageMapping). Broker adreslerine ("/topic",
     * "/queue", "/user") doğrudan gönderim yasak: oraya yazılan mesaj, sunucudan
     * geçmeden bütün abonelere dağıtılırdı (ör. bir panoya sahte yorum).
     */
    private static final Pattern APP_DESTINATION = Pattern.compile(
            "/app/(?:board/" + BOARD_ID + "/(?:ops|cursor|presence/join)|session/renew)");

    private StompDestinations() {
    }

    /** Abone olunabilen bir pano adresiyse panonun numarası, değilse null. */
    static Long boardOfSubscription(String destination) {
        if (destination == null) {
            return null;
        }
        Matcher m = BOARD_TOPIC.matcher(destination);
        return m.matches() ? Long.valueOf(m.group(1)) : null;
    }

    /** Kişinin kendi kuyruklarından biri mi (yalnızca tam biçim). */
    static boolean isPersonalQueue(String destination) {
        return destination != null && PERSONAL_QUEUES.contains(destination);
    }

    /** İstemcinin SEND edebileceği bir sunucu ucu mu. */
    static boolean isAppDestination(String destination) {
        return destination != null && APP_DESTINATION.matcher(destination).matches();
    }

    /**
     * Loga yazmak için: adres istemciden gelir. STOMP başlıklarında kaçışlı satır
     * sonu olabilir; loga olduğu gibi yazılsa sahte log satırı üretilebilirdi.
     */
    static String forLog(String destination) {
        if (destination == null) {
            return "(yok)";
        }
        String clean = destination.replaceAll("\\p{Cntrl}", "?");
        return clean.length() > 120 ? clean.substring(0, 120) + "…" : clean;
    }
}
