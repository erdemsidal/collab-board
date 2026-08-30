-- ═══════════════════════════════════════════════════════
-- V11: E-posta doğrulama jetonları
--
-- Kullanıcı kaydolduğunda hesap PASİF (users.enabled = false) açılır ve
-- e-postasına tek kullanımlık bir bağlantı gider. Bağlantıya tıklayana
-- kadar giriş yapamaz.
-- ═══════════════════════════════════════════════════════

CREATE TABLE email_verification_tokens (
    id          BIGSERIAL     PRIMARY KEY,
    user_id     BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token       VARCHAR(64)   NOT NULL UNIQUE,
    expires_at  TIMESTAMP     NOT NULL,
    -- Kullanıldığı an. NULL = henüz kullanılmadı. Satırı silmek yerine
    -- işaretliyoruz: "bu bağlantı zaten kullanılmış" ile "böyle bir bağlantı
    -- yok" farklı durumlar ve kullanıcıya farklı mesaj vermeliler.
    used_at     TIMESTAMP,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Doğrulama isteği jetonla gelir; tekil index aramayı da karşılar.
-- "Bu kullanıcının açık jetonları" sorgusu (yeniden gönderimde eskilerini
-- geçersiz kılmak için) bu index'i kullanır.
CREATE INDEX idx_email_verification_user_id ON email_verification_tokens(user_id);

-- Süresi dolmuş jetonları temizlerken tarama yapmayalım.
CREATE INDEX idx_email_verification_expires_at ON email_verification_tokens(expires_at);

-- Mevcut kullanıcılar zaten aktif; doğrulama yalnızca BUNDAN SONRAKİ
-- kayıtlar için geçerli. Geriye dönük kimseyi kapatmıyoruz.
