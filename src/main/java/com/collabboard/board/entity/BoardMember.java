package com.collabboard.board.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Bir kullanıcının bir panodaki üyeliği ve rolü.
 *
 * Sadece id'leri tutuyoruz (board_id / user_id), entity referansı değil.
 * Neden? Bu kayıt yalnızca yetki kontrolü için okunur ("bu kullanıcı bu panoda
 * ne yapabilir?"); ilişkileri nesne olarak yüklemek gereksiz sorgu ve lazy
 * yükleme derdi getirirdi.
 */
@Entity
@Table(name = "board_members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoardMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "board_id", nullable = false)
    private Long boardId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * EnumType.STRING: veritabanına "OWNER" yazılır, 0/1/2 değil.
     * Sıra numarası yazsaydık, enum'a ortadan yeni bir değer eklendiğinde
     * mevcut satırların anlamı sessizce değişirdi.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BoardRole role;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
