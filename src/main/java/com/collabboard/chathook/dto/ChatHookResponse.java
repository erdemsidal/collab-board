package com.collabboard.chathook.dto;

import com.collabboard.chathook.ChatEvent;
import com.collabboard.chathook.ChatHook;
import com.collabboard.chathook.ChatProvider;
import com.collabboard.chathook.DisabledReason;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Bir kanal bağlantısının tarayıcıya giden hâli.
 *
 * ADRES YOK. Yalnızca son 4 karakteri (urlHint): yönetici hangi webhook'un bağlı
 * olduğunu tanısın, ama bu yanıtı gören biri (tarayıcı eklentisi, ekran paylaşımı,
 * önbellek) kanala yazamasın.
 */
public record ChatHookResponse(
        Long id,
        ChatProvider provider,
        String label,
        String urlHint,
        List<ChatEvent> events,
        String locale,
        boolean active,
        DisabledReason disabledReason,
        int failureCount,
        LocalDateTime lastSentAt,
        LocalDateTime createdAt
) {
    public static ChatHookResponse of(ChatHook h) {
        return new ChatHookResponse(h.getId(), h.getProvider(), h.getLabel(), h.getUrlHint(),
                List.copyOf(h.getEvents()), h.getLocale(), h.isActive(), h.getDisabledReason(),
                h.getFailureCount(), h.getLastSentAt(), h.getCreatedAt());
    }
}
