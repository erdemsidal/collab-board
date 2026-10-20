package com.collabboard.board.operation;

/**
 * Panodaki istemcilere "şu listeyi yeniden çek" diyen içeriksiz olay.
 *
 * Neden içeriksiz? Pano kanalını (/topic/board.{id}) panonun TÜM üyeleri dinler,
 * izleyiciler dahil. Bekleyen davetlerin e-posta adresleri ise yalnızca
 * yöneticinin görmesi gereken bilgi. Olayın kendisi veri taşımaz; yetkisi olan
 * istemci yetki kontrollü REST ucundan güncel listeyi çeker.
 */
public record BoardSignal(String type) implements BoardEvent {

    /** Üye listesi değişti: biri katıldı, çıkarıldı ya da rolü değişti. */
    public static BoardSignal membersChanged() {
        return new BoardSignal("MEMBERS_CHANGED");
    }

    /** Bekleyen davetler değişti. Yalnızca yöneticinin arayüzü ilgilenir. */
    public static BoardSignal invitationsChanged() {
        return new BoardSignal("INVITATIONS_CHANGED");
    }
}
