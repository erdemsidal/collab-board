package com.collabboard.board.entity;

import com.collabboard.common.audit.Auditable;
import jakarta.persistence.*;
import lombok.*;

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
     * Optimistic locking sürümü (ADR 0001). İstemci bir düzenleme gönderirken
     * gördüğü sürümü de bildirir; sunucudaki değer farklıysa araya başkası girmiş
     * demektir ve operasyon reddedilir (ADR 0003).
     */
    @Version
    private Long version;
}
