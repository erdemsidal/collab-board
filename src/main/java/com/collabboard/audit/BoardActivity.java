package com.collabboard.audit;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Pano geçmişindeki bir kayıt: kim, ne zaman, ne yaptı.
 *
 * DEĞİŞMEZ (immutable) kayıt: yazıldıktan sonra güncellenmez. Bu yüzden
 * Auditable'ı (createdAt + updatedAt) miras almıyoruz — "güncellenme tarihi"
 * bir geçmiş kaydı için anlamsızdır.
 */
@Entity
@Table(name = "board_activities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoardActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "board_id", nullable = false)
    private Long boardId;

    /** İşlemi yapan kullanıcı (silinirse null olur, kayıt yaşamaya devam eder). */
    @Column(name = "user_id")
    private Long userId;

    /** İsim kopyalanarak saklanır — kullanıcı sonradan değişse bile geçmiş bozulmaz. */
    @Column(name = "actor_name", nullable = false, length = 200)
    private String actorName;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
