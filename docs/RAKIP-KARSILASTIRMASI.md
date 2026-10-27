# CollabBoard ve rakipleri

Tarih: 24 Ekim 2026. Asıl karşılaştırma Trello ile, çünkü CollabBoard da aynı türden bir araç (pano, liste, kart). Jira, Asana ve Linear daha büyük ekipler ve yazılım süreçleri için; Planka ve Kanboard ise kendi sunucunda çalıştırılan açık kaynak Trello benzerleri.

## Özellik özellik

| Özellik | Trello | CollabBoard |
|---|---|---|
| Pano, liste, kart, sürükle-bırak | Var | Var |
| Kart açıklaması, son tarih, kişi atama | Var | Var |
| Canlı güncelleme (yenilemeden) | Var | Var |
| Yorumlar ve @bahsetme | Var | Var |
| Renkli etiketler | Var | Var |
| Kontrol listesi (alt görevler) | Var | Var |
| Dosya ve bağlantı ekleme | Var (ücretsizde 10 MB) | **Yok** |
| Arşiv ve geri alma | Var | **Yok** (silme kalıcı) |
| Kartı başka panoya taşıma veya kopyalama | Var | **Yok** |
| E-posta bildirimi (atama, yorum, son tarih) | Var | **Kısmen** (davet ve yorumda anılma) |
| Google ile giriş | Var | Bu turda ekleniyor |
| Pano şablonları | Var | Yok |
| Takvim ve zaman çizelgesi görünümü | Premium (ücretli) | Yok |
| Otomasyon | Var (ücretsizde ayda 250 çalıştırma) | Yok |
| Mobil uygulama | Var | Yok (site telefona uyumlu) |
| Kimin panoda olduğu, canlı imleçler | Yok | **Var** |
| Panonun geçmişteki hâline dönme | Yok (yalnızca hareket listesi) | **Var** |
| WIP limiti | Eklentiyle | **Var** |
| Akış ölçümü (bekleme süresi, darboğaz, yaşlanan iş) | Yok | **Var** |
| Ücretsiz kullanım sınırı | Çalışma alanı başına 10 kişi, 10 pano | Yok |

## Ne eksik, hangi sırayla

Trello'yu kullanan biri CollabBoard'a geçince ilk gün şunları arar. Önem sırasıyla:

1. **Yorumlar ve @bahsetme.** Yapıldı (24 Ekim 2026, [ADR 0019](adr/0019-kart-yorumlari-ve-bahsetme.md)): kartın altında yorumlar, anılan kişiye bildirim ve e-posta, karta atanana uygulama içinde haber.
2. **Etiketler.** Yapıldı (26 Ekim 2026, [ADR 0022](adr/0022-etiketler-ve-kontrol-listeleri.md)): panoya özel etiketler (sekiz renk, isteğe bağlı ad), kartta küçük çipler, etikete göre süzme, geçmişte o anki hâliyle.
3. **Kontrol listesi.** Yapıldı (26 Ekim 2026, [ADR 0022](adr/0022-etiketler-ve-kontrol-listeleri.md)): kartta bir ya da daha çok liste, işaretleme, sürükleyerek ya da klavyeyle sıralama, kartta "3/5".
4. **Arşiv ve geri alma.** Silinen kart kaybolmasın: önce arşive gitsin, "Geri al" düğmesi çıksın.
5. **E-posta bildirimleri.** Bana kart atandığında, son tarih yaklaştığında. Yorumda adım geçince posta artık gidiyor; kişi isterse kapatabilsin.
6. **Kartı başka panoya taşıma.**
7. **Bağlantı ve dosya ekleme.** Önce bağlantı (ucuz), dosya sonra (depolama gerektirir).
8. **Pano şablonları.** "Yazılım ekibi", "Kişisel işler" gibi hazır kolonlar.

## Bilerek yapmayacaklarımız

Otomasyon, eklenti (Power-Up) sistemi, harita ve zaman çizelgesi görünümleri, yapay zekâ özellikleri. Hepsi büyük iş, CollabBoard'un amacı sade ve canlı bir pano. Mobil uygulama yerine site telefonda iyi çalışmaya devam edecek.

## Fazla olan, çıkardıklarımız

Ana ekrandaki dört sayaç kutusu ve 7 günlük grafik (aynı bilgi listelerde vardı), kenar menüsünde zil ve hesap menüsünü tekrar eden bağlantılar, kısayol kutusu, pano altındaki geliştirici durum çubuğu (gönderilen işlem sayısı gibi), yan paneldeki akış kutusu (Akış sekmesinin tekrarı), tanıtım sayfasındaki "kaputun altında" teknik bölümü ve teknik etiketler. Metinlerdeki uzun tireler ve süslü cümleler sade Türkçeyle değiştirildi.

## Korunacak farkımız

Canlı imleçler ve kimin panoda olduğu, panonun geçmişine dönme, akış ölçümü ve WIP limiti. Trello'da bunlar ya yok ya da eklentiyle geliyor.

## Kaynaklar

- Trello fiyatları ve ücretsiz plan sınırları (üçüncü taraf özetleri, Ekim 2026): [activepieces.com](https://www.activepieces.com/blog/trello-pricing-2026-plans-costs-and-hidden-fees), [automationatlas.io](https://automationatlas.io/answers/trello-pricing-explained-2026/), [saascrmreview.com](https://saascrmreview.com/trello-pricing/)
- Açık kaynak alternatifler: [ssdnodes.com](https://www.ssdnodes.com/learn/self-hosted-trello-alternatives)
