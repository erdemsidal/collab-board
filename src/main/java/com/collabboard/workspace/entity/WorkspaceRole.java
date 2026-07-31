package com.collabboard.workspace.entity;

/**
 * Bir kullanıcının çalışma alanındaki (şirket) rolü.
 *
 * Pano rolünden (BoardRole) FARKLI bir kavram: bu rol, şirketin TÜM panoları için
 * geçerli varsayılan erişimi belirler. Pano bazlı kayıt ise bunun üstüne yazan
 * bir istisnadır (bkz. BoardAccessService).
 */
public enum WorkspaceRole {

    /** Şirketin sahibi: her şey. */
    OWNER,

    /** Üye yönetimi yapabilir; şirketin tüm panolarında tam yetkilidir. */
    ADMIN,

    /** Şirketin panolarını düzenleyebilir, üye yönetemez. */
    MEMBER,

    /**
     * Şirketin panolarına ERİŞEMEZ; yalnızca ayrıca davet edildiği panoları görür.
     * Dış paydaş/müşteri gibi kişiler için: şirkete dahil ama her şeye değil.
     */
    GUEST;

    /** Şirkete üye ekleyip çıkarabilir mi? */
    public boolean canManageMembers() {
        return this == OWNER || this == ADMIN;
    }

    /** Şirkette yeni pano açabilir mi? */
    public boolean canCreateBoards() {
        return this != GUEST;
    }
}
