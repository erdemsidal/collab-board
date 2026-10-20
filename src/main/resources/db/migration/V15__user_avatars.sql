-- ═══════════════════════════════════════════════════════
-- V15: Profil fotoğrafları
--
-- Fotoğraf veritabanında, dosya sisteminde değil: barındırma platformunda
-- konteynerin diski kalıcı değil, her dağıtımda sıfırlanır. Ayrı bir nesne
-- deposu (S3 vb.) ise 256×256'lık birkaç kilobaytlık görseller için fazla.
--
-- Her fotoğraf rastgele bir anahtarla saklanır ve adresi o anahtardır
-- (/api/avatars/{anahtar}). Fotoğraf değişince anahtar da değişir:
--   - tarayıcı önbelleği eski görseli göstermeye devam etmez,
--   - kullanıcı numarasından tahmin edilerek fotoğraflar taranamaz.
-- ═══════════════════════════════════════════════════════

CREATE TABLE user_avatars (
    avatar_key    VARCHAR(36)   PRIMARY KEY,
    user_id       BIGINT        NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    content_type  VARCHAR(30)   NOT NULL,
    data          BYTEA         NOT NULL,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE users ADD COLUMN avatar_key VARCHAR(36);
