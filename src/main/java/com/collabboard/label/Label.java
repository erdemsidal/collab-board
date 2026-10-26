package com.collabboard.label;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Panonun etiketlerinden biri. Renk zorunlu, ad isteğe bağlı.
 *
 * Pano ilişki değil düz id (Comment.cardId ile aynı gerekçe): etiketler panonun
 * fotoğrafına tek sorguyla eklenir, panonun kendisini yüklemeye gerek yok.
 *
 * Kartlara takılması ayrı tabloda (card_labels); o tablo bir küme olduğu için
 * entity değil, LabelRepository'deki sorgularla yönetilir.
 */
@Entity
@Table(name = "board_labels")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Label {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "board_id", nullable = false)
    private Long boardId;

    /** Adsız etiket kartın üstünde yalnızca kısa bir renk çubuğu olarak görünür. */
    @Column(length = 40)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LabelColor color;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
