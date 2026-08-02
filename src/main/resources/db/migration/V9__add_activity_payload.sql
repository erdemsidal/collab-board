-- ═══════════════════════════════════════════════════════
-- V9: Geçmiş kayıtlarına yapılandırılmış olay verisi
--
-- board_activities şimdiye kadar yalnızca insan tarafından okunabilir bir açıklama
-- tutuyordu ("'Süt al' kartını Done kolonuna taşıdı"). Bu metin okunur ama panoyu
-- yeniden kurmaya yetmez.
--
-- payload, yayınlanan olayın JSON hâlini saklar. Böylece kayıt yalnızca "ne oldu"yu
-- anlatan bir günlük değil, üzerinden durumun yeniden hesaplanabildiği bir OLAY
-- KAYDI hâline gelir:
--   * belirli bir ana geri sarma (zaman yolculuğu)
--   * kartların kolonlarda ne kadar beklediğini ölçme (akış analizi)
--
-- Nullable: bu özellikten önce yazılmış satırlarda payload yoktur; onlar geçmiş
-- listesinde görünmeye devam eder ama yeniden kurulum hesabına katılmaz.
-- ═══════════════════════════════════════════════════════

ALTER TABLE board_activities ADD COLUMN payload TEXT;

-- Yeniden kurulum, olayları pano ve zaman sırasına göre okur.
CREATE INDEX idx_board_activities_replay ON board_activities(board_id, id);
