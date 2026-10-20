-- ═══════════════════════════════════════════════════════
-- V14: Bildirimler
--
-- Kişiye özel olaylar: davet geldi, davetin kabul/ret edildi, davet geri
-- çekildi. Canlı olarak kullanıcının kendi kanalına gider; çevrim dışıysa
-- buradan, zil menüsünden okunur.
--
-- Gönderenin ve panonun ADI kopyalanarak saklanır (board_activities ile aynı
-- gerekçe): kişi adını değiştirse ya da pano silinse bile bildirim anlamını
-- korur.
-- ═══════════════════════════════════════════════════════

CREATE TABLE notifications (
    id             BIGSERIAL     PRIMARY KEY,
    user_id        BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type           VARCHAR(40)   NOT NULL,
    actor_id       BIGINT        REFERENCES users(id) ON DELETE SET NULL,
    actor_name     VARCHAR(200),
    board_id       BIGINT        REFERENCES boards(id) ON DELETE SET NULL,
    board_name     VARCHAR(200),
    invitation_id  BIGINT        REFERENCES board_invitations(id) ON DELETE SET NULL,
    role           VARCHAR(20),
    read_at        TIMESTAMP,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Zil menüsü: kullanıcının bildirimleri, en yeniden eskiye.
CREATE INDEX idx_notifications_user ON notifications(user_id, id DESC);

-- Okunmamış sayısı (zilin üstündeki rozet).
CREATE INDEX idx_notifications_unread ON notifications(user_id) WHERE read_at IS NULL;

-- Bir davet için kişiye en fazla bir "davet geldi" bildirimi. Hesap davetten
-- sonra açıldığında bildirim sonradan üretiliyor; bu kısıt onu tekrar
-- üretmeyi engeller.
CREATE UNIQUE INDEX ux_notifications_invite
    ON notifications(user_id, invitation_id) WHERE type = 'BOARD_INVITE';
