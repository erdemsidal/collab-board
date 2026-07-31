package com.collabboard.workspace.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Çalışma alanı (şirket/ekip) — panoların sahibi olan üst katman.
 */
@Entity
@Table(name = "workspaces")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Workspace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    /** URL ve teknik referanslar için kısa, benzersiz ad (ör. "acme-yazilim"). */
    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
