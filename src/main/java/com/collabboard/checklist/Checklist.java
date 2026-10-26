package com.collabboard.checklist;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Bir kartın kontrol listesi ("Yayın öncesi", "Kontrol listesi"...).
 *
 * Kart ilişki değil düz id (Comment.cardId ile aynı gerekçe). Maddeler ayrı
 * tabloda; liste ile birlikte değil, sırasıyla tek sorguda okunur.
 *
 * revision: listede her değişiklikte bir artar. Değişiklikler listenin satır
 * kilidi altında yapıldığı için (ChecklistRepository.lockOnCard) sayı, işlemlerin
 * veritabanına yazılma sırasıyla aynıdır. Canlı olay listenin tamamını bu sayıyla
 * taşır; arayüz elindekinden eski bir olayı atar.
 *
 * Kartın sürümüne (Card.version) dokunmaz: madde işaretlemek kartı değiştirmek
 * değildir, aynı anda kartı taşıyan birinin işlemini bayat saymamalı.
 */
@Entity
@Table(name = "checklists")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Checklist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_id", nullable = false)
    private Long cardId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false)
    private long revision;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
