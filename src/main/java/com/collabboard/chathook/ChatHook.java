package com.collabboard.chathook;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * Bir panonun bir sohbet kanalına bağlantısı (V22, ADR 0023).
 *
 * Adres (webhook URL) bir paroladır: bilen herkes o kanala yazar. Bu yüzden
 * şifreli saklanır ve hiçbir yanıtta dönmez; yalnızca son 4 karakteri (urlHint)
 * gösterilir. Şifreyi WebhookUrlCipher çözer, o da yalnızca gönderimden hemen önce.
 *
 * @DynamicUpdate: düzenlemede yalnızca değişen sütunlar yazılır. Aynı anda arka
 * planda biten bir gönderimin yazdığı hata sayacını, ad değiştiren yönetici ezmesin.
 */
@Entity
@DynamicUpdate
@Table(name = "board_chat_hooks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatHook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "board_id", nullable = false)
    private Long boardId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatProvider provider;

    @Column(name = "url_encrypted", nullable = false, columnDefinition = "TEXT")
    private String urlEncrypted;

    @Column(name = "url_hint", nullable = false, length = 8)
    private String urlHint;

    @Column(length = 60)
    private String label;

    @Convert(converter = ChatEventsConverter.class)
    @Column(nullable = false, length = 200)
    @Builder.Default
    private Set<ChatEvent> events = EnumSet.noneOf(ChatEvent.class);

    /** Mesajların dili ("tr", "en"); kanalın dili panoyu kullananlarınkinden farklı olabilir. */
    @Column(nullable = false, length = 5)
    private String locale;

    /** Üst üste başarısız gönderim sayısı; başarılı bir gönderim sıfırlar. */
    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    /** Boşsa bağlantı açık. */
    @Enumerated(EnumType.STRING)
    @Column(name = "disabled_reason", length = 30)
    private DisabledReason disabledReason;

    @Column(name = "last_sent_at")
    private LocalDateTime lastSentAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public boolean isActive() {
        return disabledReason == null;
    }

    public boolean listensTo(ChatEvent event) {
        return events.contains(event);
    }
}
