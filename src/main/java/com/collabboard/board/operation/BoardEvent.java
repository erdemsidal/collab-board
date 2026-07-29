package com.collabboard.board.operation;

/**
 * Sunucunun panoya (aboneler) yayınladığı bir olay.
 * Yayınlanan tüm olay tiplerinin ortak üst tipi — böylece controller'daki switch
 * tek bir tip döndürüp yayınlayabiliyor.
 *
 * type(): olayın adı ("ADD_CARD", "MOVE_CARD", ...). Her olay record'unda zaten
 * bir `type` alanı var; record'un otomatik ürettiği erişim metodu bu arayüzü
 * kendiliğinden karşılar. Metriklerde etiket olarak kullanıyoruz (ör. hangi
 * operasyon tipi kaç kez işlendi).
 */
public interface BoardEvent {

    String type();
}
