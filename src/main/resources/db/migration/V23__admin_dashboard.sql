-- ═══════════════════════════════════════════════════════
-- V23: Yönetim paneli (ADR 0024)
--
-- Site sahibinin panelindeki sayılar bizim veritabanımızdan hesaplanır.
-- Bunun için tek yeni bilgi gerekiyor: kişi siteye en son ne zaman geldi.
-- ═══════════════════════════════════════════════════════

-- ── Son görülme ─────────────────────────────────────────
-- Oturum açmış bir istek ya da jeton yenilemesi gelince yazılır, ama kişi
-- başına en fazla 5 dakikada bir (LastSeenTracker). Her istekte yazmak her
-- tıklamayı bir veritabanı yazmasına çevirirdi.
-- Saat, diğer sütunlar gibi UTC duvar saati (bkz. AdminDashboardService).
ALTER TABLE users ADD COLUMN last_seen_at TIMESTAMP;

-- Var olan hesaplar için en iyi tahmin: panolardaki son hareketleri. Hiç
-- hareketi olmayan kişide boş kalır ("henüz görülmedi"). Bu bir alt sınır:
-- kişi panoya bakıp hiçbir şeyi değiştirmeden çıkmış olabilir.
UPDATE users u
SET last_seen_at = a.last_at
FROM (
    SELECT user_id, MAX(created_at) AS last_at
    FROM board_activities
    WHERE user_id IS NOT NULL
    GROUP BY user_id
) a
WHERE a.user_id = u.id;

-- ── İndeksler ───────────────────────────────────────────
-- "Son kayıtlar" listesi en yeni 50 hesabı ister, günlük kayıt grafiği son 30
-- günü. İkisi de kayıt anına göre sıralı ya da aralıklı okur.
CREATE INDEX idx_users_created_at ON users(created_at);

-- "En hareketli panolar" son 7 günün hareketlerini sayar. board_activities en
-- hızlı büyüyen tablo ve satırlar zaman sırasıyla eklenir; BRIN indeksi bu
-- durumda sayfa aralıklarının en küçük/en büyük zamanını tutar, eski
-- bölümleri hiç okumadan atlar. B-tree'nin küçük bir kesri kadar yer kaplar
-- ve her yeni satırda neredeyse hiç iş çıkarmaz.
CREATE INDEX idx_board_activities_created_brin ON board_activities USING brin (created_at);

-- last_seen_at'e indeks YOK, bilerek: panel kullanıcı tablosunu zaten tek
-- geçişte sayıyor (COUNT ... FILTER), indeks kullanılmazdı. Üstelik indeksli
-- bir sütunu güncellemek Postgres'in en ucuz güncelleme yolunu (HOT) kapatır;
-- her "son görülme" yazması gereksiz yere pahalılaşırdı.
