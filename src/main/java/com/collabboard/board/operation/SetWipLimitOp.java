package com.collabboard.board.operation;

/**
 * "Şu kolonun WIP limitini şu yap." limit null → sınır kaldırılır.
 *
 * Kolon ayarı olmasına rağmen REST değil operasyon olarak taşınıyor: limit
 * değiştiğinde panodaki herkesin rozeti anında güncellenmeli, aksi halde
 * başkasının ekranında hâlâ eski sınır görünürken kart reddedilirdi.
 */
public record SetWipLimitOp(
        Long columnId,
        Integer limit
) implements BoardOperation {
}
