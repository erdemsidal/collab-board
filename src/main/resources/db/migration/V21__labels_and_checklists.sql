-- ═══════════════════════════════════════════════════════
-- V21: Etiketler ve kontrol listeleri (ADR 0022)
--
-- İkisi de kartın sürümüne (cards.version) dokunmaz: etiket eklemek ya da
-- bir maddeyi işaretlemek, aynı anda kartı taşıyan birinin işlemini bayat
-- saymamalı (yorumlarla aynı gerekçe, ADR 0019). Kart silinince etiket
-- bağları, listeleri ve maddeleri de gider.
-- ═══════════════════════════════════════════════════════

-- ── Panonun etiketleri ───────────────────────────────────
-- Her panonun kendi etiket takımı. Renk sabit bir paletten (8 renk), ad isteğe bağlı.
CREATE TABLE board_labels (
    id          BIGSERIAL     PRIMARY KEY,
    board_id    BIGINT        NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
    name        VARCHAR(40),
    color       VARCHAR(10)   NOT NULL,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_board_labels_color
        CHECK (color IN ('GREEN', 'YELLOW', 'ORANGE', 'RED', 'PINK', 'PURPLE', 'BLUE', 'SKY'))
);
CREATE INDEX idx_board_labels_board ON board_labels(board_id, id);

-- Bir panoda aynı adlı iki etiket olmaz (büyük/küçük harf farkı sayılmaz).
-- Süzgeçte "Acil"i seçen kişi hangisini seçtiğini bilmeli. Adsız etiketler serbest.
-- lower() veritabanında: Java ile Postgres "İ"yi farklı küçültüyor (ADR 0016).
CREATE UNIQUE INDEX ux_board_labels_name ON board_labels(board_id, lower(name)) WHERE name IS NOT NULL;

-- ── Kartın etiketleri ────────────────────────────────────
-- Bir küme: aynı etiket bir karta bir kez takılır. Etiket silinince bütün
-- kartlardan kalkar (ON DELETE CASCADE).
CREATE TABLE card_labels (
    card_id     BIGINT        NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
    label_id    BIGINT        NOT NULL REFERENCES board_labels(id) ON DELETE CASCADE,
    PRIMARY KEY (card_id, label_id)
);
CREATE INDEX idx_card_labels_label ON card_labels(label_id);

-- ── Kontrol listeleri ────────────────────────────────────
-- revision: listede her değişiklikte bir artar (satır kilidi altında). Canlı
-- olay listenin tamamını bu sayıyla taşır; arayüz elindekinden eski bir olayı
-- atar, geç gelen bir yayın yeni hâli ezmez.
CREATE TABLE checklists (
    id          BIGSERIAL     PRIMARY KEY,
    card_id     BIGINT        NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
    title       VARCHAR(100)  NOT NULL,
    revision    BIGINT        NOT NULL DEFAULT 0,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_checklists_card ON checklists(card_id, id);

-- Maddelerin sırası 0'dan boşluksuz. Aynı listede iki madde aynı sırada olamaz:
-- veritabanı bunu işlem sonunda (DEFERRABLE) denetler, çünkü yeniden
-- numaralandırma satırları tek tek günceller ve arada geçici çakışmalar olur.
CREATE TABLE checklist_items (
    id            BIGSERIAL     PRIMARY KEY,
    checklist_id  BIGINT        NOT NULL REFERENCES checklists(id) ON DELETE CASCADE,
    text          VARCHAR(300)  NOT NULL,
    done          BOOLEAN       NOT NULL DEFAULT FALSE,
    position      INT           NOT NULL,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_checklist_items_position UNIQUE (checklist_id, position) DEFERRABLE INITIALLY DEFERRED
);
