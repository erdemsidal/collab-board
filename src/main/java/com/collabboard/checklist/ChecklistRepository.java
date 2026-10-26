package com.collabboard.checklist;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChecklistRepository extends JpaRepository<Checklist, Long> {

    /** Kartın listeleri, eklenme sırasıyla. */
    List<Checklist> findByCardIdOrderByIdAsc(Long cardId);

    long countByCardId(Long cardId);

    /**
     * Listeyi bu kartta arar ve satırını KİLİTLER (PESSIMISTIC_WRITE: SELECT ... FOR UPDATE).
     *
     * Listeye dokunan her işlem (madde ekleme, işaretleme, sıralama, silme, ad)
     * önce bunu çağırır; aynı listeye gelen ikinci istek ilki bitene kadar bekler.
     * Böylece iki kişi aynı anda sıralasa da sıra numaraları çakışmaz, "son madde
     * işaretlendi, liste tamamlandı" da tek bir kez görülür.
     *
     * Liste başka bir kartınsa boş döner (404): adres ile hedef aynı yerde olmalı.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Checklist c WHERE c.id = :id AND c.cardId = :cardId")
    Optional<Checklist> lockOnCard(@Param("id") Long id, @Param("cardId") Long cardId);

    /**
     * Kartın satırını kilitler: yeni liste eklenirken "kartta en fazla 10 liste"
     * kontrolü aynı anda gelen iki istekte de doğru kalsın.
     *
     * FOR NO KEY UPDATE: karta yorum yazmak gibi karta bağlı satır ekleyen
     * işlemler (yabancı anahtar için KEY SHARE alır) bu kilide takılmaz.
     */
    @Query(value = "SELECT id FROM cards WHERE id = :cardId FOR NO KEY UPDATE", nativeQuery = true)
    Optional<Long> lockCard(@Param("cardId") Long cardId);

    /**
     * Panodaki bütün listelerin özeti (madde sayısı, yapılanlar, revision), TEK
     * gruplu sorguda: panonun fotoğrafında kartın üstündeki "3/5" için. Maddelerin
     * kendisi gelmez; kart penceresi açılınca ayrıca istenir.
     */
    @Query(value = """
            SELECT cl.id AS "id", cl.card_id AS "cardId", cl.revision AS "revision",
                   COUNT(i.id) AS "total",
                   COUNT(i.id) FILTER (WHERE i.done) AS "done"
            FROM checklists cl
            JOIN cards c ON c.id = cl.card_id
            JOIN board_columns col ON col.id = c.column_id
            LEFT JOIN checklist_items i ON i.checklist_id = cl.id
            WHERE col.board_id = :boardId
            GROUP BY cl.id, cl.card_id, cl.revision
            ORDER BY cl.card_id, cl.id
            """, nativeQuery = true)
    List<SummaryRow> summaries(@Param("boardId") Long boardId);

    interface SummaryRow {
        Long getId();

        Long getCardId();

        Long getRevision();

        Long getTotal();

        Long getDone();
    }
}
