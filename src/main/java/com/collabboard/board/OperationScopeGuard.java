package com.collabboard.board;

import com.collabboard.board.operation.AddCardOp;
import com.collabboard.board.operation.AddColumnOp;
import com.collabboard.board.operation.BoardOperation;
import com.collabboard.board.operation.DeleteCardOp;
import com.collabboard.board.operation.DeleteColumnOp;
import com.collabboard.board.operation.EditCardOp;
import com.collabboard.board.operation.MoveCardOp;
import com.collabboard.board.operation.MoveColumnOp;
import com.collabboard.board.operation.RenameColumnOp;
import com.collabboard.board.operation.SetWipLimitOp;
import com.collabboard.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bir canlı işlemin hedefi (kart ya da kolon) gerçekten adresteki panoya mı ait?
 *
 * Yetki kontrolü yalnızca adresteki panoya bakıyordu (/app/board/{id}/ops);
 * işlemin içindeki kart ya da kolon numarası başka bir panoya ait olabilirdi.
 * Kendi panosunda editör olan biri, numarasını tahmin ettiği başka bir panonun
 * kolonunu kartlarıyla birlikte silebilir, kartını kendi panosuna taşıyabilirdi.
 *
 * Hedef başka panodaysa "bulunamadı" denir, "yetkin yok" değil: ikincisi o
 * numaranın başka bir panoda var olduğunu ele verirdi.
 */
@Component
public class OperationScopeGuard {

    private final CardRepository cardRepository;
    private final ColumnRepository columnRepository;

    public OperationScopeGuard(CardRepository cardRepository, ColumnRepository columnRepository) {
        this.cardRepository = cardRepository;
        this.columnRepository = columnRepository;
    }

    /** sealed BoardOperation: yeni bir işlem tipi eklenip burada ele alınmazsa kod derlenmez. */
    @Transactional(readOnly = true)
    public void requireOnBoard(Long boardId, BoardOperation op) {
        switch (op) {
            case AddCardOp add -> requireColumn(boardId, add.columnId());
            case MoveCardOp move -> {
                requireCard(boardId, move.cardId());
                requireColumn(boardId, move.toColumnId());
            }
            case EditCardOp edit -> requireCard(boardId, edit.cardId());
            case DeleteCardOp del -> requireCard(boardId, del.cardId());
            case MoveColumnOp moveCol -> requireColumn(boardId, moveCol.columnId());
            case SetWipLimitOp wip -> requireColumn(boardId, wip.columnId());
            case RenameColumnOp rename -> requireColumn(boardId, rename.columnId());
            case DeleteColumnOp delCol -> requireColumn(boardId, delCol.columnId());
            case AddColumnOp addCol -> {
                if (!boardId.equals(addCol.boardId())) {
                    throw new ResourceNotFoundException("error.notFound.board", addCol.boardId());
                }
            }
        }
    }

    private void requireCard(Long boardId, Long cardId) {
        boolean onBoard = cardId != null && cardRepository.findBoardIdByCardId(cardId)
                .map(boardId::equals).orElse(false);
        if (!onBoard) {
            throw new ResourceNotFoundException("error.notFound.card", cardId);
        }
    }

    private void requireColumn(Long boardId, Long columnId) {
        boolean onBoard = columnId != null && columnRepository.findBoardIdByColumnId(columnId)
                .map(boardId::equals).orElse(false);
        if (!onBoard) {
            throw new ResourceNotFoundException("error.notFound.column", columnId);
        }
    }
}
