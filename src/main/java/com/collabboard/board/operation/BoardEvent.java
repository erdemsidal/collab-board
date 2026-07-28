package com.collabboard.board.operation;

/**
 * Sunucunun panoya (aboneler) yayınladığı bir olay (event).
 * İşaret (marker) arayüzü: yayınlanan tüm event tiplerinin ortak üst tipi.
 * Böylece controller'daki switch tek bir tip (BoardEvent) döndürüp yayınlayabilir.
 */
public interface BoardEvent {
}
