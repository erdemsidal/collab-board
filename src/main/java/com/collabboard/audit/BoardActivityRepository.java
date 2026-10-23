package com.collabboard.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface BoardActivityRepository extends JpaRepository<BoardActivity, Long> {

    /** Bir panonun hareketleri, en yeni önce. */
    List<BoardActivity> findByBoardIdOrderByIdDesc(Long boardId, Pageable pageable);

    /**
     * Olayları oluş sırasına göre okur — durumu yeniden hesaplayan kod bunu kullanır.
     * id'ye göre sıralamak zaman damgasından güvenlidir: aynı milisaniyede iki olay
     * olabilir, ama id sırası her zaman gerçek uygulanma sırasıdır.
     */
    List<BoardActivity> findByBoardIdOrderByIdAsc(Long boardId);

    /** Belirli bir ana kadarki olaylar (zaman yolculuğu). */
    List<BoardActivity> findByBoardIdAndIdLessThanEqualOrderByIdAsc(Long boardId, Long upToId);

    /**
     * Birden çok panonun hareketleri, en yeni önce — ana ekran özeti için.
     * Panoları çağıran verir (erişebildikleri); sıra id'ye göre, aynı gerekçeyle.
     */
    List<BoardActivity> findByBoardIdInOrderByIdDesc(Collection<Long> boardIds, Pageable pageable);

    /** Verilen panolarda bir andan bu yana kaç hareket oldu ("son 7 gün"). */
    long countByBoardIdInAndCreatedAtGreaterThanEqual(Collection<Long> boardIds, LocalDateTime since);

    /**
     * Verilen panolarda gün gün hareket sayısı — ana ekrandaki 7 günlük grafik, tek sorguda.
     *
     * Gün veritabanında ayrılır ve veritabanı UTC tutar (hibernate.jdbc.time_zone):
     * gün sınırı UTC gece yarısıdır, sunucunun saat dilimi ne olursa olsun.
     * Gün metin olarak döner ("2026-10-17"): tarih türü okunurken JDBC sürücüsü
     * onu yeniden bir saat dilimine çevirip bir gün kaydırabilirdi.
     * Hareket olmayan günler satır olarak gelmez; sıfırları çağıran doldurur.
     */
    @Query("""
            SELECT cast(cast(a.createdAt as LocalDate) as String) AS day, COUNT(a) AS events
            FROM BoardActivity a
            WHERE a.boardId IN :boardIds AND cast(a.createdAt as LocalDate) BETWEEN :from AND :to
            GROUP BY cast(cast(a.createdAt as LocalDate) as String)
            """)
    List<DayCount> countPerDay(@Param("boardIds") Collection<Long> boardIds,
                               @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Bir günün hareket sayısı (UTC günü, "YYYY-AA-GG"). */
    interface DayCount {
        String getDay();

        long getEvents();
    }

    /** Panoların en son hareket anı — "en son ne olan pano önce" sıralaması için, tek sorguda. */
    @Query("""
            SELECT a.boardId AS boardId, MAX(a.createdAt) AS lastAt
            FROM BoardActivity a WHERE a.boardId IN :boardIds GROUP BY a.boardId
            """)
    List<LastActivity> lastActivityPerBoard(@Param("boardIds") Collection<Long> boardIds);

    interface LastActivity {
        Long getBoardId();

        LocalDateTime getLastAt();
    }

    /**
     * Verilen panoların belirli türdeki olayları, oluş sırasıyla — ana ekranın akış
     * özeti kart olaylarını (CardFlowReplay) tüm panolar için tek sorguda oynatır.
     */
    List<BoardActivity> findByBoardIdInAndTypeInOrderByIdAsc(Collection<Long> boardIds, Collection<String> types);
}
