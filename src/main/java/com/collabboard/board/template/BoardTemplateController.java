package com.collabboard.board.template;

import com.collabboard.board.template.dto.BoardTemplateResponse;
import com.collabboard.user.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Pano şablonları: salt okunur liste. Pano açarken seçilen kimlik
 * POST /api/boards gövdesinde "templateId" olarak gelir.
 */
@RestController
public class BoardTemplateController {

    private final BoardTemplateService templateService;
    private final UserService userService;

    public BoardTemplateController(BoardTemplateService templateService, UserService userService) {
        this.templateService = templateService;
        this.userService = userService;
    }

    /** GET /api/boards/templates: kişinin kayıtlı dilinde, panoyu da o dilde açacağımız için. */
    @GetMapping("/api/boards/templates")
    public ResponseEntity<List<BoardTemplateResponse>> list(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(templateService.list(userService.localeOf(user.getUsername())));
    }
}
