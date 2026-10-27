package com.collabboard.checklist;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Kontrol listesindeki bir madde: metin, yapıldı mı, sırası.
 *
 * position listede 0'dan boşluksuz gider. Veritabanı aynı listede iki maddenin
 * aynı sırada durmasına izin vermez (V21, işlem sonunda denetlenir).
 */
@Entity
@Table(name = "checklist_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChecklistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "checklist_id", nullable = false)
    private Long checklistId;

    @Column(nullable = false, length = 300)
    private String text;

    @Column(nullable = false)
    private boolean done;

    @Column(nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
