package com.collabboard.realtime;

/**
 * Sunucu canlı bağlantıyı neden kapattı? (ADR 0025)
 *
 * STOMP'ta bir isteği reddetmenin tek yolu ERROR çerçevesidir ve kural gereği
 * ardından bağlantı kapanır. Arayüz bu kapanmayı her zaman "jetonun süresi
 * doldu" sanıyordu: yeniler ve yeniden bağlanır. Panodan çıkarılan biri için bu
 * sonsuz bir döngü olurdu: bağlan, reddedil, yenile, yine bağlan.
 *
 * Bu yüzden ERROR çerçevesine bir başlık ekliyoruz: {@value #HEADER}. Arayüz
 * değere göre karar verir.
 */
public enum CloseReason {

    /** Adres izin listesinde yok ya da çerçeve türü kullanılmıyor. Meşru arayüz bunu hiç görmez. */
    FORBIDDEN("forbidden"),

    /** Oturum yok, jeton geçersiz ya da iptal edilmiş: jetonu yenileyip yeniden bağlan. */
    UNAUTHENTICATED("unauthenticated"),

    /** Panoya erişim yok ya da az önce kaldırıldı: yeniden bağlanma, panodan çık. */
    BOARD_ACCESS("board-access"),

    /** Şifre değişti ya da sıfırlandı; kişinin bütün bağlantıları kapatıldı. */
    SESSION_REVOKED("session-revoked"),

    /** Bağlantının dayandığı erişim jetonunun süresi doldu ve yenilenmedi. */
    TOKEN_EXPIRED("token-expired");

    /** ERROR çerçevesindeki başlığın adı. */
    public static final String HEADER = "x-reason";

    private final String code;

    CloseReason(String code) {
        this.code = code;
    }

    /** Başlıkta giden değer; arayüz (collab-api.js) bunu karşılaştırır. */
    public String code() {
        return code;
    }
}
