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
}
