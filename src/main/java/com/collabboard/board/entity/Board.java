package com.collabboard.board.entity;

import com.collabboard.common.audit.Auditable;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Bir Kanban panosu. En üstteki kök (aggregate root):
 * Board → içinde Column'lar → onların içinde Card'lar.
 */
@Entity
@Table(name = "boards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Board extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    /**
     * Panonun ait olduğu çalışma alanı (şirket). Zorunlu: sahipsiz pano yoktur,
     * kişisel kullanım da bir çalışma alanıdır. Erişim yetkisi öncelikle buradan
     * türetilir (bkz. BoardAccessService).
     */
    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    /**
     * Panonun kolonları (todo / doing / done ...).
     * - mappedBy = "board": ilişkinin SAHİBİ karşı taraf (BoardColumn.board).
     * Yani foreign key (board_id) columns tablosunda tutulur, burada değil.
     * - cascade = ALL: pano kaydedilince/silinince kolonları da kapsanır.
     * - orphanRemoval: listeden çıkarılan kolon DB'den de silinir.
     * - @OrderBy: DB'den çekerken position'a göre sıralı gelsin.
     */
    @OneToMany(mappedBy = "board", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    @Builder.Default
    private List<BoardColumn> columns = new ArrayList<>();

    /**
     * İki yönlü ilişkide TUTARLILIK helper'ı.
     * Bir kolonu panoya eklerken HER İKİ tarafı da bağlarız:
     * hem listeye ekle, hem kolonun board'unu bu pano yap.
     * (Sadece tek tarafı set edersen JPA/bellek tutarsız kalır — klasik hata.)
     */
    public void addColumn(BoardColumn column) {
        columns.add(column);
        column.setBoard(this);
    }

    public void removeColumn(BoardColumn column) {
        columns.remove(column);
        column.setBoard(null);
    }

}
