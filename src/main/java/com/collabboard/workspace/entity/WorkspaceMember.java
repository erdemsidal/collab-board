package com.collabboard.workspace.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Bir kullanıcının çalışma alanındaki üyeliği ve rolü.
 *
 * BoardMember ile aynı desen: sadece id'ler tutulur, çünkü bu kayıt yalnızca
 * yetki çözümlemek için okunur.
 */
@Entity
@Table(name = "workspace_members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspaceMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkspaceRole role;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
