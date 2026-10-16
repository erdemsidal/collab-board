-- ═══════════════════════════════════════════════════════
-- V13: Pano davetleri
--
-- Önceden yönetici bir e-posta yazdığı anda o kişi panoya ekleniyordu; kişinin
-- haberi ya da onayı yoktu. Artık önce davet oluşur, davet edilen kabul edince
-- üyelik başlar.
--
-- Davet kullanıcıya değil E-POSTAYA bağlı: henüz hesabı olmayan birini de davet
-- edebilmek için. O adresle kayıt olup doğrulayan kişi daveti görür.
-- ═══════════════════════════════════════════════════════

CREATE TABLE board_invitations (
    id            BIGSERIAL     PRIMARY KEY,
    board_id      BIGINT        NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
    email         VARCHAR(255)  NOT NULL,               -- küçük harfe çevrilmiş
    role          VARCHAR(20)   NOT NULL,
    invited_by    BIGINT        REFERENCES users(id) ON DELETE SET NULL,
    status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    responded_at  TIMESTAMP,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_board_invitations_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELLED'))
);

-- Aynı panoda aynı adrese aynı anda yalnızca bir bekleyen davet olabilir.
-- Kısmi index: eski (kabul/ret edilmiş) davetler yeni bir daveti engellemesin.
CREATE UNIQUE INDEX ux_board_invitations_pending
    ON board_invitations(board_id, email) WHERE status = 'PENDING';

-- "Bana gelen bekleyen davetler" sorgusu.
CREATE INDEX idx_board_invitations_email_pending
    ON board_invitations(email) WHERE status = 'PENDING';
