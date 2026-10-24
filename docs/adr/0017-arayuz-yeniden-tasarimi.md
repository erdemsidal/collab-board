# ADR 0017 — Arayüzün yeniden tasarımı ("A · Gece")

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-23

---

## Bağlam

Giriş sonrası ekranlar boş görünüyordu: üst çubukta yalnızca logo, zil ve
kullanıcı vardı; ana ekran birkaç sayaç ve bir pano listesinden ibaretti; pano
ekranının üstü de seyrekti. Üç görsel yön (Gece, Ferah, Canlı) tasarım tuvalinde
karşılaştırıldı, **A · Gece** seçildi: koyu zemin, turuncu vurgu, Inter +
JetBrains Mono. İstek: ilk açılışta tanıtım sayfası, giriş sonrası ve panoda dolu
bir üst bölge, "Hakkında" dahil dolu bir tanıtım.

## Karar

**Koyu tema varsayılan, açık tema bir düğme uzaklıkta.** Renkler yalnızca
değişken olarak tanımlı (`:root`, `[data-theme="light"]`); bileşenlerde sabit
renk yok. Tema değişince hiçbir ekran yeniden çizilmez. Seçim tarayıcıda
saklanır ve sayfa çizilmeden önce uygulanır (yanıp sönme olmaz).

**Ana ekran, yeni uç eklemeden önce var olan veriyi gösterir.** Özet tek istekle
gelir (ADR 0015'teki uç genişletildi: son 7 gün, biten kart sayısı, canlı
panolar, ekip, pano sağlığı, yaşlanan işler). Veri gelmezse ekran bozulmaz; her
kutu kendi boş hâlini söyler. Gerçek günlük verisi olmayan sayaçlara süs olsun
diye eğri çizilmez — tasarımdaki dört küçük grafikten yalnızca "son 7 günde
hareket" gerçek veriyle çizilir, diğerleri ikondur.

**Komut paleti (Ctrl K / ⌘ K).** Panolar bellekteki listeden anında, kartlar
`GET /api/search` ile (ADR 0016), komutlar aynı listede. Ok tuşları ve Enter ile
kullanılır; geç gelen eski sonuç atılır. Tek tuşlu kısayollar (N yeni kart,
G H ana ekran) yalnızca bir alana yazılmıyorken çalışır; yoksa kart başlığına "n"
yazan her seferinde yeni kart açardı.

**Pano görünümleri sekme oldu: Pano · Geçmiş · Akış.** Geçmiş ayrı bir ekran
değil, panonun salt okunur geçmiş hâlidir (zaman çubuğu). Akış, yan paneldeki
ölçümün geniş ve açıklamalı hâlidir. Davet, yan paneldeki satırdan üst çubuktaki
bir pencereye taşındı; ana ekrandan da açılır (yöneticisi olunan pano seçilir).

**"Geciken" tek anlam taşır.** Süzgeç, sayaç ve özet aynı kuralı kullanır: son
tarihi geçmiş ve son kolonda (bitmiş) olmayan kart. Önceki süzgeç bugün/yarın
bitenleri de "geciken" sayıyordu; etiket ile davranış ayrışıyordu.

**Kolon taşıma diğer kolonları kaydırır (sunucu düzeltmesi).** Yeni ekranlar
"son kolon = bitti" kuralına daha çok dayanıyor. Kolon taşınınca yalnızca
taşınanın numarası değişiyordu; iki kolon aynı sıraya düşüyor, özet bir kartı
bitmiş, pano ise açık sayıyordu. Artık sıra 0..n-1 boşluksuz yeniden numaralanır.

## Sonuçlar

- (+) Giriş sonrası ilk ekran "ne bekliyor, kim burada, nerede tıkanıyor"
  sorularını tek bakışta cevaplıyor.
- (+) Tema ve yeni bileşenler aynı değişkenleri kullandığı için ileride bir renk
  değişikliği tek yerden yapılır.
- (−) `index.html` büyüdü (~4000 satır). Çerçevesiz kalma kararı (ADR 0001'den
  beri) bilinçli; bölünme ihtiyacı doğarsa ilk aday ana ekran ve palet kodu.
- (−) Yeni panoların varsayılan kolon adları hâlâ İngilizce (To Do, In Progress,
  Done); Türkçeleştirmek sunucu ve testlerde ayrı bir değişiklik.
