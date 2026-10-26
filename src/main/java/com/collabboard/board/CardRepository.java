package com.collabboard.board;

import org.springframework.data.jpa.repository.JpaRepository;

import com.collabboard.board.entity.Card;

public interface CardRepository extends JpaRepository<Card, Long> {

    /**
     * Bir panodaki toplam kart sayısı (pano listesindeki "N kart" bilgisi).
     * Kart → kolon → pano zinciri üzerinden sayar; ara listeyi belleğe çekmez.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT COUNT(c) FROM Card c WHERE c.column.board.id = :boardId")
    long countByBoardId(@org.springframework.data.repository.query.Param("boardId") Long boardId);

    /**
     * Bir kolondaki kart sayısı — WIP limiti kontrolü için.
     *
     * Kolonun kart listesini belleğe çekip size() demek yerine COUNT sorgusu:
     * limit kontrolü her kart ekleme/taşımada çalışıyor, yüzlerce kartı boşuna
     * yüklemenin anlamı yok.
     */
    long countByColumnId(Long columnId);

    /** Kartın panosu: canlı işlemin hedefinin adresteki panoya ait olduğunu doğrulamak için. */
    @org.springframework.data.jpa.repository.Query(
            "SELECT c.column.board.id FROM Card c WHERE c.id = :cardId")
    java.util.Optional<Long> findBoardIdByCardId(
            @org.springframework.data.repository.query.Param("cardId") Long cardId);

    /**
     * Kişiye atanmış AÇIK kartlar, verilen panolarda — ana ekran özeti için.
     * Panoları çağıran verir; yetki burada değil, BoardService.accessibleBoards'ta.
     *
     * "Açık" = kart panonun son kolonunda DEĞİL. Akış ölçümü de (FlowMetricsService)
     * işi kart son kolona ulaştığında bitmiş sayar; özet aynı tanımı kullanmazsa
     * pano "bitti" derken ana ekran "sende bekliyor" derdi. Tek kolonlu panoda
     * akış yoktur: o kolon "bitti" anlamına gelmez, kartları açık sayılır.
     *
     * Kolon da aynı sorguda gelir (JOIN FETCH): kolon adı her satırda gösteriliyor,
     * kart başına ayrı kolon sorgusu atılmasın. Sıra: son tarihi en yakın olan önce,
     * tarihsizler sonda.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT c FROM Card c JOIN FETCH c.column col
            WHERE c.assigneeId = :userId
              AND col.board.id IN :boardIds
              AND (EXISTS (SELECT 1 FROM BoardColumn later
                           WHERE later.board = col.board AND later.position > col.position)
                   OR NOT EXISTS (SELECT 1 FROM BoardColumn other
                                  WHERE other.board = col.board AND other.id <> col.id))
            ORDER BY c.dueDate ASC NULLS LAST, c.id DESC
            """)
    java.util.List<Card> findOpenAssigned(
            @org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("boardIds") java.util.Collection<Long> boardIds);

    /**
     * Verilen kolonlardaki kartlar, kolonlarıyla birlikte — ana ekranın pano sağlığı
     * ve yaşlanan iş listesi için. Hangi kolonların "açık" olduğunu çağıran seçer
     * (son kolon kuralı, bkz. findOpenAssigned).
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT c FROM Card c JOIN FETCH c.column col WHERE col.id IN :columnIds")
    java.util.List<Card> findInColumns(
            @org.springframework.data.repository.query.Param("columnIds") java.util.Collection<Long> columnIds);

    /** Panoların kolon başına kart sayısı, tek sorguda (pano sağlığındaki doluluk). */
    @org.springframework.data.jpa.repository.Query("""
            SELECT c.column.id AS columnId, COUNT(c) AS cards
            FROM Card c WHERE c.column.board.id IN :boardIds GROUP BY c.column.id
            """)
    java.util.List<ColumnCount> countPerColumn(
            @org.springframework.data.repository.query.Param("boardIds") java.util.Collection<Long> boardIds);

    interface ColumnCount {
        Long getColumnId();

        long getCards();
    }

    /**
     * Kimliği verilen kart, yalnızca verilen panolardaysa — genel aramada anahtarla
     * bulma (STR-42, ADR 0021). Panoları çağıran verir, yetki aramadakiyle aynı.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT c FROM Card c JOIN FETCH c.column col WHERE c.id = :cardId AND col.board.id IN :boardIds")
    java.util.Optional<Card> findInBoards(
            @org.springframework.data.repository.query.Param("cardId") Long cardId,
            @org.springframework.data.repository.query.Param("boardIds") java.util.Collection<Long> boardIds);

    /**
     * Başlığında ya da açıklamasında aranan metin geçen kartlar, verilen panolarda —
     * genel arama (ADR 0016). Kaçışlama ve küçültme BoardRepository.search ile aynı.
     *
     * Sıra: başlığı aramayla başlayanlar, sonra başlığında geçenler, sonra yalnızca
     * açıklamasında geçenler; her grupta en yeni önce. Kolon aynı sorguda gelir.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT c FROM Card c JOIN FETCH c.column col
            WHERE col.board.id IN :boardIds
              AND (lower(c.title) LIKE lower(:contains) ESCAPE '!'
                   OR lower(c.description) LIKE lower(:contains) ESCAPE '!')
            ORDER BY CASE WHEN lower(c.title) LIKE lower(:prefix) ESCAPE '!' THEN 0
                          WHEN lower(c.title) LIKE lower(:contains) ESCAPE '!' THEN 1
                          ELSE 2 END,
                     c.id DESC
            """)
    java.util.List<Card> search(
            @org.springframework.data.repository.query.Param("boardIds") java.util.Collection<Long> boardIds,
            @org.springframework.data.repository.query.Param("contains") String contains,
            @org.springframework.data.repository.query.Param("prefix") String prefix,
            org.springframework.data.domain.Pageable page);
}
