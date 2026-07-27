package com.collabboard.board;

import com.collabboard.board.dto.BoardResponse;
import com.collabboard.board.dto.CreateBoardRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pano REST uçları — "fotoğraf" kanalı (tam state al/oluştur).
 * Canlı senkron ayrı gelecek (WebSocket, adım 3-4).
 */
@RestController
@RequestMapping("/api/boards")
public class BoardController {

    private final BoardService boardService;

    public BoardController(BoardService boardService) {
        this.boardService = boardService;
    }

    /**
     * Yeni pano oluştur.
     * @Valid  → CreateBoardRequest'teki @NotBlank/@Size kurallarını tetikler;
     *           ihlal olursa Spring 400 döner (GlobalExceptionHandler yakalar).
     * @RequestBody → gelen JSON gövdesini DTO'ya bağlar.
     * 201 Created → "yeni bir kaynak yarattım" için doğru HTTP durumu.
     */
    @PostMapping
    public ResponseEntity<BoardResponse> createBoard(@Valid @RequestBody CreateBoardRequest request) {
        BoardResponse board = boardService.createBoard(request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(board);
    }

    /**
     * Panonun tam hâlini getir.
     * @PathVariable → URL'deki {id}'yi Long parametreye bağlar.
     * 200 OK; pano yoksa servis 404 fırlatır.
     */
    @GetMapping("/{id}")
    public ResponseEntity<BoardResponse> getBoard(@PathVariable Long id) {
        return ResponseEntity.ok(boardService.getBoard(id));
    }
}
