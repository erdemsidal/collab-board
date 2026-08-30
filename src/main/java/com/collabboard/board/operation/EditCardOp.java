package com.collabboard.board.operation;

import java.time.LocalDate;

/**
 * "Şu kartın düzenlenebilir alanlarının yeni hâli bu."
 *
 * Tek tek "şu alanı değiştir" demek yerine alanların TAMAMI taşınır; gönderilmeyen
 * alan temizlenir. Bu güvenli, çünkü baseVersion araya başkasının girmediğini
 * garanti ediyor (ADR 0003) — istemci gördüğü hâlin üzerine yazıyor, kör bir
 * ezme yapmıyor. Alternatifi ("null = dokunma") alanı BOŞALTMAYI imkânsız kılardı.
 *
 * baseVersion: istemcinin EKRANINDA GÖRDÜĞÜ sürüm. Sunucudaki güncel sürümle
 * uyuşmuyorsa operasyon reddedilir.
 */
public record EditCardOp(
        Long cardId,
        String title,
        String description,
        Long assigneeId,
        LocalDate dueDate,
        Long baseVersion
) implements BoardOperation {
}
