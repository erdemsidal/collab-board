package com.collabboard.board.operation;

/**
 * "Şu kolonu şu pozisyona taşı." (kolonları soldan sağa yeniden sırala)
 * İlk KART dışı operasyonumuz — bu yüzden CardOperation değil, BoardOperation.
 */
public record MoveColumnOp(
        Long columnId,
        int position
) implements BoardOperation {
}
