# ADR 0024 — Yönetim paneli ve ziyaretçi analitiği: kendi veritabanımızdan sayılar, çerezsiz Umami

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-26

---

## Bağlam

Site canlıya çıkınca sahibi iki tür soru soracak:

1. **Hesaplarla ilgili:** Kim kaydoldu, ne zaman, Google'la mı e-postayla mı?
   Kaç kişi gerçekten kullanıyor, geri geliyor mu? Hangi panolar canlı?
   Bu bilgilerin hepsi **zaten bizim veritabanımızda**.
2. **Ziyaretçilerle ilgili:** Tanıtım sayfasına kaç kişi geldi, nereden geldi
   (Google, LinkedIn, bir arkadaşın mesajı), hangi ülkeden, telefondan mı?
   Kayıt olmayan ziyaretçi bizim veritabanımızda hiç yok.

Cevaplanması gerekenler:

- İkinci tür için hangi araç: Google Analytics mi, başka bir şey mi?
- Birinci tür için panel kime açık, nasıl korunur?
- "Kullanıyor mu?" sorusu için hangi bilgi tutulur ve her istekte veritabanına
  yazmadan nasıl tutulur?

## Seçenekler (ziyaretçi analitiği)

| | Google Analytics 4 | Umami Cloud | Plausible | Kendi sunucumuzda Umami |
|---|---|---|---|---|
| Çerez | Var | Yok | Yok | Yok |
| Çerez onay bandı (KVKK/GDPR) | Gerekir | Genelde gerekmez | Genelde gerekmez | Genelde gerekmez |
| Ücret | Ücretsiz | Ücretsiz plan var | Ücretli (deneme süresi var) | Sunucu masrafı |
| Kurulum | Hesap + etiket + onay bandı | Hesap + kimlik | Hesap + alan adı | Ayrı uygulama + veritabanı |
| Betik boyutu | Büyük | ~2 KB | ~1 KB | ~2 KB |
| Arayüz | Çok güçlü, karmaşık | Tek sayfa, sade | Tek sayfa, sade | Tek sayfa, sade |
| Veri kimde | Google | Umami | Plausible | Bizde |

## Karar

**İkisini ayırıyoruz:** hesap soruları için kendi panelimiz (veritabanından),
ziyaretçi soruları için **Umami Cloud** (çerezsiz). Google Analytics yok.

Neden Google Analytics değil:

- **Çerez onay bandı.** GA çerez yazar; Türkiye (KVKK) ve AB (GDPR) için onay
  bandı gerekir. Bant hem ilk izlenimi bozar hem de "reddet" diyenler hiç
  sayılmaz; sayılar zaten eksik çıkar.
- **Gizlilik.** Ziyaretçinin davranışı reklam şirketine gider. Bir iş aracı
  için kötü bir başlangıç.
- **Sadelik.** Sahibin istediği beş sayı var (ziyaretçi, sayfa, kaynak, ülke,
  cihaz). Umami bunları tek ekranda gösterir.

Neden Plausible değil: Umami'nin ücretsiz planı başlangıç için yetiyor, ileride
aynı Umami'yi kendi sunucumuza taşıyabiliriz (açık kaynak) ve betik adresini
değiştirmek tek bir değişken (`ANALYTICS_UMAMI_SCRIPT_URL`).

### 1. Yönetim paneli: kim görür

- **Site sahipleri bir ortam değişkeninde:** `APP_ADMIN_EMAILS` (virgülle,
  büyük/küçük harf fark etmez, varsayılanı boş). Veritabanında rol yok, "yönetici
  yap" ekranı yok. Sahibi değiştirmek sunucuya erişimi olan birinin işi;
  veritabanına bir yolla yazabilen biri kendini sahip yapamaz.
- **Sahip olmayana panel yok.** `GET /api/admin/dashboard` sahip olmayana,
  **olmayan bir adres istemiş gibi** 404 döner (Spring'in kendi
  `NoResourceFoundException`'ı; gövde birebir aynı). 403 dönseydik "burada bir
  şey var" demiş olurduk. Oturumsuz istek, her kimlikli adres gibi 401.
- **Menüdeki giriş:** `/api/users/me` (ve giriş yanıtı) yalnızca sahipte
  `platformOwner: true` taşır; diğer herkesin yanıtında alan hiç yok.
  Arayüz hesap menüsüne "Yönetim"i buna göre koyar.
- Adresle eşleşme: hesabın e-postası doğrulanmış olmalı. Doğrulanmamış hesap
  giriş yapamadığı için, listedeki adresi başkası önden kaydetse bile panele
  ulaşamaz.

Arayüzün kodu herkese açık (depo da açık), yani panelin *varlığı* gizli değil;
gizli olan **veri**. Koruma sunucuda.

### 2. Son görülme

`users.last_seen_at` (V23). Oturum açmış her istekte (`JwtAuthenticationFilter`)
ve jeton yenilemede (`AuthService`) yazılır. Sayfayı açık bırakıp yalnızca canlı
bağlantıyla çalışan kişi REST isteği atmayabilir; tarayıcı jetonunu ~12 dakikada
bir yenilediği için yine görünür.

Her istekte yazmak her tıklamayı bir UPDATE'e çevirirdi. İki fren var
(`LastSeenTracker`):

1. **Bellek:** Bu sunucu kişi için son 5 dakikada yazdıysa veritabanına hiç
   gidilmez. İsteklerin neredeyse hepsi burada döner.
2. **Veritabanı:** `UPDATE ... WHERE last_seen_at < şimdi - 5 dk`. Birden çok
   sunucu çalışırken de kişi başına 5 dakikada en fazla bir yazma.

Bilgi en fazla 5 dakika geride kalır; "bugün aktif" için fazlasıyla yeterli.
Yazma başarısız olursa istek bozulmaz. Var olan hesaplar, panolardaki son
hareketleriyle dolduruldu (bir alt sınır).

### 3. Ne ölçülür

"Bugün", "son 7 gün", "son 30 gün" **takvim günleri** ve panele bakanın saat
diliminde (tarayıcı gönderir; Türkiye'de gece yarısı, UTC'de değil). Son 7 gün
bugünü de içerir. Grafiğin 30 çubuğunun toplamı bu yüzden "son 30 günde yeni"
sayısına eşit.

| Bölüm | Ne | Nereden |
|---|---|---|
| Kullanıcılar | Toplam; bugün, son 7 ve 30 günde yeni; 30 günlük kayıt grafiği | `users.created_at` |
| Etkinlik | Bugün / 7 gün / 30 gün aktif | `users.last_seen_at` |
| | Şu an bir panoda | Canlı bağlantılar (Redis presence, SCAN ile) |
| Geri gelenler | 7 ile 30 gün önce kayıt olanlardan son 7 günde yeniden gelenler | `created_at` + `last_seen_at` |
| Giriş yöntemi | Yalnızca Google / yalnızca e-posta ve şifre / ikisi | `user_identities`, `users.password` |
| Doğrulama | Doğrulanmış / bekleyen | `users.enabled` |
| Dil | Arayüz dili dağılımı | `users.locale` |
| İçerik | Çalışma alanı, pano, kart, yorum: toplam ve son 7 gün | İlgili tablolar (silinenler sayılmaz) |
| Son kayıtlar | En yeni 50 hesap: ad, e-posta, kayıt, yöntem, doğrulama, son görülme, pano sayısı | `users` + alt sorgular |
| En hareketli panolar | Son 7 günde en çok işlem gören 10 pano: ad, üye sayısı, işlem sayısı | `board_activities` |

Kart başlığı, yorum metni ya da hareketin içeriği **hiçbir yerde yok**. Pano adı
yalnızca son listede.

Geri gelme ölçüsü kasıtlı olarak basit: kohort, kayıt gününün kendisi
sayılmasın diye son 7 günü dışarıda bırakır (yeni gelen herkes zaten "son 7
günde görüldü").

### 4. Performans

- Düz SQL (`JdbcTemplate`), entity yok. Sorgu sayısı **sabit (yedi)**; kullanıcı
  ya da pano sayısıyla artmaz (N+1 yok).
- Kullanıcı sayıları tek geçişte: `COUNT(*) FILTER (WHERE ...)` ile on üç sayı.
- İndeksler (V23): `users(created_at)` (en yeni 50 ve 30 günlük grafik);
  `board_activities(created_at)` için **BRIN** (en hızlı büyüyen tablo, satırlar
  zaman sırasıyla eklenir; eski bölümleri okumadan atlar, B-tree'nin küçük bir
  kesri kadar yer kaplar).
- `last_seen_at`'e **indeks yok**, bilerek: panel tabloyu zaten tek geçişte sayıyor;
  indeksli bir sütunu güncellemek Postgres'in en ucuz güncelleme yolunu (HOT)
  kapatır ve her son görülme yazmasını pahalılaştırırdı.
- Yanıt `Cache-Control: no-store`: içinde e-postalar var.

### 5. Zaman

Tablolardaki `TIMESTAMP` sütunları **UTC duvar saati**: Hibernate yazarken UTC'ye
çevirir (`hibernate.jdbc.time_zone: UTC`), canlıdaki sunucu zaten UTC. Panelin
sınırları Java'da UTC olarak hesaplanıp sorguya verilir; grafiğin günleri
`(created_at AT TIME ZONE 'UTC') AT TIME ZONE :tz` ile kesilir. Bu varsayım bir
testle sabitlendi (`AdminDashboardIntegrationTest.kayitAniUtc`).

Saat dilimi tarayıcıdan gelir ve denetlenir: yalnızca `Europe/Istanbul` gibi
bölge adları. `GMT+3` gibi yazımlarda Java ile Postgres işareti ters yorumlar;
bunlar, tanınmayan adlar ve Postgres'in bilmediği adlar UTC'ye düşer.

### 6. Ziyaretçi analitiği: Umami

- **Ayar:** `ANALYTICS_UMAMI_WEBSITE_ID` (boşsa kapalı) ve
  `ANALYTICS_UMAMI_SCRIPT_URL` (varsayılan `https://cloud.umami.is/script.js`).
  Açılışta denetlenir; geçersiz değerde analitik kapalı kalır ve logda nedeni yazar.
- **Sayfaya nasıl ulaşır:** küçük, kimliksiz bir uç: `GET /api/public/config` →
  `{analytics: {umamiWebsiteId, scriptUrl}}` (5 dk önbellek). `analytics.js`
  bunu okur ve kimlik varsa betiği ekler. `index.html` sunucuda şablonla
  doldurulmadı: sayfalar düz statik dosya olarak kalsın, tek bir JSON yetiyor.
- **Betiğin ayarları:** `defer`, `data-website-id`,
  `data-exclude-search="true"` (adresin `?...` kısmı gitmez: kart bağlantıları,
  doğrulama ve sıfırlama jetonları orada), `data-exclude-hash="true"`,
  `data-do-not-track="true"` (tarayıcısında "izleme" kapalı olan sayılmaz; Umami
  belgelerinin de dediği gibi bu ayar artık yaygın değil ve bir onay çözümü
  yerine geçmez, yalnızca soran kişiye saygı).
- **Kapalıyken:** betik yüklenmez, Umami'ye tek istek gitmez. Sunucuya giden tek
  istek kendi ayar ucumuz.
- **Özel olaylar** (betik yoksa hiçbir şey yapmaz; yüklenirken gelenler sıraya alınır):

  | Olay | Ne zaman | Ek bilgi |
  |---|---|---|
  | `signup` | Kayıt başarılı | yok |
  | `signup-google` | Google düğmesine basıldı | `screen`: `register` ya da `login` |
  | `cta-start` | Tanıtım sayfasındaki ana "Kayıt ol" düğmeleri | `place`: `hero` ya da `bottom` |

  E-posta, ad ya da kimlik numarası **asla** olay verisi olarak gönderilmez.
- **CSP:** Bugün `Content-Security-Policy` başlığı yok. Eklenirse betiğin geldiği
  ve verinin gittiği Umami adresleri `script-src` ve `connect-src`'ye yazılmalı
  (adresler Umami belgelerinden kontrol edilir).

### 7. Gizlilik (KVKK / GDPR)

- **Yönetim paneli kişisel veri gösterir:** site sahibi kullanıcıların adını,
  e-postasını, kayıt tarihini, en son ne zaman geldiklerini ve pano adlarını
  görür. **Gizlilik politikasında bunun yazması gerekir:** kimin gördüğü (site
  sahibi), ne için (hizmeti işletmek ve geliştirmek), ne kadar süre tutulduğu.
- **Umami** çerez kullanmaz ve kişiyi tanımlayan bir şey saklamaz (IP adresini
  tutmaz). Bu yüzden genelde onay bandı gerekmez. Yine de bir **veri işleyen**
  olarak gizlilik sayfasında anılmalı: hangi hizmet, ne topluyor (sayfa, kaynak,
  ülke, cihaz türü), verinin nerede tutulduğu (Umami'nin sayfasından kontrol edilir).
- Gizlilik sayfası henüz yok; bu karar onu bekleyen işlere ekler.
- Bu bölüm bir hukuk görüşü değil; politika metni yazılırken bir uzmana sorulmalı.

## Sonuçlar (feda ettiklerimiz)

- **Son görülme 5 dakika geriden gelir** ve yalnızca en son anı tutar. Geçmişe
  dönük "her günün aktif kişi sayısı" grafiği çizilemez; bunun için günlük bir
  özet tablosu gerekir. İhtiyaç olunca eklenir.
- **Var olan hesapların son görülmesi tahmin:** panolardaki son hareketleri. Hiç
  hareketi olmayan eski hesaplar "henüz yok" görünür, ta ki bir sonraki girişe kadar.
- **Silinen içerik sayılmaz.** "Bugüne kadar oluşturulan kart" değil, "şu an
  var olan kart".
- **"Şu an bir panoda"** sunucu çöktüyse Redis'te kalan oturumları da sayabilir
  (ADR 0004'teki bilinen boşluk). Yalnızca panoyu açık tutanları sayar; ana
  ekranda duran kişi orada yok.
- **Umami sayıları eksik çıkar:** reklam engelleyiciler betiği engeller,
  "izleme" kapalı tarayıcılar sayılmaz. Bilerek: kişinin tercihine saygı.
- **Umami Cloud'un ücretsiz planı sınırlı** (aylık olay sayısı, verinin saklama
  süresi). Sınırlar değişebilir; kayıt olurken fiyat sayfasından bakılır
  (`docs/ANALITIK-KURULUMU.md`).
- **Sahip listesi yeniden yayın ister:** `APP_ADMIN_EMAILS` değişince uygulama
  yeniden başlamalı. Az değişen bir şey için kabul edilebilir.
