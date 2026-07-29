package com.collabboard.board.operation;

/**
 * "Şu kartın başlığını değiştir."
 *
 * baseVersion: istemcinin EKRANINDA GÖRDÜĞÜ sürüm (ADR 0003). Sunucudaki güncel
 * sürümle uyuşmuyorsa operasyon reddedilir — böylece başkasının değişikliğini
 * habersizce ezmeyiz.
 */
public record EditCardOp(
        Long cardId,
        String title,
        Long baseVersion
) implements BoardOperation {
}
