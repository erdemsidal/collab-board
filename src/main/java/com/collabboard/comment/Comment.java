package com.collabboard.comment;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Bir karta yazılmış yorum.
 *
 * Kart ve yazar ilişki değil düz id: yorumlar toplu okunur ve yazarların adları
 * tek sorguda çekilir (CommentService); ilişki kurulsaydı yorum başına ayrı
 * kullanıcı sorgusu doğardı (Card.assigneeId ile aynı gerekçe).
 *
 * Kartın sürümüne (Card.version) dokunmaz: yorum yazmak kartı değiştirmek
 * değildir, aynı anda kartı taşıyan birinin işlemini bayat saymamalı (ADR 0019).
 */
@Entity
@Table(name = "comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_id", nullable = false)
    private Long cardId;

    /** Yazan; hesabını silerse null olur, yorum kalır. */
    @Column(name = "author_id")
    private Long authorId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    /**
     * Anılan kişiler. Metindeki "@Ad Soyad" yalnızca görünüş; kimin anıldığını
     * bu küme söyler (aynı adlı iki kişi olabilir).
     */
    @ElementCollection
    @CollectionTable(name = "comment_mentions", joinColumns = @JoinColumn(name = "comment_id"))
    @Column(name = "user_id")
    @Builder.Default
    private Set<Long> mentionIds = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Son düzenleme anı; hiç düzenlenmediyse null. */
    @Column(name = "edited_at")
    private LocalDateTime editedAt;
}
