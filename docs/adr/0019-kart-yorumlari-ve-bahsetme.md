# ADR 0019 — Kart yorumları ve @bahsetme

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-24

---

## Bağlam

Trello ile karşılaştırmada en büyük eksik buydu ([RAKIP-KARSILASTIRMASI](../RAKIP-KARSILASTIRMASI.md)):
kart üzerinde konuşulamıyordu. "Bunu kim yapıyor?", "taslağa baktın mı?" gibi
sorular panonun dışında kalıyor, karta bakan biri ne konuşulduğunu göremiyordu.

İstenen: kartın altında yorumlar; birini `@` ile anınca ona uygulama içinde ve
e-postayla haber; hepsi sayfa yenilemeden. Cevaplanması gereken üç soru vardı:
yorum kartın operasyon modeline girer mi, kim yazabilir, bahsetme bir posta
yağdırma aracına dönüşmesin diye ne yapılır.

## Karar

### 1. Yorum bir kart operasyonu değil

Kart operasyonları sürümlüdür ([ADR 0001](0001-operasyon-tabanli-model-ve-cakisma-cozumu.md)):
bayat sürümle gelen taşıma reddedilir. Yorum bu modele girmez. Ayrı bir tabloda
(`comments`, anılanlar `comment_mentions`) durur, REST'ten yazılır ve kartın
`version` alanına dokunmaz. Girseydi, biri yorum yazdığı anda kartı taşıyan
kişinin işlemi bayat sayılıp reddedilirdi ([ADR 0003](0003-cakisma-cozumu-versiyon-kontrolu.md)).
Yorumlar arasında çakışma da yok: her yorum ayrı satır, bir yorumu yalnızca
yazanı düzenler.

Canlı yayın yine pano kanalından (`/topic/board.{id}`): `COMMENT_ADDED`,
`COMMENT_EDITED`, `COMMENT_DELETED`. Her olay kartın yeni yorum sayısını taşır;
arayüz sayıyı kendisi saymaz, kaçırdığı bir olay yüzünden sayı kaymaz.
Panonun fotoğrafı (`GET /api/boards/{id}`) her karta `commentCount` ekler; sayılar
tek gruplu sorguyla gelir.

### 2. Olay kaydında yalnızca bilgi

Yeni yorum `ADD_COMMENT` türüyle olay kaydına yazılır (kart ve yorum kimliğiyle):
"Son hareketler"de ve ana ekranda "X, 'Kart' kartına yorum yazdı" olarak görünür.
Zaman yolculuğu artık yalnızca panonun hâlini değiştiren türleri **açık bir
listeyle** oynatır ve kaydırıcıda gösterir; yorum bir "an" değildir, o anki pano
bir öncekinin aynısı olurdu. Akış ölçümü zaten yalnızca kart ekleme, taşıma ve
silmeyi okuyor ([ADR 0006](0006-olay-kaydi-uzerinden-gecmis-ve-akis.md)).
Düzenleme ve silme kaydedilmez; listeyi gürültüyle doldururdu.

Yorumlar geçmiş görünümünde gösterilmez; orada kartların yorum sayısı 0'dır.

### 3. Yetki

| İşlem | Kim |
|---|---|
| Okumak, yazmak | Panonun her üyesi, **izleyici dahil** |
| Düzenlemek | Yalnızca yazan |
| Silmek | Yazan ya da panonun yöneticisi (`OWNER`) |

İzleyici de yazar: yorum panoyu değiştirmez ve izleyicinin en doğal ihtiyacı soru
sormaktır (Trello'da da "observer" yorum yazar). Yönetici başkasının yorumunu
silebilir ama düzenleyemez; birinin ağzına söylemediği bir sözü koymak olurdu.

Üye olmayan, panonun diğer uçlarıyla aynı `403`'ü alır; bu kontrol karttan önce
yapılır. Üyenin istediği kart o panoda değilse `404`: başka panodaki kart ile hiç
olmayan kart aynı cevabı alır. Kurallar `BoardAccessService`'ten gelir, yeniden
yazılmadı.

### 4. Bahsetme

Kimin anıldığı metinden değil, istekteki `mentions` (kullanıcı id listesi)
alanından okunur. Metindeki "@Ayşe Yılmaz" aynı adlı iki kişiyi ayırt edemez.
Arayüz `@` yazılınca panonun üyelerini açar, seçilen kişinin adını metne yazar,
id'sini gönderir.

Sunucu listeyi süzer: panoya erişemeyenler ve yazanın kendisi **sessizce
düşülür**, 20'den fazla kişi `400` alır. Hata yerine düşürmek: istemcinin üye
listesi eski olabilir (biri az önce çıkarılmış), bu yüzden bütün yorumu
reddetmek yanlış olurdu. Kimin erişebildiğini yine `BoardAccessService` söyler.

Anılan kişiye:

- **Zil bildirimi**, kişisel kanaldan canlı ([ADR 0012](0012-onayli-davet-ve-kisisel-canli-bildirim.md)).
  Pano adı, kart başlığı ve kart kimliği taşır; tıklanınca kart açılır.
- **E-posta**: kim, hangi kart, yorumun ilk 200 karakteri ve kartı açan bağlantı
  (`<APP_BASE_URL>/?board=<id>&card=<id>`). İşlem kaydedildikten sonra gider;
  gidemezse yalnızca loglanır, yorum geri alınmaz.

**Bir yorumda bir kişiye bir posta.** Düzenleme yalnızca o yorumda ilk kez anılan
kişilere haber verir; çıkarılıp yeniden eklenen kişi ikinci postayı almaz. Garanti
veritabanında: `notifications (user_id, comment_id) WHERE type = 'COMMENT_MENTION'`
üzerinde tekil indeks.

Karta atanmış kişi, kartına yazılan **yeni** yoruma uygulama içinde haber alır
(posta yok). Yorumu kendisi yazdıysa ya da yorumda zaten anıldıysa almaz; aynı
olay için iki bildirim gitmez.

### 5. Hız sınırı

Kişi başına dakikada 10, saatte 100 yorum; yazma ve düzenleme aynı kovadan harcar
(düzenleme de yeni kişi anıp posta gönderebilir). Aşan `429` ve `Retry-After`
alır. Sohbet eden bir insan bu sınıra takılmaz; ama her yorum 20 kişiye posta
gönderebildiği için sınırsız bırakılamazdı.

`RateLimitPolicy` kullanılmadı: o kimliksiz uçlar içindir, IP başına sayar ve
isteği kimlik doğrulanmadan keser. Burada kişi belli; aynı ofisten yazan iki
kişinin birbirinin kotasını yemesi için sebep yok. Kovalar yine bellekte
([ADR 0010](0010-canliya-cikis-oncesi-guvenlik.md)'daki sınır geçerli).

### 6. Arayüz

Kart penceresinin altında "Yorumlar": eskiden yeniye, yazma kutusu en altta,
Ctrl+Enter (Mac'te ⌘+Enter) ile gönderilir. Canlı olaylar listeyi **tek tek**
günceller, baştan çizmez: başkasının yeni yorumu yazılmakta olan yorumu ya da açık
bir düzenlemeyi silmez. Gönderilmemiş yorum pencere kapansa da o oturumda kalır.
Metin kaçışlanır; yalnızca `http(s)` bağlantılar tıklanır (`rel="noopener noreferrer"`).
Postadaki bağlantı oturum yoksa girişten sonra açılır; hedef sekmede saklanır,
çünkü Google girişi sayfadan ayrılıp geri dönüyor.

## Sonuçlar

**Kazandıklarımız**
- Kart üzerinde konuşulabiliyor; ilgilisine anında ve postayla haber gidiyor.
- Kart operasyonları, sürüm kontrolü, zaman yolculuğu ve akış ölçümü değişmedi
  (testle sabit).
- Bahsetme spam'e kapalı: erişimi olmayan anılamaz, kişi başına bir posta, hız sınırı.

**Feda ettiklerimiz / bilinen sınırlar**
- Yorumun eski hâli tutulmuyor; "(düzenlendi)" yalnızca bir damga.
- Bildirim tercihi yok: anılan herkese posta gider, kapatılamaz. Kişinin
  postaları seçebilmesi ayrı bir iş (karşılaştırmadaki 5. madde).
- Kişi adını değiştirirse eski yorumlardaki "@Eski Ad" vurgusu kaybolur; kimin
  anıldığı kayıtlı kaldığı için bildirim etkilenmez.
- Yorum sayısı panonun fotoğrafında; yeni kart olayı ve geçmiş görünümü 0 taşır,
  sayıyı canlı olaylar günceller.
- Hız sınırı sayaçları bellekte: iki sunucuda etkin sınır iki katına çıkar.
