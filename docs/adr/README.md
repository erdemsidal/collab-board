# Mimari Karar Kayıtları (ADR)

Bu klasör, projedeki önemli teknik kararları ve **gerekçelerini** tutar. Her ADR
şu iskeleti izler: **Bağlam → Seçenekler → Karar → Sonuçlar (feda ettiklerimiz).**

Amaç: "ne kodladık" değil, **"neden o kararı verdik"** sorusunun cevabını kalıcı
kılmak. Bir kararı sonradan sorgularken (veya mülakatta savunurken) buraya bakarız.

## Kayıtlar

| No | Karar | Durum |
|----|-------|-------|
| [0001](0001-operasyon-tabanli-model-ve-cakisma-cozumu.md) | Operasyon tabanlı model + versiyonlama/LWW (OT/CRDT değil) | Kabul edildi |
| [0002](0002-gercek-zamanli-protokol-stomp.md) | Gerçek zamanlı protokol: STOMP over WebSocket | Kabul edildi |
| [0003](0003-cakisma-cozumu-versiyon-kontrolu.md) | Çakışma çözümü: optimistic versiyon kontrolü + snapshot resync | Kabul edildi |
| [0004](0004-cok-sunucuya-olcekleme-redis-pubsub.md) | Çok sunucuya ölçekleme: Redis Pub/Sub köprüsü | Kabul edildi |
| [0005](0005-websocket-kimlik-dogrulama.md) | WebSocket kimlik doğrulama: JWT, STOMP CONNECT frame'inde | Kabul edildi |
| [0006](0006-olay-kaydi-uzerinden-gecmis-ve-akis.md) | Geçmiş ve akış ölçümü: olay kaydından yeniden hesaplama | Kabul edildi |
| [0007](0007-wip-limiti-ve-kart-detaylari.md) | WIP limiti (akış sağlığının tedavisi) + kart detayları | Kabul edildi |
| [0008](0008-kolon-yasam-dongusu-ve-gecmis-tohumlamasi.md) | Kolon yaşam döngüsü ve geçmişin tohumlanması | Kabul edildi |
| [0009](0009-eposta-dogrulama.md) | E-posta doğrulama ve posta gönderimi | Kabul edildi |
| [0010](0010-canliya-cikis-oncesi-guvenlik.md) | Canlıya çıkış öncesi güvenlik sıkılaştırmaları | Kabul edildi |
| [0011](0011-sifre-sifirlama.md) | Şifre sıfırlama | Kabul edildi |
| [0012](0012-onayli-davet-ve-kisisel-canli-bildirim.md) | Onaylı davet ve kişiye özel canlı bildirim | Kabul edildi |
| [0013](0013-profil-ve-fotograf.md) | Profil, şifre değiştirme ve profil fotoğrafı | Kabul edildi |
| [0014](0014-canli-imlec-ve-akici-tasima.md) | Canlı imleçler ve akıcı taşıma | Kabul edildi |
| [0015](0015-tanitim-sayfasi-ve-ana-ekran-ozeti.md) | Tanıtım sayfası ve ana ekran özeti | Kabul edildi |
| [0016](0016-genel-arama.md) | Genel arama: erişilebilen panolarda, kaçışlanmış LIKE | Kabul edildi |
| [0017](0017-arayuz-yeniden-tasarimi.md) | Arayüzün yeniden tasarımı ("A · Gece"): tema, komut paleti, pano sekmeleri | Kabul edildi |
| [0018](0018-google-ile-giris.md) | Google ile giriş: sunucu tarafı kod akışı, Redis'te akış, tek kullanımlık kod, doğrulanmış adresle bağlama | Kabul edildi |
| [0019](0019-kart-yorumlari-ve-bahsetme.md) | Kart yorumları ve @bahsetme: kart operasyonu değil, izleyici de yazar, yorum başına kişiye bir posta, kişi başına hız sınırı | Kabul edildi |
| [0020](0020-turkce-ve-ingilizce.md) | Türkçe ve İngilizce: tek sözlük modülü, dil hesapta, sunucuda anahtar taşıyan hatalar, geçmiş satırı parçalarıyla, ürün adı tek yerde | Kabul edildi |
| [0021](0021-kart-anahtari-ve-pano-sablonlari.md) | Kart anahtarı (STR-42, kimlikten türer, erişim yoksa 404) ve dal adı; pano şablonları kodda, kolonlar açanın dilinde | Kabul edildi |
| [0022](0022-etiketler-ve-kontrol-listeleri.md) | Etiketler ve kontrol listeleri: kart operasyonu değil (REST + canlı olay), etiket bir küme, liste satır kilidi ve revision ile, etiketler geçmişte oynatılır, listeler oynatılmaz | Kabul edildi |
| [0023](0023-kanal-bildirimleri.md) | Kanal bildirimleri (Slack, Teams, Discord): gelen webhook, şifreli adres, izin listesi, işlem kaydedildikten sonra arka planda gönderim | Kabul edildi |
| [0024](0024-yonetim-paneli-ve-ziyaretci-analitigi.md) | Yönetim paneli ve ziyaretçi analitiği: sayılar kendi veritabanımızdan, sahipler ortam değişkeninde, sahip olmayana 404, son görülme kişi başına 5 dakikada bir; ziyaretçiler için çerezsiz Umami | Kabul edildi |
| [0025](0025-guvenlik-incelemesi-duzeltmeleri.md) | Güvenlik incelemesi düzeltmeleri: canlı bağlantıda izin listesi (varsayılan ret, `x-reason` ile ERROR), gerçek istemci IP'si (yalnızca özel ağdaki vekile güven) ve hesap başına giriş sınırı, yaygın şifre listesi, profilden bağımsız JWT koruması, oturum aileleri ve yeniden kullanımda ailenin kapanması, erişim kalkınca canlı bağlantının kapanması, HSTS ve diğer başlıklar | Kabul edildi |
