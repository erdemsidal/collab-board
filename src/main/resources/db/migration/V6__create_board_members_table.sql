-- ═══════════════════════════════════════════════════════
-- V6: Pano üyelikleri ve roller
--
-- Bu tablo, "kim hangi panoda ne yapabilir" sorusunun tek kaynağıdır.
-- İleride şirket/workspace eklendiğinde, üyelik zinciri
--   kullanıcı → şirket üyeliği → şirketin panoları
-- şeklinde genişleyecek; bu tablo o zaman pano bazlı İSTİSNALARI
-- (ör. şirket üyesi olmayan bir misafiri tek bir panoya davet etmek)
-- ifade etmeye devam edebilir.
-- ═══════════════════════════════════════════════════════

CREATE TABLE board_members (
    id         BIGSERIAL     PRIMARY KEY,
    board_id   BIGINT        NOT NULL REFERENCES boards(id)  ON DELETE CASCADE,
    user_id    BIGINT        NOT NULL REFERENCES users(id)   ON DELETE CASCADE,

    -- OWNER  → her şey + üye yönetimi + pano silme
    -- EDITOR → kart/kolon değiştirme
    -- VIEWER → sadece görüntüleme
    role       VARCHAR(20)   NOT NULL,

    created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Bir kullanıcı aynı panoda iki kez üye olamaz. Bu kısıt, "iki farklı rolle
    -- iki satır" gibi belirsiz durumları VERİTABANI seviyesinde imkânsız kılar.
    CONSTRAINT uq_board_members_board_user UNIQUE (board_id, user_id)
);

-- "Benim panolarım" sorgusu için (user_id ile filtreleme).
CREATE INDEX idx_board_members_user_id ON board_members(user_id);

-- ── Tek seferlik geçiş ────────────────────────────────────────────────
-- Bu özellikten ÖNCE oluşturulmuş panoların sahibi yok; erişilemez hâle
-- gelmemeleri için en eski kullanıcıya OWNER olarak bağlanırlar.
-- (Kullanıcı yoksa hiçbir satır eklenmez.)
INSERT INTO board_members (board_id, user_id, role)
SELECT b.id, (SELECT MIN(id) FROM users), 'OWNER'
FROM boards b
WHERE EXISTS (SELECT 1 FROM users);
