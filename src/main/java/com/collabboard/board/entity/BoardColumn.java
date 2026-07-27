package com.collabboard.board.entity;

import com.collabboard.common.audit.Auditable;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Panodaki bir kolon (todo / doing / done gibi).
 *
 * NEDEN "BoardColumn", sadece "Column" değil?
 * Çünkü "Column" hem JPA'nın @Column anotasyonuyla, hem de SQL'in ayrılmış
 * (reserved) 'column' kelimesiyle çakışır. BoardColumn hem net hem güvenli.
 */
@Entity
@Table(name = "board_columns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoardColumn extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Bu kolon hangi panoya ait?
     * - Bu taraf ilişkinin SAHİBİ → foreign key burada (board_id sütunu).
     * - fetch = LAZY: kolonu çekince board otomatik yüklenmesin (performans);
     *   gerçekten lazım olunca yüklenir.
     * - optional = false: her kolonun bir panosu OLMAK ZORUNDA.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "board_id", nullable = false)
    private Board board;

    @Column(nullable = false, length = 100)
    private String name;

    /**
     * Kolonun pano içindeki sırası (0,1,2...). Kolonları soldan sağa dizmek için.
     */
    @Column(nullable = false)
    private int position;

    /**
     * Bu kolondaki kartlar, position'a göre sıralı.
     * Sahip taraf Card.column; foreign key (column_id) cards tablosunda.
     */
    @OneToMany(mappedBy = "column", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    @Builder.Default
    private List<Card> cards = new ArrayList<>();

    public void addCard(Card card) {
        cards.add(card);
        card.setColumn(this);
    }
}
