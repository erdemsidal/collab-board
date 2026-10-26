package com.collabboard.security;

import com.collabboard.realtime.CloseReason;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

/**
 * ERROR çerçevesine "neden" başlığını ekler (ADR 0025).
 *
 * Spring'in varsayılanı ERROR'a yalnızca istisnanın metnini koyar; o da çoğu
 * zaman iç bir sarmalayıcının metnidir ("Failed to send message to
 * ExecutorSubscribableChannel..."). Bizim reddimizde (StompRejection) hem
 * kullanıcının dilindeki metni hem de arayüzün karar verdiği "x-reason"
 * başlığını yazıyoruz. Diğer hatalar olduğu gibi kalır.
 */
public class StompErrorHandler extends StompSubProtocolErrorHandler {

    @Override
    protected Message<byte[]> handleInternal(StompHeaderAccessor errorHeaderAccessor, byte[] errorPayload,
                                             @Nullable Throwable cause,
                                             @Nullable StompHeaderAccessor clientHeaderAccessor) {
        StompRejection rejection = rejectionIn(cause);
        if (rejection != null && errorHeaderAccessor.isMutable()) {
            errorHeaderAccessor.setMessage(rejection.getMessage());
            errorHeaderAccessor.setNativeHeader(CloseReason.HEADER, rejection.reason().code());
        }
        return super.handleInternal(errorHeaderAccessor, errorPayload, cause, clientHeaderAccessor);
    }

    private static StompRejection rejectionIn(@Nullable Throwable cause) {
        for (Throwable t = cause; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof StompRejection rejection) {
                return rejection;
            }
        }
        return null;
    }
}
