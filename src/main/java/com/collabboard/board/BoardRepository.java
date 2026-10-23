package com.collabboard.board;

import com.collabboard.board.entity.Board;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoardRepository extends JpaRepository<Board, Long> {

    /** Bir çalışma alanının panoları (en yeni önce). */
    java.util.List<Board> findByWorkspaceIdOrderByIdDesc(Long workspaceId);

    /** Birden çok çalışma alanının panoları tek sorguda (en yeni önce) — "panolarım" için. */
    java.util.List<Board> findByWorkspaceIdInOrderByIdDesc(java.util.Collection<Long> workspaceIds);

    /**
     * Adı aranan metni içeren panolar, verilen panolar arasında — genel arama (ADR 0016).
     * Panoları çağıran verir; yetki burada değil, BoardService.accessibleBoards'ta.
     *
     * Kalıplar çağıranda kaçışlanmış gelir ('!' kaçış karakteri). İki taraf da
     * veritabanında küçültülür: Java ile Postgres'in "İ"yi farklı küçültmesi
     * eşleşmeyi bozmasın. Adı aramayla BAŞLAYANLAR önce.
     */
    @Query("""
            SELECT b FROM Board b
            WHERE b.id IN :boardIds AND lower(b.name) LIKE lower(:contains) ESCAPE '!'
            ORDER BY CASE WHEN lower(b.name) LIKE lower(:prefix) ESCAPE '!' THEN 0 ELSE 1 END,
                     lower(b.name), b.id DESC
            """)
    java.util.List<Board> search(@Param("boardIds") java.util.Collection<Long> boardIds,
                                 @Param("contains") String contains, @Param("prefix") String prefix,
                                 Pageable page);
}
