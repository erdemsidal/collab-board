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
