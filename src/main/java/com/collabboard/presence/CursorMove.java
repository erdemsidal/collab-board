package com.collabboard.presence;

/**
 * Tarayıcının gönderdiği imleç konumu.
 *
 * x, y: kolonların kaydırılabilir alanına göre piksel — herkeste kolonlar aynı
 * sırada ve aynı genişlikte olduğu için aynı noktaya düşer.
 * cardId: o an sürüklenen kart (yoksa null) — karşı taraf hangi kartın
 * taşındığını görsün diye.
 * gone: imleç panodan çıktı (fare panoyu terk etti, sekme gizlendi).
 */
public record CursorMove(Double x, Double y, Long cardId, boolean gone) {

    /** Kolonlar ne kadar uzun olursa olsun bunun ötesi bozuk ya da kötü niyetli veridir. */
    private static final double LIMIT = 100_000;

    /** Konum kullanılabilir mi? Ayrılma mesajında konum gerekmez. */
    boolean valid() {
        return gone || (x != null && y != null && Double.isFinite(x) && Double.isFinite(y));
    }

    double clampedX() {
        return clamp(x);
    }

    double clampedY() {
        return clamp(y);
    }

    private static double clamp(Double value) {
        return value == null ? 0 : Math.max(0, Math.min(LIMIT, value));
    }
}
