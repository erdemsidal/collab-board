package com.collabboard.board;

import com.collabboard.board.dto.BoardResponse;
import com.collabboard.board.dto.BoardSummaryResponse;
import com.collabboard.board.dto.CreateBoardRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Pano REST uçları — "fotoğraf" kanalı (tam state al/oluştur).
 * Canlı senkron ayrı gelir (WebSocket).
 *
 * @AuthenticationPrincipal: JWT filtresi tarafından doğrulanmış kullanıcıyı verir;
 * getUsername() e-postadır. Yetki kontrolleri bu kimlikle yapılır — istemcinin
 * gönderdiği hiçbir "ben şuyum" bilgisine güvenmiyoruz.
 */
@RestController
@RequestMapping("/api/boards")
public class BoardController {

    private final BoardService boardService;

    public BoardController(BoardService boardService) {
        this.boardService = boardService;
    }

    /** Yeni pano oluştur — oluşturan kişi otomatik OWNER olur. */
    @PostMapping
    public ResponseEntity<BoardResponse> createBoard(@Valid @RequestBody CreateBoardRequest request,
                                                     @AuthenticationPrincipal UserDetails user) {
        BoardResponse board = boardService.createBoard(request.name(), user.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(board);
    }

    /** Kullanıcının üye olduğu panolar (kendi rolüyle birlikte). */
    @GetMapping
    public ResponseEntity<List<BoardSummaryResponse>> myBoards(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(boardService.myBoards(user.getUsername()));
    }

    /** Panonun tam hâli. Üye olmayan 403 alır. */
    @GetMapping("/{id}")
    public ResponseEntity<BoardResponse> getBoard(@PathVariable Long id,
                                                  @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(boardService.getBoard(id, user.getUsername()));
    }
}
