-- ═══════════════════════════════════════════════════════
-- V17: Kart yorumları ve @bahsetme
--
-- Yorum kartın parçası değil, kartın ALTINDA bir konuşmadır: kartın sürümünü
-- (cards.version) değiştirmez, olay kaydına (board_activities) yalnızca bilgi
-- olarak düşer. Kart silinince yorumları da gider; yazan hesabını silerse
-- yorum kalır, yazarı boş görünür (ADR 0019).
-- ═══════════════════════════════════════════════════════

CREATE TABLE comments (
    id          BIGSERIAL     PRIMARY KEY,
    card_id     BIGINT        NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
    author_id   BIGINT        REFERENCES users(id) ON DELETE SET NULL,
    body        TEXT          NOT NULL,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    edited_at   TIMESTAMP
);

-- Kartın yorumları eskiden yeniye; panodaki yorum sayıları da bu indeksten.
CREATE INDEX idx_comments_card ON comments(card_id, id);

-- Yorumda anılan kişiler. Kimin anıldığı metinden değil buradan okunur:
-- metinde "@Ayşe Yılmaz" yazması, aynı adlı iki kişiden hangisi olduğunu söylemez.
CREATE TABLE comment_mentions (
    comment_id  BIGINT        NOT NULL REFERENCES comments(id) ON DELETE CASCADE,
    user_id     BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (comment_id, user_id)
);

-- Bildirim kartı açabilsin diye kart ve yorum bilgisi. Kart başlığı, pano adı
-- gibi kopyalanarak saklanır: kart silinse de bildirim anlamını korur.
ALTER TABLE notifications
    ADD COLUMN card_id     BIGINT        REFERENCES cards(id) ON DELETE SET NULL,
    ADD COLUMN card_title  VARCHAR(500),
    ADD COLUMN comment_id  BIGINT        REFERENCES comments(id) ON DELETE SET NULL;

-- Bir yorumda anılan kişiye en fazla bir bildirim (ve bir posta). Yorum
-- düzenlenip aynı kişi yeniden anılsa bile ikinci kez gitmez.
CREATE UNIQUE INDEX ux_notifications_mention
    ON notifications(user_id, comment_id) WHERE type = 'COMMENT_MENTION';
