package com.collabboard.board.entity;

/**
 * Bir kullanıcının pano üzerindeki rolü.
 *
 * Yetkileri rolün KENDİSİNE sordurmak (canEdit() gibi), kontrolleri kod içine
 * dağılmış "if (role == OWNER || role == EDITOR)" satırlarından kurtarır:
 * yarın yeni bir rol eklenirse tek yerde güncellenir.
 */
public enum BoardRole {

    /** Her şey: içerik + üye yönetimi. Panoyu oluşturan kişi otomatik OWNER olur. */
    OWNER,

    /** İçeriği değiştirebilir (kart/kolon) ama üye yönetemez. */
    EDITOR,

    /** Sadece görüntüler: panoyu okur, presence'ta görünür, hiçbir şeyi değiştiremez. */
    VIEWER;

    /** Kart/kolon operasyonu yapabilir mi? */
    public boolean canEdit() {
        return this == OWNER || this == EDITOR;
    }

    /** Üye ekleyip çıkarabilir, rol değiştirebilir mi? */
    public boolean canManageMembers() {
        return this == OWNER;
    }
}
