package com.collabboard.security;

import com.collabboard.realtime.CloseReason;
import org.springframework.messaging.MessageDeliveryException;

/**
 * Canlı bağlantıda (STOMP) reddedilen bir çerçeve.
 *
 * Süzgeçten (interceptor) fırlatılınca Spring onu bir ERROR çerçevesine çevirir
 * ve bağlantıyı kapatır. Mesaj kullanıcının dilindedir; neden kodu
 * {@link StompErrorHandler} tarafından "x-reason" başlığına yazılır.
 *
 * MessageDeliveryException'dan türemesi önemli: Spring, mesajlaşma istisnalarını
 * sarmalamadan olduğu gibi iletir; böylece hata işleyicisi nedeni bulabilir.
 */
public class StompRejection extends MessageDeliveryException {

    private final CloseReason reason;

    public StompRejection(CloseReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public CloseReason reason() {
        return reason;
    }
}
