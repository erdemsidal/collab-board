package com.collabboard.board.operation;

/**
 * Sunucunun panoya (aboneler) yayınladığı bir olay.
 * Yayınlanan tüm olay tiplerinin ortak üst tipi — böylece controller'daki switch
 * tek bir tip döndürüp yayınlayabiliyor.
 *
 * type(): olayın adı — istemci ayrıştırmasında ve metrik etiketlerinde kullanılır.
 */
public interface BoardEvent {

    String type();
}
