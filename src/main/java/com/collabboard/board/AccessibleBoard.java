package com.collabboard.board;

import com.collabboard.board.entity.Board;
import com.collabboard.board.entity.BoardRole;

/**
 * Kullanıcının erişebildiği bir pano ve oradaki ETKİN rolü.
 *
 * "Panolarım" listesi ile ana ekran özeti aynı kaynaktan beslenir
 * (BoardService.accessibleBoards); bu kayıt o kaynağın tek satırıdır.
 */
public record AccessibleBoard(Board board, BoardRole role) {
}
