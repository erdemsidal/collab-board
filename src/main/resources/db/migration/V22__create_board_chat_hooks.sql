-- ═══════════════════════════════════════════════════════
-- V22: Kanal bildirimleri (Slack, Microsoft Teams, Discord)
--
-- Pano yöneticisi bir kanalın "gelen webhook" adresini yapıştırır; seçtiği
-- olaylar olunca o kanala kısa bir mesaj gider (ADR 0023).
--
-- Adres bir PAROLA gibidir: bilen herkes o kanala yazabilir. Bu yüzden düz
-- metin olarak saklanmaz (url_encrypted, AES-GCM) ve tarayıcıya hiç geri
-- gönderilmez; yalnızca son 4 karakteri (url_hint) gösterilir.
-- ═══════════════════════════════════════════════════════

CREATE TABLE board_chat_hooks (
    id               BIGSERIAL     PRIMARY KEY,
    board_id         BIGINT        NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
    provider         VARCHAR(20)   NOT NULL,   -- SLACK | TEAMS | DISCORD
    url_encrypted    TEXT          NOT NULL,
    url_hint         VARCHAR(8)    NOT NULL,
    label            VARCHAR(60),
    events           VARCHAR(200)  NOT NULL,   -- virgülle: CARD_CREATED,CARD_DONE,...
    locale           VARCHAR(5)    NOT NULL DEFAULT 'tr',
    failure_count    INT           NOT NULL DEFAULT 0,
    -- Boşsa bağlantı açık. Doluysa neden durduğu: GONE, FORBIDDEN, FAILING, BLOCKED.
    disabled_reason  VARCHAR(30),
    last_sent_at     TIMESTAMP,
    created_by       BIGINT        REFERENCES users(id) ON DELETE SET NULL,
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Bir olay olduğunda panonun açık bağlantıları aranır.
CREATE INDEX idx_board_chat_hooks_board ON board_chat_hooks(board_id);

-- Kanal bağlantısı durunca yöneticilere giden bildirimde kanalın adı.
-- Pano ve kart adları gibi kopyalanarak saklanır: bağlantı silinse de bildirim anlamını korur.
ALTER TABLE notifications ADD COLUMN detail VARCHAR(200);
