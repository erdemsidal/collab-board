package com.collabboard.label;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LabelRepository extends JpaRepository<Label, Long> {

    /** Panonun etiketleri, oluşturulma sırasıyla (kartın üstündeki sıra da bu). */
    List<Label> findByBoardIdOrderByIdAsc(Long boardId);

    /** Etiket bu panoda mı? Başka panonun etiketi "yok" sayılır (404). */
    Optional<Label> findByIdAndBoardId(Long id, Long boardId);

    long countByBoardId(Long boardId);

    /**
     * Panoda bu adla (büyük/küçük harf farkı gözetmeden) başka bir etiket var mı?
     * İki taraf da veritabanında küçültülür; benzersiz indeks de lower(name) üzerinde.
     */
    @Query("""
            SELECT COUNT(l) > 0 FROM Label l
            WHERE l.boardId = :boardId AND lower(l.name) = lower(:name) AND l.id <> :exceptId
            """)
    boolean nameTaken(@Param("boardId") Long boardId, @Param("name") String name, @Param("exceptId") Long exceptId);

    /**
     * Panonun satırını kilitler: etiket takımındaki değişiklikler (ekleme, ad,
     * renk, silme) sırayla uygulanır. Böylece "en fazla 30 etiket" ve "aynı ad
     * olmaz" kontrolleri aynı anda gelen iki istekte de doğru kalır.
     *
     * FOR NO KEY UPDATE: kolon ekleme gibi panoya bağlı satır yazan işlemler
     * (yabancı anahtar için KEY SHARE alır) bu kilide takılmaz.
     */
    @Query(value = "SELECT id FROM boards WHERE id = :boardId FOR NO KEY UPDATE", nativeQuery = true)
    Optional<Long> lockBoard(@Param("boardId") Long boardId);

    /**
     * Etiketi karta takar. Zaten takılıysa hiçbir şey yapmaz: aynı anda iki kişi
     * aynı etiketi taksa da tek satır olur. 1 = takıldı, 0 = zaten takılıydı.
     */
    @Modifying
    @Query(value = "INSERT INTO card_labels (card_id, label_id) VALUES (:cardId, :labelId) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    int attach(@Param("cardId") Long cardId, @Param("labelId") Long labelId);

    /** Etiketi karttan çıkarır. 1 = çıkarıldı, 0 = zaten yoktu. */
    @Modifying
    @Query(value = "DELETE FROM card_labels WHERE card_id = :cardId AND label_id = :labelId", nativeQuery = true)
    int detach(@Param("cardId") Long cardId, @Param("labelId") Long labelId);

    /** Etiketin takılı olduğu kart sayısı (silme kaydı için). */
    @Query(value = "SELECT COUNT(*) FROM card_labels WHERE label_id = :labelId", nativeQuery = true)
    long countCards(@Param("labelId") Long labelId);

    /**
     * Panodaki bütün kart-etiket bağları, TEK sorguda (panonun fotoğrafı için).
     * Kart başına sorgu atılsaydı yüz kartlık panoda yüz sorgu olurdu.
     * Takma adlar tırnaklı: Postgres tırnaksız adı küçültür, alanlar eşleşmezdi.
     */
    @Query(value = """
            SELECT cl.card_id AS "cardId", cl.label_id AS "labelId"
            FROM card_labels cl
            JOIN cards c ON c.id = cl.card_id
            JOIN board_columns col ON col.id = c.column_id
            WHERE col.board_id = :boardId
            ORDER BY cl.card_id, cl.label_id
            """, nativeQuery = true)
    List<CardLabelRow> cardLabels(@Param("boardId") Long boardId);

    interface CardLabelRow {
        Long getCardId();

        Long getLabelId();
    }
}
