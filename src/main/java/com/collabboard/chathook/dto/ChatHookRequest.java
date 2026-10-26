package com.collabboard.chathook.dto;

import com.collabboard.chathook.ChatEvent;
import com.collabboard.chathook.ChatProvider;

import java.util.List;

/**
 * Kanal bağlantısı ekleme ve düzenleme isteği.
 *
 * Eklerken provider, url ve en az bir olay zorunlu; label ve locale isteğe bağlı
 * (locale verilmezse isteğin dili). Düzenlerken yalnızca gönderilen alanlar değişir:
 *  - url: yeni adres yapıştırıldıysa. Bağlantı sıfırdan başlar (sayaç sıfır, durmuşsa açılır).
 *  - provider: yalnızca yeni bir url ile birlikte değişebilir.
 *  - active: true → durdurulmuş bağlantıyı yeniden açar ("Yeniden aç").
 *
 * Doğrulama servis içinde: hata alan listesi değil, tek bir cümle olsun.
 */
public record ChatHookRequest(
        ChatProvider provider,
        String url,
        String label,
        List<ChatEvent> events,
        String locale,
        Boolean active
) {
}
