package com.collabboard.search;

import com.collabboard.search.dto.SearchResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Genel arama. Kimlik ister (SecurityConfig: açıkça izin verilmeyen her uç); hız
 * sınırı yok, çünkü sınır kimliksiz uçlar için (RateLimitFilter). Kötüye kullanıma
 * karşı aramanın uzunluğu sınırlı.
 */
@RestController
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /** GET /api/search?q=plan — q kısaysa ya da yoksa boş sonuç, 100 karakterden uzunsa 400. */
    @GetMapping("/api/search")
    public ResponseEntity<SearchResponse> search(@RequestParam(required = false) String q,
                                                 @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(searchService.search(user.getUsername(), q));
    }
}
