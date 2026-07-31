-- ═══════════════════════════════════════════════════════
-- V7: Çalışma alanları (workspace / şirket)
--
-- Panolar artık tek tek kullanıcılara değil bir ÇALIŞMA ALANINA aittir.
-- Şirkete katılan biri, tek tek davet edilmeden şirketin panolarına erişir.
--
-- Bu migration iki iş yapar:
--   A) Yeni tabloları kurar
--   B) MEVCUT VERİYİ taşır (kimse erişimini kaybetmesin)
-- ═══════════════════════════════════════════════════════

-- ── A) Şema ──────────────────────────────────────────────

CREATE TABLE workspaces (
    id         BIGSERIAL     PRIMARY KEY,
    name       VARCHAR(200)  NOT NULL,
    -- slug: URL'de ve teknik referanslarda kullanılabilen kısa, benzersiz ad.
    slug       VARCHAR(80)   NOT NULL UNIQUE,
    created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE workspace_members (
    id           BIGSERIAL    PRIMARY KEY,
    workspace_id BIGINT       NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id      BIGINT       NOT NULL REFERENCES users(id)      ON DELETE CASCADE,

    -- OWNER  → şirketin sahibi: her şey
    -- ADMIN  → üye yönetimi + tüm panolarda tam yetki
    -- MEMBER → şirketin panolarını düzenleyebilir
    -- GUEST  → şirketin panolarına erişemez; SADECE ayrıca davet edildiği panolara
    role         VARCHAR(20)  NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_workspace_members UNIQUE (workspace_id, user_id)
);

CREATE INDEX idx_workspace_members_user_id ON workspace_members(user_id);

-- Panoyu çalışma alanına bağla. ÖNCE nullable ekliyoruz; veriyi doldurduktan
-- SONRA zorunlu kılacağız. Tersini yaparsak mevcut satırlar yüzünden migration patlar.
ALTER TABLE boards ADD COLUMN workspace_id BIGINT REFERENCES workspaces(id);

-- ── B) Veri geçişi ───────────────────────────────────────
-- Sıralama kritik: (1) alan aç → (2) üyelik ver → (3) panoları bağla → (4) zorunlu kıl

-- 1) Her kullanıcıya bir "Kişisel Alan".
--    Karar: sahipsiz pano olmasın; kişisel kullanım da bir çalışma alanıdır.
--    Böylece kodda "workspace'i var mı?" diye ikinci bir yol açmak gerekmez.
INSERT INTO workspaces (name, slug)
SELECT COALESCE(NULLIF(TRIM(u.first_name || ' ' || u.last_name), ''), u.email) || ' — Kişisel Alan',
       'kisisel-' || u.id
FROM users u;

-- 2) Kullanıcı kendi kişisel alanının sahibi olur.
INSERT INTO workspace_members (workspace_id, user_id, role)
SELECT w.id, u.id, 'OWNER'
FROM users u
JOIN workspaces w ON w.slug = 'kisisel-' || u.id;

-- 3) Her panoyu, mevcut pano sahibinin kişisel alanına taşı.
--    Panonun birden çok OWNER'ı varsa en küçük user_id seçilir (belirlilik için).
UPDATE boards b
SET workspace_id = w.id
FROM (
    SELECT board_id, MIN(user_id) AS owner_id
    FROM board_members
    WHERE role = 'OWNER'
    GROUP BY board_id
) o
JOIN workspaces w ON w.slug = 'kisisel-' || o.owner_id
WHERE o.board_id = b.id;

-- Güvenlik ağı: sahibi bulunamayan pano kalırsa (olmaması gerekir) ilk alana bağla,
-- ki aşağıdaki NOT NULL kısıtı veriyi kilitlemesin.
UPDATE boards
SET workspace_id = (SELECT MIN(id) FROM workspaces)
WHERE workspace_id IS NULL;

-- 4) Artık her pano bir çalışma alanına ait: kuralı veritabanına yazdır.
ALTER TABLE boards ALTER COLUMN workspace_id SET NOT NULL;

CREATE INDEX idx_boards_workspace_id ON boards(workspace_id);
