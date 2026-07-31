-- ═══════════════════════════════════════════════════════
-- V8: Çalışma alanı silinince panoları da silinsin
--
-- Arayüzde "çalışma alanını sil" işlemi, içindeki panoların da kalıcı olarak
-- silineceğini söyleyerek onay ister. Bu kuralı VERİTABANINA yazdırıyoruz:
-- uygulama kodunda tek tek silmek yerine zincir kendiliğinden işlesin.
--
-- Zincir zaten hazırdı; tek eksik halka boards → workspaces bağıydı:
--   workspaces → boards → board_columns → cards
--                      ├→ board_members
--                      └→ board_activities
-- ═══════════════════════════════════════════════════════

-- Mevcut kısıtın adı Postgres tarafından otomatik verilmişti; onu bulup
-- kaldırıyor ve yerine ON DELETE CASCADE olanı koyuyoruz.
ALTER TABLE boards DROP CONSTRAINT IF EXISTS boards_workspace_id_fkey;

ALTER TABLE boards
    ADD CONSTRAINT boards_workspace_id_fkey
    FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE;
