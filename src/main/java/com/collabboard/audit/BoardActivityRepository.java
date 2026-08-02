package com.collabboard.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
