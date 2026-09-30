-- ═══════════════════════════════════════════════════════
-- V10: Kart detayları + kolon WIP limiti
--
-- WIP limiti Kanban'ın temel kuralıdır: bir kolonda aynı anda kaç kart
-- bulunabileceğini sınırlar. NULL = sınırsız, böylece mevcut panolar
-- davranış değiştirmeden çalışmaya devam eder.
-- ═══════════════════════════════════════════════════════

ALTER TABLE board_columns
    ADD COLUMN wip_limit INT;

-- Sınır ancak pozitifse anlamlı; 0 "hiç kart giremez" demek olurdu.
ALTER TABLE board_columns
    ADD CONSTRAINT chk_board_columns_wip_limit CHECK (wip_limit IS NULL OR wip_limit > 0);

-- ── Kart detayları ───────────────────────────────────────
ALTER TABLE cards
    ADD COLUMN description TEXT,
    ADD COLUMN assignee_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN due_date    DATE;

-- Atanan kişi silinirse kart kalır, ataması boşalır (ON DELETE SET NULL).
-- "Bana atanmış kartlar" sorgusu için index.
CREATE INDEX idx_cards_assignee_id ON cards(assignee_id);

-- Son tarihe göre sıralama/filtreleme için.
CREATE INDEX idx_cards_due_date ON cards(due_date) WHERE due_date IS NOT NULL;
