package com.collabboard.search;

import com.collabboard.board.AccessibleBoard;
import com.collabboard.board.BoardRepository;
import com.collabboard.board.BoardService;
import com.collabboard.board.CardKey;
import com.collabboard.board.CardRepository;
import com.collabboard.board.entity.Board;
import com.collabboard.board.entity.Card;
import com.collabboard.common.exception.BadRequestException;
import com.collabboard.search.dto.SearchResponse;
import com.collabboard.user.UserService;
import com.collabboard.workspace.WorkspaceRepository;
import com.collabboard.workspace.entity.Workspace;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Genel arama: panolar (ada göre) ve kartlar (başlık ya da açıklamaya göre, ya da
 * "STR-42" gibi anahtarla, ADR 0021).
 *
 * Yetki burada YENİDEN yazılmaz (ADR 0015 ile aynı ilke): aranan küme
 * BoardService.accessibleBoards'un döndürdüğü panolardır. Erişemediğim bir panoda
 * aynı kelimeyi taşıyan kart sonuçlara giremez — sorgu o panoyu hiç görmez.
 */
@Service
@Transactional(readOnly = true)
public class SearchService {

    /** Bundan kısa arama boş döner: tek harf her kartla eşleşir, sonuç işe yaramaz. */
    static final int MIN_LENGTH = 2;
    /** Bundan uzun arama 400 alır: anlamlı bir başlık bu kadar uzun olmaz. */
    static final int MAX_LENGTH = 100;
    /** Her listenin en fazla uzunluğu. */
    static final int LIMIT = 8;

    /** LIKE kaçış karakteri. Ters bölü yerine '!': Java, JPQL ve SQL katmanlarında kaçış istemez. */
    private static final char ESCAPE = '!';

    private final UserService userService;
    private final BoardService boardService;
    private final BoardRepository boardRepository;
    private final CardRepository cardRepository;
    private final WorkspaceRepository workspaceRepository;

    public SearchService(UserService userService, BoardService boardService, BoardRepository boardRepository,
                         CardRepository cardRepository, WorkspaceRepository workspaceRepository) {
        this.userService = userService;
        this.boardService = boardService;
        this.boardRepository = boardRepository;
        this.cardRepository = cardRepository;
        this.workspaceRepository = workspaceRepository;
    }

    public SearchResponse search(String actorEmail, String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.length() > MAX_LENGTH) {
            throw new BadRequestException("error.search.tooLong", String.valueOf(MAX_LENGTH));
        }
        // "STR-42", "str-42", "#42", "42": kartın anahtarı (ADR 0021). Tek rakam da
        // olabilir ("7"); anahtar tam eşleşme olduğu için kısalık sınırı ona uygulanmaz.
        Optional<Long> keyId = CardKey.parse(query);
        boolean text = query.length() >= MIN_LENGTH;
        if (!text && keyId.isEmpty()) {
            return SearchResponse.EMPTY;
        }

        Long userId = userService.getUserByEmail(actorEmail).getId();
        Map<Long, AccessibleBoard> boards = boardService.accessibleBoards(userId).stream()
                .collect(Collectors.toMap(a -> a.board().getId(), Function.identity()));
        if (boards.isEmpty()) {
            return SearchResponse.EMPTY;
        }

        // Anahtarla bulunan kart listenin başında. Yalnızca erişilebilen panolarda
        // aranır: başkasının STR-42'si "yok" sayılır, başlığı sızmaz.
        List<Card> cards = new ArrayList<>();
        keyId.flatMap(id -> cardRepository.findInBoards(id, boards.keySet())).ifPresent(cards::add);

        List<Board> matchedBoards = List.of();
        if (text) {
            // Kullanıcının yazdığı % ve _ joker değil, düz karakterdir: "%50" yalnızca "%50"yi bulur.
            String literal = escapeLike(query);
            String contains = "%" + literal + "%";
            String prefix = literal + "%";
            PageRequest top = PageRequest.of(0, LIMIT);

            matchedBoards = boardRepository.search(boards.keySet(), contains, prefix, top);
            Set<Long> already = cards.stream().map(Card::getId).collect(Collectors.toSet());
            cardRepository.search(boards.keySet(), contains, prefix, top).stream()
                    .filter(c -> !already.contains(c.getId()))
                    .limit(LIMIT - cards.size())
                    .forEach(cards::add);
        }

        Map<Long, String> workspaceNames = workspaceRepository
                .findAllById(matchedBoards.stream().map(Board::getWorkspaceId).distinct().toList()).stream()
                .collect(Collectors.toMap(Workspace::getId, Workspace::getName));

        return new SearchResponse(
                matchedBoards.stream()
                        .map(b -> new SearchResponse.BoardHit(b.getId(), b.getName(),
                                workspaceNames.get(b.getWorkspaceId()), boards.get(b.getId()).role()))
                        .toList(),
                cards.stream()
                        .map(c -> {
                            // Kolonun panosu yüklenmez: id'si kolon satırında, adı elimizdeki listede.
                            Long boardId = c.getColumn().getBoard().getId();
                            return new SearchResponse.CardHit(c.getId(), CardKey.of(c.getId()), c.getTitle(),
                                    boardId, boards.get(boardId).board().getName(), c.getColumn().getName());
                        })
                        .toList());
    }

    /** LIKE'ın özel karakterlerini düz karaktere çevirir (önce kaçış karakterinin kendisi). */
    static String escapeLike(String text) {
        return text.replace(String.valueOf(ESCAPE), "" + ESCAPE + ESCAPE)
                .replace("%", ESCAPE + "%")
                .replace("_", ESCAPE + "_");
    }
}
