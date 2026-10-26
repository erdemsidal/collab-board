package com.collabboard.chathook;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Kanal bağlantıları.
 *
 * Gönderim sonuçları tek satırlık güncellemelerle yazılır, nesne okunup geri
 * kaydedilmez: aynı bağlantıya iki mesaj aynı anda gidebilir (iki kişi aynı anda
 * kart taşıdı) ve "oku, bir artır, yaz" ikisinden birinin sayımını yutardı.
 *
 * Güncellemeler kendi işlemlerini açar (@Transactional): gönderim bilerek bir
 * işlemin dışında çalışıyor (ChatDelivery), HTTP beklerken bağlantı tutulmasın.
 */
public interface ChatHookRepository extends JpaRepository<ChatHook, Long> {

    List<ChatHook> findByBoardIdOrderByIdAsc(Long boardId);

    /** Bir olay olduğunda: panonun açık bağlantıları. */
    List<ChatHook> findByBoardIdAndDisabledReasonIsNull(Long boardId);

    Optional<ChatHook> findByIdAndBoardId(Long id, Long boardId);

    long countByBoardId(Long boardId);

    /** Başarılı gönderim: sayaç sıfır, son gönderim şimdi. */
    @Modifying
    @Transactional
    @Query("UPDATE ChatHook h SET h.failureCount = 0, h.lastSentAt = :now WHERE h.id = :id")
    int markSent(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** Geçici hata: sayaç bir artar. Yeni değer ayrıca okunur (failureCount). */
    @Modifying
    @Transactional
    @Query("UPDATE ChatHook h SET h.failureCount = h.failureCount + 1 WHERE h.id = :id")
    int countFailure(@Param("id") Long id);

    @Query("SELECT h.failureCount FROM ChatHook h WHERE h.id = :id")
    Optional<Integer> failureCount(@Param("id") Long id);

    /**
     * Bağlantıyı durdurur; yalnızca hâlâ açıksa. 1 dönerse durduran bu çağrıdır:
     * yöneticilere haberi o verir, aynı anda başarısız olan ikinci gönderim vermez.
     */
    @Modifying
    @Transactional
    @Query("UPDATE ChatHook h SET h.disabledReason = :reason WHERE h.id = :id AND h.disabledReason IS NULL")
    int disable(@Param("id") Long id, @Param("reason") DisabledReason reason);
}
