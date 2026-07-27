-- ═══════════════════════════════════════════════════════
-- V4: Kanban domaini — boards / board_columns / cards
-- Board → BoardColumn → Card hiyerarşisi.
-- ═══════════════════════════════════════════════════════

-- ── Panolar ──────────────────────────────────────────────
CREATE TABLE boards (
    id          BIGSERIAL      PRIMARY KEY,
    name        VARCHAR(200)   NOT NULL,
    created_at  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ── Kolonlar (todo / doing / done ...) ───────────────────
CREATE TABLE board_columns (
    id          BIGSERIAL      PRIMARY KEY,
    board_id    BIGINT         NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
    name        VARCHAR(100)   NOT NULL,
    position    INT            NOT NULL,
    created_at  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- Bir panonun kolonlarını çekerken hızlı olsun diye FK üzerine index.
CREATE INDEX idx_board_columns_board_id ON board_columns(board_id);

-- ── Kartlar ──────────────────────────────────────────────
CREATE TABLE cards (
    id          BIGSERIAL      PRIMARY KEY,
    column_id   BIGINT         NOT NULL REFERENCES board_columns(id) ON DELETE CASCADE,
    title       VARCHAR(500)   NOT NULL,
    position    INT            NOT NULL,
    version     BIGINT         NOT NULL DEFAULT 0,   -- @Version (optimistic locking)
    created_at  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_cards_column_id ON cards(column_id);
