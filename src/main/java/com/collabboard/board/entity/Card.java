package com.collabboard.board.entity;

import com.collabboard.common.audit.Auditable;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Bir kart. Kanban'daki en küçük birim; bir kolonun içinde yaşar.
 */
@Entity
@Table(name = "cards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Card extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Kart hangi kolonda? Sahip taraf burası → foreign key column_id cards tablosunda.
     * LAZY + optional=false: her kartın bir kolonu olmak zorunda, kolon peşinen yüklenmez.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "column_id", nullable = false)
    private BoardColumn column;

    @Column(nullable = false, length = 500)
    private String title;

    /**
     * Kartın kolon içindeki sırası (0,1,2...). MOVE_CARD operasyonu bunu değiştirir.
     */
    @Column(nullable = false)
    private int position;

    /**
     * Kartın uzun açıklaması. Başlık "ne", açıklama "nasıl/neden" taşır.
     * TEXT: uzunluk sınırı koymuyoruz, kart notu bir cümle de olabilir bir sayfa da.
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Kartı üstlenen kullanıcının id'si.
     *
     * NEDEN @ManyToOne User değil de düz Long? Kartlar toplu hâlde (pano açılışında
     * yüzlercesi) DTO'ya çevriliyor; ilişki kursaydık her kart için ayrı bir kullanıcı
     * sorgusu doğardı (N+1). İstemci üye listesini zaten ayrıca çekiyor, id'yi isme
     * orada eşliyor — tek sorgu, sıfır ek yük.
     */
    @Column(name = "assignee_id")
    private Long assigneeId;

    /** Son teslim tarihi. Saat bilgisi taşımaz; "hangi gün" yeterli. */
    @Column(name = "due_date")
    private LocalDate dueDate;

    /**
     * Optimistic locking sürümü (ADR 0001). İstemci bir düzenleme gönderirken
     * gördüğü sürümü de bildirir; sunucudaki değer farklıysa araya başkası girmiş
     * demektir ve operasyon reddedilir (ADR 0003).
     */
    @Version
    private Long version;
}
