-- ═══════════════════════════════════════════════════════
-- V18: Kullanıcının dili (ADR 0020)
--
-- Arayüz ve kişiye giden postalar bu dilde: "tr" ya da "en".
-- Yeni hesapta kayıt isteğinin diliyle (Accept-Language) dolar; kişi
-- profilinden ya da hesap menüsünden değiştirir.
--
-- Var olan hesaplar Türkçe başlar: bugüne kadar her şeyi Türkçe gördüler.
-- Uzunluk 5: "pt-BR" gibi bölgeli bir dil gerekirse sütun yetsin.
-- ═══════════════════════════════════════════════════════

ALTER TABLE users ADD COLUMN locale VARCHAR(5) NOT NULL DEFAULT 'tr';
