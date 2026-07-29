-- ═══════════════════════════════════════════════════════
-- V5: Pano geçmişi (audit) — kim, ne zaman, ne yaptı
-- ═══════════════════════════════════════════════════════

CREATE TABLE board_activities (
    id          BIGSERIAL      PRIMARY KEY,
    board_id    BIGINT         NOT NULL REFERENCES boards(id) ON DELETE CASCADE,

    -- Kullanıcı silinirse geçmiş kaydı SİLİNMEZ, sadece bağlantısı kopar (SET NULL).
    -- Geçmiş, kullanıcı kaydından bağımsız olarak ayakta kalmalıdır.
    user_id     BIGINT         REFERENCES users(id) ON DELETE SET NULL,

    -- İsmi KOPYALAYARAK saklıyoruz (denormalizasyon). Neden?
    -- Kullanıcı adını değiştirse ya da silinse bile "o an kim yaptı" bilgisi
    -- değişmemeli. Audit kaydı, olayın çekildiği fotoğraftır — sonradan bozulmaz.
    actor_name  VARCHAR(200)   NOT NULL,

    type        VARCHAR(40)    NOT NULL,   -- ADD_CARD, MOVE_CARD, EDIT_CARD, ...
    description VARCHAR(500)   NOT NULL,   -- insan tarafından okunabilir özet
    created_at  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- "Şu panonun son hareketleri" sorgusu için: board_id ile filtrele, id'ye göre tersten sırala.
CREATE INDEX idx_board_activities_board_id_desc ON board_activities(board_id, id DESC);
