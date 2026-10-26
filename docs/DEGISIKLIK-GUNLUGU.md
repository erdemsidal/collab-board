# Değişiklik günlüğü

CollabBoard'da neyin, ne zaman ve neden değiştiğinin sade dille kaydı. **En yeni en üstte.**
Her kayıtta üç soru var:

- **Ne değişti** — kullanıcının ya da geliştiricinin fark edeceği şey.
- **Neden** — hangi sorunu çözüyor.
- **Nasıl görülür / denenir** — kendi gözünle doğrulamanın yolu.

Teknik ayrıntı bağlantılı PR'da ve ADR'de. Saatler Türkiye saatidir (UTC+3).
`main` dalı = canlı site: bir PR birleştiği an Railway onu yayına alır (bkz.
[rehber/github-ve-canliya-alma.pdf](rehber/github-ve-canliya-alma.pdf), [CANLIYA-ALMA.md](CANLIYA-ALMA.md)).

---

## Yayında olmayan / geliştirilmekte

> Bu bölüm `main`'e henüz girmemiş, yani **canlıda olmayan** işler içindir.
> İş bir PR ile birleşince kaydını aşağıya, tarihli bölüme taşı.

Önerilen birleştirme sırası: **#7 → #9 → #8**. #9, #7'nin üzerine kurulu; #8 yalnızca belge.

### Canlı imleçler ve akıcı kart taşıma — PR #7

- **Ne değişti:**
  - Panodaki herkes diğerlerinin imlecini isim etiketiyle görüyor.
  - Biri bir kartı tutunca diğerlerinde o kart soluklaşıyor, kartın adı da imlecin altında taşınıyor.
  - Karşı taraf bir kartı taşıyınca kart yeni yerine **kayarak** gidiyor, pano titremiyor.
  - Sürüklerken gelen değişiklikler bekletiliyor, sürükleme bozulmuyor.
  - Sürüklenen kart eğik ve gölgeli, bırakılacak yer kesik çizgiyle gösteriliyor.
  - Telefonda karta kısa basılı tutunca sürükleme başlıyor.
- **Neden:**
  - Her canlı değişiklikte bütün pano yeniden çiziliyordu; bütün kartlar yeniden beliriyor, taşınan kart ışınlanıyordu.
  - Biri kartı taşırken diğerleri bunu ancak bırakılınca görüyordu.
- **Nasıl görülür:** İki tarayıcıda aynı panoyu aç (biri gizli pencere). Birinde fareyi gezdir, öbüründe imleç belirir. Bir kartı sürükle, öbüründe kart soluklaşır ve bırakınca kayarak yerine gider.
- Ayrıntı: [ADR 0014](adr/0014-canli-imlec-ve-akici-tasima.md). Yol üstünde "Yaşlanan iş" listesinde kart adlarının görünmemesi de düzeltildi.

### Tanıtım sayfası ve ana ekran özeti — PR #9

- **Ne değişti:**
  - Oturumu olmayan ziyaretçi önce canlı demo panolu bir **tanıtım sayfası** görüyor.
  - Giriş yapınca **ana ekranın üstünde bir özet** var: selam, dört kutu (panolar, bana atanan açık kartlar, gecikenler, son 7 günde hareket), "Bana atanan kartlar" ve tüm panolardaki "Son hareketler".
- **Neden:**
  - Ziyaretçi ürünü görmeden doğrudan giriş formuna düşüyordu.
  - Ana ekran yalnızca pano kartlarından oluşuyordu ve boş görünüyordu.
- **Nasıl görülür:**
  - Çıkış yap ya da gizli pencerede siteyi aç: tanıtım sayfası gelir.
  - Giriş yap: kartları kendine ata, son tarih ver; ana ekranda özet dolar.
- Ayrıntı: [ADR 0015](adr/0015-tanitim-sayfasi-ve-ana-ekran-ozeti.md).

### GitHub ve canlıya alma rehberi + bu günlük — PR #8

- **Ne değişti:**
  - [rehber/github-ve-canliya-alma.pdf](rehber/github-ve-canliya-alma.pdf) eklendi: 30 sayfa; dal, commit, PR, CI, birleştirme ve Railway'in otomatik yayını, gerçek ekran görüntüleriyle.
  - Bu değişiklik günlüğü başladı.
- **Neden:** GitHub'da yaptığımız işlemler yeterince anlaşılmıyordu.
- **Nasıl görülür:** PDF'i aç. Son sayfada "Değişikliği canlıya alma — 10 adım" kopya kâğıdı var.

---

## 2026-10-23

### Otomatik yayın bağlandı (ayar değişikliği, kod yok)

- **Ne değişti:** GitHub hesabına **Railway App** kuruldu ve `collab-board` deposuna
  erişim verildi. Railway'de `collab-board` servisinde **Auto deploys when pushed to
  GitHub** ve **Wait for CI** açıldı. Artık `main`'e giren her commit, CI yeşil
  bittikten sonra kendiliğinden yayına çıkıyor.
- **Neden:** 20 Ekim'deki birleştirme Railway'de yayın başlatmamıştı; elle
  "Deploy Latest Commit" gerekmişti. Sebep: Railway, GitHub'dan "yeni kod var" haberini
  alamıyordu — GitHub'da bu depo için tek bir Deployment kaydı bile yoktu.
- **Nasıl görülür:** Bir PR birleşince Railway → `collab-board` → **Deployments**'ta en
  üstte yeni satır "Waiting for CI" ile belirir, CI bitince ACTIVE olur. GitHub'da
  Actions sekmesinin sol altındaki **Deployments** bağlantısında kayıt oluşur.
  İlk örnek PR #6: birleştirme 20:39, CI 20:45'te bitti, yeni sürüm 20:46'da canlı.

### PR #6 — Sağlamlaştırma: olmayan adres 404, canlıda metrikler kapalı, testler `.env`'den yalıtıldı

Birleşti: 20:39 · merge commit `0db46b9` · [PR #6](https://github.com/erdemsidal/collab-board/pull/6)

**Olmayan adresler 500 yerine 404**
- **Ne değişti:** Tarayıcının kendiliğinden istediği `/favicon.ico` ve canlıda kapalı olan
  Swagger artık 404 dönüyor; loga hata değil, yalnızca debug satırı düşüyor.
- **Neden:** Her sayfa açılışında loga bir "beklenmeyen hata" yazılıyor, gerçek hatalar
  bu gürültünün altında kayboluyordu.
- **Nasıl denenir:** Canlıda `https://<site>/favicon.ico` → 404. Railway loglarında sayfa
  açılışlarıyla birlikte `level_value: 40000` (ERROR) satırı çıkmamalı.

**Canlıda iç metrikler kapalı**
- **Ne değişti:** `prod` profilinde dışarıya yalnızca `/actuator/health` açık
  (`application-prod.yml`). Yerelde metrikler açık kalıyor.
- **Neden:** `/actuator/metrics` canlıda herkese açıktı: bellek, istek sayıları, sürümler.
- **Nasıl denenir:** `https://<site>/actuator/metrics` → 404,
  `https://<site>/actuator/health` → `{"status":"UP"}`.

**Testler geliştiricinin `.env`'ini okumuyor**
- **Ne değişti:** Testlere özel `src/test/resources/.env.properties`, `spring-dotenv`'e var
  olmayan bir dosya adı veriyor; test tabanı posta yolunu ayrıca `smtp`'ye sabitliyor.
  `TestIsolationIntegrationTest` sızıntı geri gelirse kırılıyor. `.gitignore`'a bu dosya
  için istisna eklendi (içinde sır yok).
- **Neden:** `.env`'e `MAIL_PROVIDER=brevo` yazılınca testler sahte posta kutusu yerine
  gerçek Brevo'ya yöneldi ve yerelde 11 test kırıldı. CI'da `.env` olmadığı için CI yeşil
  kaldı. (Brevo raporuna göre gerçekte posta gitmedi.)
- **Nasıl denenir:** Yerelde `.env`'de `MAIL_PROVIDER=brevo` dursa bile `./mvnw test` geçer
  (Docker açık olmalı). Toplam 96 test: 92 entegrasyon + 4 birim.

**Belgeler**
- [CANLIYA-ALMA.md](CANLIYA-ALMA.md) Railway CLI yerine **web paneli** adımlarıyla yeniden
  yazıldı — CLI bu bilgisayarda Windows Akıllı Uygulama Denetimi tarafından engelleniyor.
  "main'e birleştirdim ama yayın başlamadı" sorunu sorun tablosuna eklendi.
- README'deki metrik bölümü düzeltildi: arayüzün alt şeridindeki sayılar sunucudan değil,
  tarayıcının kendi sayaçlarından geliyor.

---

## 2026-10-20 — PR #3, #4 ve #5 birlikte canlıya çıktı

Birleşti: 18:35 · tek merge commit `5c9f8a5`

Üç PR üst üste kurulmuştu (#4'ün dalı #3'ün, #5'in dalı #4'ün üstünde). #5 birleşince
dokuz commit'in hepsi `main`'e girdi; GitHub #3'ü ve #4'ü de kendiliğinden "merged"
işaretledi — bu yüzden geçmişte onlar için ayrı merge commit yok. CollabBoard bu sürümle
Railway'de ilk kez gerçek kullanıcıya açıldı; yayın o gün elle başlatıldı (otomatik
yayın 23 Ekim'de bağlandı).

### PR #5 — Canlıda posta: Brevo HTTPS API ve canlıya alma rehberi

[PR #5](https://github.com/erdemsidal/collab-board/pull/5) · açıldı 17 Ekim

- **Ne değişti:**
  - `MAIL_PROVIDER` postanın nasıl çıkacağını seçiyor: `smtp` (varsayılan, yerelde Gmail)
    ya da `brevo` (Brevo'nun HTTPS API'si, 443 portu).
  - Brevo modunda uygulama açılışta anahtarı, IP kısıtlamasını ve gönderen adresini
    sınıyor; sorun varsa logda kutu içinde ne yapılacağını yazıyor.
  - Canlı loglarda uygulanan migration'lar görünüyor.
  - Yeni belgeler: [CANLIYA-ALMA.md](CANLIYA-ALMA.md), [MAIL-KURULUMU.md](MAIL-KURULUMU.md)'nin Brevo bölümü.
- **Neden:** Railway'in Trial ve Hobby planları SMTP portlarını (25, 465, 587) kapatıyor.
  Canlıda doğrulama postası gitmediği için kimse kaydını tamamlayıp giriş yapamıyordu.
- **Nasıl denenir:** Canlıda gerçek bir adresle kaydol → doğrulama postası gelir (spam
  klasörüne de bak). Railway → View logs'ta `Brevo bağlantısı doğrulandı` ve
  `Brevo gönderen adresi onaylı` satırları.

### PR #4 — Onaylı pano daveti, canlı bildirimler, profil ve kesintisiz oturum

[PR #4](https://github.com/erdemsidal/collab-board/pull/4) · açıldı 14 Ekim · ADR [0012](adr/0012-onayli-davet-ve-kisisel-canli-bildirim.md), [0013](adr/0013-profil-ve-fotograf.md)

- **Ne değişti:**
  - **Onaylı davet:** yönetici kişiyi panoya doğrudan ekleyemiyor, davet ediyor; üyelik
    davet edilen **Kabul et** deyince başlıyor. Hesabı olmayan adrese kayıt bağlantılı
    posta gidiyor; kişi kaydolunca davet onu bekliyor. Yönetici bekleyen davetleri görüp
    geri çekebiliyor.
  - **Canlı bildirim:** davet ve yanıtı, kişi hangi ekranda olursa olsun anında düşüyor
    (sağ altta Kabul et / Reddet kutusu, zilde sayaç, bildirim paneli).
  - **Profil:** ad-soyad değiştirme (e-posta kilitli), şifre değiştirme (mevcut şifre şart,
    diğer cihazlardaki oturumlar kapanır), tarayıcıda kırpılan profil fotoğrafı
    (PNG/JPEG/WebP; tür dosyanın içeriğinden anlaşılır, SVG reddedilir).
  - **Kesintisiz oturum:** kullanıcıyı 15 dakikada oturumdan atan hata düzeldi (süresi
    dolan jetona sunucu 403 yerine 401 dönüyor); tarayıcı jetonu süresi dolmadan yeniliyor;
    oturum 30 gün ve her kullanımda yenileniyor; bir sekmede çıkış diğerlerini de çıkarıyor.
  - **Yol üstünde:** zamanlar saat dilimiyle gidiyor (az önceki işler "3 sa önce"
    görünüyordu); bilinmeyen alan ya da bozuk JSON 500 yerine 400 dönüyor; davet ucuna
    hız sınırı eklendi.
  - Veritabanı: `V13` davetler, `V14` bildirimler, `V15` profil fotoğrafları.
- **Neden:** Denemede panoya eklenen kişi bundan haberdar olmuyordu, rızası sorulmuyordu;
  menüdeki "Profilim" hiçbir yere gitmiyordu; kullanıcılar 15 dakikada bir atılıyordu.
- **Nasıl denenir:** İki ayrı oturum aç (biri gizli pencerede, farklı hesapla). Biri
  diğerini panoya davet etsin → diğerinin ekranına sayfa yenilenmeden kutu düşer →
  **Kabul et** → üye listesi iki tarafta da canlı güncellenir. Menü → **Profilim**'den
  fotoğraf yükle. Sayfayı 20 dakika açık bırak: oturum hâlâ açık olmalı.

### PR #3 — Kayıt formu adı ve soyadı boş gönderiyordu

[PR #3](https://github.com/erdemsidal/collab-board/pull/3) · açıldı 14 Ekim

- **Ne değişti:** Kayıt formu yazılanları kaybetmiyor: ad ve soyad sunucuya dolu gidiyor,
  hata sonrası form silinmiyor; örnek yazılar nötr ("Adın", "Soyadın"); düğme "Kayıt ol".
  Ayrıca yalnızca tek bir panoya davet edilmiş kişi o panoyu kenar çubuğunda
  **Benimle paylaşılanlar** altında görüyor.
- **Neden:** Bütün alanlar dolu olsa bile "Ad boş olamaz" hatası alınıyordu: form,
  "bekleniyor" göstergesi için yeniden çizildikten sonra okunuyordu. Otomatik testler API
  düzeyinde çalıştığı için hata fark edilmemişti. Tek panoya davet edilen kişi ise "0 pano"
  görüp kendisine verilen panoya ulaşamıyordu.
- **Nasıl denenir:** Arayüzden yeni bir hesapla kaydol → "Ad boş olamaz" çıkmamalı. Bir
  hesabı yalnızca bir panoya davet et → o hesapla girince kenar çubuğunda
  "Benimle paylaşılanlar" görünür.

---

## 2026-09-30

### PR #2 — README: zaman yolculuğu, akış sağlığı ve bir aylık işi güncelle

Birleşti: 21:21 · merge commit `3ee5fc6` (mesajı "collabboard readme" olarak değiştirilmişti) · [PR #2](https://github.com/erdemsidal/collab-board/pull/2)

- **Ne değişti:** README projeyi farklı kılan iki özellikle (zaman yolculuğu, akış sağlığı)
  açılıyor; özellikler gruplandı; "Tek kayıt, iki özellik" bölümü, demo, güncel API ve ADR
  tablosu, CI rozeti eklendi. Kod değişikliği yok.
- **Neden:** README teknik tarafı anlatıyor ama bu iki özellikten hiç bahsetmiyordu; depoya
  bakan biri iyi yapılmış bir Trello kopyası görüp asıl farkı kaçırırdı.
- **Nasıl görülür:** GitHub'da depo ana sayfasındaki README.

### PR #1 — WIP limiti, kolon yönetimi, e-posta doğrulama, şifre sıfırlama ve canlıya hazırlık

Birleşti: 06:05 · merge commit `c2c8ae7` · 11 commit (30 Ağustos – 27 Eylül) · [PR #1](https://github.com/erdemsidal/collab-board/pull/1)

- **Ne değişti:**
  - **Pano:** WIP limiti — dolan kolon yeni kart kabul etmiyor ([ADR 0007](adr/0007-wip-limiti-ve-kart-detaylari.md));
    kart detayları (açıklama, atanan, son tarih); kolon ekleme, adlandırma, silme — geçmiş
    bozulmadan ([ADR 0008](adr/0008-kolon-yasam-dongusu-ve-gecmis-tohumlamasi.md));
    "yaşlanan iş" listesi; arama ve süzme.
  - **Hesap:** e-posta doğrulama — hesap pasif açılır, postadaki bağlantıya tıklanınca
    etkinleşir ([ADR 0009](adr/0009-eposta-dogrulama.md)); şifre sıfırlama — 30 dakikalık
    tek kullanımlık bağlantı, sıfırlama bütün oturumları kapatır ([ADR 0011](adr/0011-sifre-sifirlama.md)).
  - **Güvenlik** ([ADR 0010](adr/0010-canliya-cikis-oncesi-guvenlik.md)): WebSocket origin listesi
    daraltıldı; depodaki açık JWT anahtarıyla `prod` profilinde uygulama açılmıyor; giriş,
    kayıt, doğrulama ve sıfırlama uçlarına IP başına hız sınırı; refresh token'lar artık
    loglara tam yazılmıyor.
  - **Altyapı:** CI düzeltildi (bkz. aşağıda); uygulama platformun verdiği `PORT`'u ve Redis
    parolasını okuyor; `docker compose up -d` artık uygulamayı da başlatıyor; `.env`
    çalışma anında okunuyor (`spring-dotenv`).
  - Veritabanı: `V10` kart detayları + WIP limiti, `V11` doğrulama jetonları, `V12` sıfırlama jetonları.
- **Neden:** Panoyu gerçek bir süreç aracına, hesapları gerçek kullanıcılara ve projeyi
  canlıya çıkmaya hazır hâle getirmek. CI ise o güne kadar **her** `main` çalışmasında
  kırmızıydı: `ci.yml`'deki `JWT_SECRET` geçerli Base64 değildi, uygulama tek test koşmadan
  açılamıyordu. Hiçbir şey yayınlamadan yeşil yanan sahte "deploy" adımı da kaldırıldı.
- **Nasıl denenir:** Kolon başlığındaki menüden **WIP limiti…** koy, kolonu doldur → yeni kart
  girmez. Yeni hesapla kaydol → doğrulama postası gelmeden giriş yapılamaz. Girişte
  "Şifremi unuttum" → postadaki bağlantıyla yeni şifre. GitHub → Actions: #31'den itibaren
  bütün çalışmalar yeşil.

---

## 2026-07-27 → 2026-08-03 — PR'sız dönem (doğrudan `main`'e)

Bu dönemde 30 commit doğrudan `main`'e yazıldı; PR açılmadı. Bu commit'lerin CI
çalışmalarının (#1–#30) hepsi kırmızı göründü — sebep kod değil, `ci.yml`'deki geçersiz
`JWT_SECRET`'tı (PR #1'de düzeldi).

### 2026-08-02 / 08-03 — Zaman yolculuğu ve akış sağlığı

`60ed913`, `9995eaf`, `17e03e6`, `89a9782` · [ADR 0006](adr/0006-olay-kaydi-uzerinden-gecmis-ve-akis.md)

- **Ne değişti:** Pano geçmişteki herhangi bir ana geri sarılabiliyor (salt okunur);
  kartların her kolonda ne kadar beklediği ve darboğaz ölçülüyor. Etkinlik akışında aynı
  kişinin art arda aynı türden işlemleri tek satırda toplanıyor; sayaca tıklayınca açılıyor.
- **Neden:** Sıradan Kanban araçlarının cevaplamadığı iki soru: "Geçen salı pano nasıldı?"
  ve "İşler nerede tıkanıyor?". İkisi de ayrı veri toplamadan, olay kaydından hesaplanıyor.
- **Nasıl denenir:** Panoda birkaç kart taşı → üst çubukta **Geçmiş** → alttaki kaydırıcıyı
  geri çek. Sağ panelde **Akış sağlığı**.

### 2026-08-01 — README ve kod yorumları

`fccb4fd`, `3f49208`

- **Ne değişti:** README çalışma alanları, roller ve yeni arayüzle güncellendi; kod
  yorumları "ne yapıyor" anlatımından "neden böyle" gerekçesine indirildi.
- **Neden:** README projenin geride bıraktığı bir hâli anlatıyordu.
- **Nasıl görülür:** README.

### 2026-07-31 — Roller, çalışma alanları ve yeni arayüz

`51ae05f`, `fbdf781`, `74434fe`, `3bcfccc`, `89fc915`, `d73acd4` · [WORKSPACE-PLANI.md](WORKSPACE-PLANI.md)

- **Ne değişti:** Panolara yalnızca üyeler erişiyor (OWNER / EDITOR / VIEWER). Panolar bir
  **çalışma alanına** ait; çalışma alanı üyeliği tüm panolarına erişim veriyor, pano
  düzeyindeki kayıt istisna olarak üstüne yazıyor. Tasarım teslimine göre arayüz baştan
  yazıldı; tasarım özeti (`FRONTEND-DESIGN-BRIEF.md` ve PDF'i) eklendi.
- **Neden:** Herkes her panoyu okuyup değiştirebiliyordu; ekibe yeni biri katıldığında her
  panoya tek tek davet etmek sürdürülemezdi.
- **Nasıl denenir:** Başka bir hesapla giriş yap → davet edilmediğin panolar listede
  görünmez. Kenar çubuğunda çalışma alanları, pano kartlarında kart ve üye sayıları.

### 2026-07-30 — Varlık, çok sunucu, kimlik doğrulama, geçmiş, testler

`3de75c7`, `f337c75`, `898edd0`, `00f7535`, `5e10caa`, `0bd712e`, `9a3be66`, `135be9c` · ADR [0004](adr/0004-cok-sunucuya-olcekleme-redis-pubsub.md), [0005](adr/0005-websocket-kimlik-dogrulama.md)

- **Ne değişti:** Panoda kimlerin çevrimiçi olduğu görünüyor; canlı senkron Redis Pub/Sub
  köprüsüyle birden fazla sunucuda çalışıyor; kullanıcılar giriş yapıyor ve WebSocket
  bağlantısı JWT ile doğrulanıyor; gerçek zamanlı katman Micrometer ile ölçülüyor; pano
  geçmişi ("kim, ne zaman, ne yaptı") tutuluyor; oturum refresh token ile sürüyor; ilk
  entegrasyon testleri (Testcontainers ile gerçek Postgres ve Redis) yazıldı; README
  baştan yazıldı.
- **Neden:** İki sunucu çalışınca bir sunucudaki işlem diğerine bağlı kullanıcılara hiç
  ulaşmıyordu; pano verisi herkese açıktı; her davranış elle deneniyordu.
- **Nasıl denenir:** İki ayrı oturumda (farklı hesaplarla) aynı panoyu aç → üst çubukta iki
  kişinin avatarı görünür; bir kartı taşı → diğerinde anında kayar. README'deki "İki sunucuyla ölçeklemeyi görmek" bölümü.

### 2026-07-29 — Çakışma çözümü ve sürükle-bırak

`07675c7`, `fd930e9` · [ADR 0003](adr/0003-cakisma-cozumu-versiyon-kontrolu.md)

- **Ne değişti:** Sürükle-bırak SortableJS ile yeniden yazıldı. Bayat bir işlem (araya
  başkası girmişse) sessizce üzerine yazmak yerine reddediliyor; yalnızca gönderene uyarı
  gidiyor ve pano sunucudan tazeleniyor.
- **Neden:** Aynı anda düzenleyen iki kişiden birinin değişikliği fark edilmeden kayboluyordu.
- **Nasıl denenir:** İki pencerede aynı kartı neredeyse aynı anda düzenle → biri uyarı alır,
  diğerinin değişikliği korunur.

### 2026-07-28 — Pano ve gerçek zamanlı işlemler

`76f2a5a`, `cf4b37b`, `02014cd`, `c13c4d2`, `84534a6`, `f55dc3a` · [ADR 0002](adr/0002-gercek-zamanli-protokol-stomp.md)

- **Ne değişti:** Pano → kolon → kart modeli ve REST uçları; STOMP üzerinden canlı kart
  ekleme, taşıma, düzenleme, silme ve kolon taşıma; açık tema, turuncu vurgu.
- **Neden:** Projenin çekirdeği: aynı panoyu açan herkes değişiklikleri yenilemeden görsün.
- **Nasıl denenir:** İki sekmede aynı panoyu aç, birinde kart ekle → diğerinde belirir.

### 2026-07-27 — Başlangıç

`29335e7`, `fb2a7cc` · ADR [0001](adr/0001-operasyon-tabanli-model-ve-cakisma-cozumu.md), [0002](adr/0002-gercek-zamanli-protokol-stomp.md)

- **Ne değişti:** Proje, kimlik doğrulama altyapısı hazır bir Spring Boot iskeletinden
  başlatıldı; ilk iki mimari karar yazıldı (operasyon tabanlı model, STOMP over WebSocket).
- **Neden:** Kimlik doğrulama, migration ve Docker altyapısını yeniden yazmadan asıl işe,
  gerçek zamanlı panoya odaklanmak.
- **Nasıl görülür:** [adr/README.md](adr/README.md).
