-- ═══════════════════════════════════════════════════════
-- V3: Refresh Tokens tablosu (Redis backup)
-- Primary storage Redis'te, burası güvenlik audit ve
-- Redis down olduğunda fallback için.
-- ═══════════════════════════════════════════════════════

CREATE TABLE refresh_tokens (
    id          BIGSERIAL       PRIMARY KEY,
    token       VARCHAR(255)    NOT NULL UNIQUE,
    user_id     BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expiry_date TIMESTAMP       NOT NULL,
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
