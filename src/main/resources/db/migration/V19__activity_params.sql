-- ═══════════════════════════════════════════════════════
-- V19: Geçmiş satırının parçaları (ADR 0020)
--
-- description, satırı yazıldığı anda Türkçe cümle olarak saklıyor
-- ("'Süt al' kartını Done kolonuna taşıdı"). İngilizce arayüz bu cümleyi
-- çeviremez; payload da yetmez, çünkü taşıma olayında yalnızca kimlikler var,
-- kartın ve kolonun o anki adı yok.
--
-- params, cümlenin değişen parçalarını o anki hâliyle tutar:
--   {"card": "Süt al", "column": "Done"}
-- Arayüz satırı türünden ve bu parçalardan kendi dilinde kurar. Bu alandan
-- önce yazılmış satırlarda params yoktur; onlar description ile gösterilir.
-- ═══════════════════════════════════════════════════════

ALTER TABLE board_activities ADD COLUMN params TEXT;
