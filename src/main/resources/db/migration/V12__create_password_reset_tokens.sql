-- ═══════════════════════════════════════════════════════
-- V12: Şifre sıfırlama jetonları
--
-- E-posta doğrulama jetonlarıyla (V11) aynı yapı, bir farkla: jetonun KENDİSİ
-- değil, SHA-256 özeti saklanır. Doğrulama jetonu sızarsa en fazla bir hesap
-- etkinleşir; sıfırlama jetonu sızarsa hesap ELE GEÇER. Veritabanı yedeği
-- çalınsa bile özetten jeton geri üretilemez.
-- ═══════════════════════════════════════════════════════

CREATE TABLE password_reset_tokens (
    id          BIGSERIAL     PRIMARY KEY,
    user_id     BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)   NOT NULL UNIQUE,   -- SHA-256, hex (64 karakter)
    expires_at  TIMESTAMP     NOT NULL,
    used_at     TIMESTAMP,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_password_reset_user_id ON password_reset_tokens(user_id);
