package com.collabboard.chathook.dto;

import com.collabboard.chathook.DeliveryOutcome;

/**
 * Deneme mesajının sonucu. İsteğin kendisi başarılı (200); mesajın kanala
 * ulaşıp ulaşmadığı veridir. Arayüz outcome'u kendi dilinde anlatır.
 *
 * @param status kanalın döndüğü HTTP durumu; zaman aşımı ve bağlantı hatasında yok
 */
public record ChatHookTestResponse(
        boolean delivered,
        DeliveryOutcome outcome,
        Integer status
) {
}
