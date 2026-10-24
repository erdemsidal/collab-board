-- ═══════════════════════════════════════════════════════
-- V16: Bağlı dış hesaplar ("Google ile devam et")
--
-- Bir CollabBoard hesabına bağlanmış dış kimlikler. Kişiyi sağlayıcının
-- verdiği kalıcı kimlikle (subject, Google'da "sub") tanırız, e-postayla
-- değil: e-posta değişebilir, subject değişmez.
--
-- email: bağlandığı andaki adres. Yalnızca iz için; eşleştirmede kullanılmaz.
--
-- Hesap şifresiz de açılabiliyor artık (Google ile kayıt olan kişinin
-- şifresi yoktur), bu yüzden users.password boş olabilir. Şifresiz hesapla
-- şifreyle giriş, yanlış şifreyle aynı şekilde reddedilir (bkz. ADR 0018).
-- ═══════════════════════════════════════════════════════

CREATE TABLE user_identities (
    id          BIGSERIAL     PRIMARY KEY,
    user_id     BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider    VARCHAR(30)   NOT NULL,              -- "google"
    subject     VARCHAR(255)  NOT NULL,              -- sağlayıcıdaki kalıcı kimlik
    email       VARCHAR(255)  NOT NULL,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Bir dış hesap yalnızca bir CollabBoard hesabına bağlanabilir.
    CONSTRAINT ux_user_identities_provider_subject UNIQUE (provider, subject)
);

-- "Bu kişinin bağlı hesapları" (profilde ve giriş yanıtında gösterilir).
CREATE INDEX idx_user_identities_user_id ON user_identities(user_id);

ALTER TABLE users ALTER COLUMN password DROP NOT NULL;
