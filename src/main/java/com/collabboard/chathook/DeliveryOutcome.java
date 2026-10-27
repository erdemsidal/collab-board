package com.collabboard.chathook;

/**
 * Bir gönderimin sonucu ve bağlantıya etkisi.
 *
 * <pre>
 *  2xx              → OK            hata sayacı sıfırlanır
 *  404, 410         → GONE          bağlantı durur, yöneticilere haber
 *  403              → FORBIDDEN     bağlantı durur, yöneticilere haber
 *  429              → RATE_LIMITED  sayaç +1
 *  5xx              → SERVER_ERROR  sayaç +1
 *  zaman aşımı      → TIMEOUT       sayaç +1
 *  bağlantı hatası  → NETWORK       sayaç +1
 *  3xx              → REDIRECT      sayaç +1 (yönlendirme izlenmez)
 *  diğer 4xx        → REJECTED      sayaç +1 (ör. 400: mesajı beğenmedi)
 * </pre>
 *
 * Sayaç 5'e varınca bağlantı FAILING nedeniyle durur. Bu sürümde yeniden
 * deneme kuyruğu yok: kaçan mesaj kaçmış olur.
 */
public enum DeliveryOutcome {
    OK, GONE, FORBIDDEN, RATE_LIMITED, SERVER_ERROR, TIMEOUT, NETWORK, REDIRECT, REJECTED;

    public static DeliveryOutcome ofStatus(int status) {
        if (status >= 200 && status < 300) {
            return OK;
        }
        if (status == 404 || status == 410) {
            return GONE;
        }
        if (status == 403) {
            return FORBIDDEN;
        }
        if (status == 429) {
            return RATE_LIMITED;
        }
        if (status >= 500) {
            return SERVER_ERROR;
        }
        if (status >= 300 && status < 400) {
            return REDIRECT;
        }
        return REJECTED;
    }

    /** Bu sonuç bağlantıyı hemen durdurur mu? (Adres kalıcı olarak geçersiz.) */
    public DisabledReason permanentReason() {
        return switch (this) {
            case GONE -> DisabledReason.GONE;
            case FORBIDDEN -> DisabledReason.FORBIDDEN;
            default -> null;
        };
    }
}
