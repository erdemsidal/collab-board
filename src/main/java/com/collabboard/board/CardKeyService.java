package com.collabboard.board;

import com.collabboard.board.dto.CardKeyResponse;
import com.collabboard.board.entity.Board;
import com.collabboard.board.entity.Card;
import com.collabboard.common.exception.BadRequestException;
import com.collabboard.common.exception.ResourceNotFoundException;
import com.collabboard.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "STR-42" hangi panonun hangi kartı? (ADR 0021)
 *
 * Kartın panosuna erişimi olmayan, kart hiç yokmuş gibi 404 alır: 403 demek,
 * o numarada bir kartın var olduğunu ele verirdi ve anahtarlar sıralı olduğu
 * için bütün kart numaraları taranabilirdi. Kimin erişebildiğini
 * BoardAccessService söyler; kural burada yeniden yazılmaz.
 */
@Service
@Transactional(readOnly = true)
public class CardKeyService {

    private final CardRepository cardRepository;
    private final BoardAccessService accessService;
    private final UserService userService;

    public CardKeyService(CardRepository cardRepository, BoardAccessService accessService, UserService userService) {
        this.cardRepository = cardRepository;
        this.accessService = accessService;
        this.userService = userService;
    }

    public CardKeyResponse resolve(String rawKey, String actorEmail) {
        long cardId = CardKey.parse(rawKey)
                .orElseThrow(() -> new BadRequestException("error.cardKey.invalid", CardKey.of(42)));
        Long userId = userService.getUserByEmail(actorEmail).getId();

        Card card = cardRepository.findById(cardId)
                .filter(c -> accessService.roleOf(c.getColumn().getBoard().getId(), userId).isPresent())
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound.card", rawKey));

        Board board = card.getColumn().getBoard();
        return new CardKeyResponse(CardKey.of(card.getId()), card.getId(), board.getId(), board.getName(),
                card.getColumn().getName(), card.getTitle(), CardKey.branchName(card.getId(), card.getTitle()));
    }
}
