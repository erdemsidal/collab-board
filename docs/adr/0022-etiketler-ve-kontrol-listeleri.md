# ADR 0022 — Etiketler ve kontrol listeleri

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-26

---

## Bağlam

Trello ile karşılaştırmada yorumlardan sonraki iki eksik bunlardı
([RAKIP-KARSILASTIRMASI](../RAKIP-KARSILASTIRMASI.md)): kartı bir bakışta
sınıflandıracak **etiket** ("Acil", "Hata", "Tasarım") ve kartın içindeki işi
adım adım izleyecek **kontrol listesi** ("Yayın öncesi: 3/5").

İstenen: her panonun kendi etiketleri, karta etiket takıp çıkarma, etikete göre
süzme; kartta bir ya da daha çok adlandırılmış liste, maddeleri işaretleme,
düzenleme, sıralama; ikisi de herkese anında. Cevaplanacak sorular: değişiklik
hangi kanaldan gider, iki kişi aynı listeyi aynı anda değiştirirse ne olur,
zaman yolculuğu bunları gösterir mi.

## Seçenekler: hangi kanal?

Projede iki gerçek zamanlı desen var:

1. **Kart operasyonu** (`BoardOperation`, `/app/board/{id}/ops`): sürümlü, sealed
   tipler, `OperationScopeGuard` hedefi denetler, sonuç pano kanalına yayınlanır
   ([ADR 0001](0001-operasyon-tabanli-model-ve-cakisma-cozumu.md)).
2. **REST + canlı olay** (yorumlar, [ADR 0019](0019-kart-yorumlari-ve-bahsetme.md)):
   istek REST'ten gider, sunucu işlem kaydedildikten sonra pano kanalına olay yayınlar.

**Seçilen: REST + canlı olay.** Gerekçeler:

- **Kartın sürümüne girmemeli.** Kart operasyonları `baseVersion` taşır; etiket
  takmak ya da madde işaretlemek kartın sürümünü artırsaydı, aynı anda kartı
  taşıyan kişinin işlemi bayat sayılıp reddedilirdi ([ADR 0003](0003-cakisma-cozumu-versiyon-kontrolu.md)).
  Yorumlar da aynı sebeple ayrı tutuldu. `EDIT_CARD` alanların tamamını taşır
  (son yazan kazanır); etiketler onun içinde olsaydı başlığı düzelten kişi
  başkasının az önce taktığı etiketi silerdi.
- **İstek bir cevap bekliyor.** Yeni etiketin, yeni maddenin kimliği; "30
  etiketten fazla olmaz", "madde 300 karakteri geçemez" gibi hatalar. REST bunları
  durum koduyla ve kişinin dilinde döner; canlı kanalda ret yalnızca ayrı bir
  kuyruğa düşen bir mesaj olurdu.
- **Çakışma başka türlü çözülüyor.** Etiket bir kümedir, liste ise satır kilidiyle
  korunur (aşağıda). Sürüm numarası bu ikisine bir şey katmaz.

`OperationScopeGuard`'ın çözdüğü sorun burada da var: adresteki pano ile hedefin
(kart, etiket, liste, madde) panosu aynı olmalı. Kural yeniden yazılmadı;
`OperationScopeGuard.requireCardOnBoard` aynı denetimi REST uçlarına açar.

## Karar

### 1. Veri (V21)

| Tablo | Ne tutar |
|---|---|
| `board_labels` | panonun etiketleri: renk (8 renklik palet) ve isteğe bağlı ad (≤ 40) |
| `card_labels` | kart ↔ etiket kümesi, birincil anahtar `(card_id, label_id)` |
| `checklists` | kartın listeleri: ad (≤ 100), `revision` |
| `checklist_items` | metin (≤ 300), yapıldı, sıra |

Kart silinince bağlar, listeler ve maddeler gider; etiket silinince bütün kartlardan
kalkar (hepsi `ON DELETE CASCADE`). Panoda aynı adlı iki etiket olmaz (büyük/küçük
harf farkı sayılmaz, `lower(name)` üzerinde benzersiz indeks); adsız etiketler serbest.

### 2. Uçlar ve yetki

| Uç | Kim |
|---|---|
| `POST/PATCH/DELETE /api/boards/{b}/labels[/{l}]` | editör, yönetici |
| `PUT/DELETE /api/boards/{b}/cards/{c}/labels/{l}` | editör, yönetici |
| `GET /api/boards/{b}/cards/{c}/checklists` | panonun her üyesi (izleyici dahil) |
| liste ve madde yazan uçlar | editör, yönetici |

Sıra her uçta aynı: önce rol (`BoardAccessService`: üye değil → 403, izleyici → 403),
sonra hedef. Kart başka panodaysa, etiket başka panonunsa, liste başka kartınsa,
madde başka listedeyse **404**: "başka yerde var" ile "hiç yok" aynı cevabı alır.

### 3. Eşzamanlılık

**Etiketler bir küme.** Takmak `INSERT ... ON CONFLICT DO NOTHING`, çıkarmak
`DELETE`; ikisi de tekrarlanabilir. On kişi aynı etiketi aynı anda taksa tek satır,
tek geçmiş satırı, tek olay olur (testle sabit). Olay "şu an hangileri takılı"
değil "şu takıldı / şu çıkarıldı" der: iki kişi farklı etiketleri aynı anda
taktığında iki olay da birbirini ezmeden uygulanır.

Etiket takımındaki değişiklikler (ekleme, ad, renk, silme) panonun satırını kilitler
(`SELECT ... FOR NO KEY UPDATE`): "en fazla 30" ve "aynı ad olmaz" aynı anda gelen
isteklerde de doğru kalır. `NO KEY` kilit, panoya satır ekleyen işleri (kolon
eklemek gibi) bekletmez.

**Listeler satır kilidiyle.** Listeye dokunan her istek önce listenin satırını
kilitler (`PESSIMISTIC_WRITE`), sonra okur ve yazar. Aynı listeye gelen ikinci istek
bekler, ilkinin sonucunu görür. Sonuçları:

- Sıra her zaman 0..n-1, boşluksuz ve tekil. Taşıma "şu maddeyi şu yere koy"dur,
  diğerleri kayar. Veritabanı da denetler: `(checklist_id, position)` benzersiz,
  işlem sonunda (`DEFERRABLE`) bakılır, çünkü yeniden numaralandırma satırları tek
  tek günceller.
- İşaretleme bir atamadır (`done: true`), değiştir-tersine-çevir değil: iki kişi
  aynı maddeye aynı anda tıklasa da madde işaretli kalır.
- "Son açık madde işaretlendi, liste tamamlandı" tek bir istekte görülür; geçmişe
  bir kez yazılır (on maddeyi aynı anda iki kez işaretleyen yirmi istek, tek kayıt;
  testle sabit).
- Her değişiklik `revision`'ı bir artırır; sayı işlemlerin yazılma sırasıyla aynı.

Yeni liste eklenirken kartın satırı kilitlenir ("kartta en fazla 10 liste").

**Arayüz eski hâli atar.** Her yanıt ve her canlı olay listenin **tamamını**
revision ile taşır. Arayüz ne değiştiğini yorumlamaz, elindekini değiştirir;
gelen hâlin revision'ı elindekinden küçükse atar. Yayınlar ağda sırasını
kaybederse ya da geç gelirse yeni hâl ezilmez. Silinen listenin kimliği
hatırlanır, geç gelen bir olay onu geri getirmez. Bedeli: liste en fazla 100
madde, olay en kötü ihtimalle ~30 KB.

Kartın üstündeki "3/5" kart başına tek sayı değil, liste başına özettir (yapılan,
toplam, revision); arayüz toplar. Kart başına tek sayı tutulsaydı iki farklı
listede aynı anda işaretlenen iki madde birbirinin sayısını ezebilirdi.

### 4. Sınırlar

| | En fazla |
|---|---|
| Panoda etiket | 30 |
| Etiket adı | 40 karakter |
| Kartta liste | 10 |
| Liste adı | 100 karakter |
| Listede madde | 100 |
| Madde | 300 karakter |

Aşan istek `400`, aynı ad `409`. Hız sınırı konmadı: bu uçlar posta göndermiyor
(yorumdaki gerekçe yok) ve yukarıdaki sınırlar büyümeyi zaten durduruyor.

### 5. Panonun fotoğrafı

`GET /api/boards/{id}` artık panonun etiket takımını (`labels`) ve her kartta
`labelIds` ile `checklists` özetini taşır. Her biri pano başına **tek sorgu**:
etiketler, kart-etiket bağları, liste özetleri (gruplu sorgu, `COUNT ... FILTER`).
Testte Hibernate'in hazırladığı ifade sayısı 1 kartlı panoda ve etiketli,
listeli 9 kartlı panoda aynı. Maddelerin kendisi fotoğrafa girmez; kart penceresi
açılınca iki sorguyla gelir (listeler, sonra bütün maddeleri).

Not: kolonların kartları zaten kolon başına bir sorguyla yükleniyor (bu ADR'den
önce de böyleydi); bu sayı kolon sayısıyla büyür, kart sayısıyla değil.

### 6. Olay kaydı

Yalnızca anlamlı anlar yazılır (`ActivityService`, parçalarıyla, [ADR 0020](0020-turkce-ve-ingilizce.md)):

| Tür | Ne zaman | Parçalar |
|---|---|---|
| `ADD_LABEL` / `REMOVE_LABEL` | karta etiket takıldı / çıkarıldı | kart, etiket adı, renk |
| `DELETE_LABEL` | etiket silindi | etiket adı, renk, kaç kartta olduğu |
| `ADD_CHECKLIST` | karta liste eklendi | kart, liste |
| `COMPLETE_CHECKLIST` | son açık madde işaretlendi | kart, liste |
| `DELETE_CHECKLIST` | liste silindi | kart, liste |

Yazılmayanlar: etiket oluşturmak, adını ya da rengini değiştirmek (ayar), madde
eklemek, işaretlemek, düzenlemek, sıralamak. Her işaretleme yazılsaydı "Son
hareketler" okunmaz olurdu. Adsız etiket cümlede rengiyle geçer: "'Ödeme' kartına
yeşil etiketi ekledi".

### 7. Zaman yolculuğu

**Etiketler oynatılır.** Takma, çıkarma ve silme her seferinde kaydedildiği için
geçmişteki bir anda kartın hangi etiketleri taşıdığı kesin olarak bilinir; bu üç tür
`REPLAYABLE` listesinde, kaydırıcıda birer an. Geçmiş görünümünün etiket takımı
bugünkü etiketler ile sonradan silinenlerdir: silinen etiketin adı ve rengi silme
kaydında duruyor (silinen kolon gibi, [ADR 0008](0008-kolon-yasam-dongusu-ve-gecmis-tohumlamasi.md)).

**Listeler oynatılmaz.** İşaretlemeler kayda yazılmadığı için o anki ilerleme
bilinemez; yarım bir doğruyu göstermek yerine geçmiş görünümünde kartlar listesiz
görünür ve kart penceresinde liste bölümü yoktur (yorumlar gibi). Liste kayıtları
"Son hareketler"de görünür ama kaydırıcıda an değildir. Akış ölçümü yalnızca kart
olaylarını okuduğu için etkilenmez (testle sabit).

### 8. Renkler

Sekiz renk, iki temada da aynı: bir renk her ekranda aynı anlama gelir.

| Renk | Kod | L* | Yazı | Kontrast |
|---|---|---|---|---|
| Sarı | `#F7DC5C` | 88 | koyu | 14.3:1 |
| Açık mavi | `#8AD3F2` | 81 | koyu | 11.8:1 |
| Yeşil | `#5CC88A` | 73 | koyu | 9.4:1 |
| Turuncu | `#E6852F` | 65 | koyu | 7.2:1 |
| Pembe | `#D25A92` | 55 | koyu | 5.2:1 |
| Kırmızı | `#C83A34` | 46 | beyaz | 5.1:1 |
| Mor | `#673BBA` | 37 | beyaz | 7.2:1 |
| Mavi | `#1D438C` | 30 | beyaz | 9.4:1 |

- Her etiketin yazısı en az 4.5:1.
- Renkler yalnızca tonla ayrılmaz: açıklıkları 7-10 L* aralıklı, renk ayırt
  edemeyen biri de iki etiketi gri tonundan ayırır. Seçicide her rengin adı var
  (ipucu ve ekran okuyucu), seçili renk halkanın yanında bir işaretle de belli.
- Tek palet her iki zeminde de yüksek kontrastlı olamaz (sarı beyaz kartta, lacivert
  koyu kartta silikleşir). Bu yüzden her çipin çevresinde temaya göre ince bir halka
  var (`--lb-ring`).

### 9. Arayüz

- **Kartın yüzü:** başlığın üstünde etiketler; adlı etiket küçük bir çip, adsız
  etiket kısa bir renk çubuğu. Alt satırda liste ilerlemesi "3/5", hepsi bitince yeşil.
- **Kart penceresi:** başlığın altında etiket satırı ve "+" ile açılan seçici:
  arama, onay kutuları, düzenle düğmesi, yazılan ad yoksa "“…” etiketini oluştur".
  Oluşturma ve düzenleme seçicinin içinde açılır, kart penceresi kapanmaz; silme
  satır içinde onay ister. Altında "Kontrol listeleri": liste başına ilerleme
  çubuğu, "Tamamlananları gizle", maddeye tıklayınca yerinde düzenleme, tutamaçtan
  sürükleme ya da ↑/↓ düğmeleri ve Alt+↑/↓ ile sıralama, onaylı liste silme.
- **Süzgeç çubuğu:** "Etiket" düğmesi aynı seçiciyi süzgeç olarak açar; seçilen
  etiketlerden herhangi biri kartta olmalı, "Etiketsiz kartlar" da seçilebilir.
  Düzenleyen kişi etiket takımını buradan da yönetir.
- **Ctrl K:** açık panoda etiket adı da aranır ("acil" yazınca Acil etiketli kartlar).
- Canlı olaylar panoyu baştan çizmez: yalnızca o kart, açık pencere ve seçici
  güncellenir; yazılmakta olan madde ya da açık bir düzenleme kaybolmaz.

## Sonuçlar

**Kazandıklarımız**
- Kartlar sınıflandırılıyor ve etikete göre süzülüyor; kartın içindeki iş adım adım izleniyor.
- Aynı anda çalışan kişiler sırayı bozamıyor, işaretlemeler kaybolmuyor (kilit + revision).
- Kart operasyonları, sürüm kontrolü ve akış ölçümü değişmedi; zaman yolculuğu
  etiketleri o anki hâliyle gösteriyor.

**Feda ettiklerimiz / bilinen sınırlar**
- Geçmiş görünümünde kontrol listeleri yok; adı ya da rengi sonradan değişen
  etiket geçmişte bugünkü hâliyle görünür (ad değişikliği kaydedilmiyor).
- Listelerin kendi sırası değiştirilemiyor; eklenme sırasıyla durur.
- Etiket süzgeci "herhangi biri" demek; "hepsi birden" seçeneği yok.
- Ctrl K'nın sunucu araması etiket adını bilmiyor; etiket araması yalnızca açık panoda.
- "Tamamlananları gizle" seçimi tarayıcı oturumunda tutulur, hesaba yazılmaz.
- Liste olayı listenin tamamını taşır (en kötü ~30 KB); çok büyük listelerde
  madde başına olaya geçilmeli.
