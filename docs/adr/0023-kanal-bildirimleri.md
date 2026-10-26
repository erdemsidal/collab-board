# ADR 0023 — Kanal bildirimleri: Slack, Microsoft Teams, Discord

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-26

---

## Bağlam

Ekipler konuşmalarını Slack, Teams ya da Discord'da yapıyor. Panoda bir iş
bitince ya da bir karta yorum yazılınca bunu orada görmek istiyorlar; herkes
gün boyu panoyu açık tutmuyor.

Cevaplanması gereken dört soru vardı:

1. Kanala nasıl bağlanılır: her uygulama için bir OAuth uygulaması mı, yoksa
   kullanıcının yapıştırdığı bir adres mi?
2. O adres bir parola gibi davranıyor (bilen herkes kanala yazar). Nasıl saklanır?
3. İsteği sunucu atıyor, adresi kullanıcı veriyor. Sunucu iç ağına ya da
   bulutun bilgi servisine istek attırılmaya nasıl kapatılır (SSRF)?
4. Kanal yavaşsa ya da düşmüşse kartı taşıyan kişi bundan nasıl etkilenmez?

## Karar

### 1. Gelen webhook, OAuth uygulaması değil

Pano yöneticisi kanalın **gelen webhook adresini** yapıştırır; Sturnio o adrese
JSON POST eder. Slack'te "Incoming Webhooks", Teams'te "Workflows", Discord'da
kanalın "Webhooks" ayarı.

| | Gelen webhook | OAuth uygulaması |
|---|---|---|
| Kurulum | Adresi kopyala, yapıştır | Her uygulamada kayıt, izin ekranı, onay süreci |
| Bize gelen istek | Yok | Dönüş adresi ve olay uçları gerekir |
| Yerelde (localhost) | Çalışır | Dışarıdan erişilebilir bir adres ister |
| Yapabildiği | Tek kanala mesaj | Kanal seçme, kişiyi anma, mesajı güncelleme |

Bize yalnızca "kanala kısa bir satır yaz" lazımdı; webhook bunu en az parçayla
yapıyor. OAuth, ileride kişiye özel mesaj ya da mesajdan işlem (kartı oradan
taşımak) istenirse yeniden düşünülür.

Teams için yalnızca **Workflows** adresleri kabul edilir. Eski Office 365
bağlayıcısının webhook'ları Mayıs 2026'da kapatıldı.

### 2. Adres bir parola: şifreli saklanır, geri dönmez

`board_chat_hooks.url_encrypted` (V22): Spring Security'nin `Encryptors.delux`'u,
yani AES-256-GCM. Anahtar iki ortam değişkeninden türetilir:
`INTEGRATIONS_ENC_PASSWORD` ve `INTEGRATIONS_ENC_SALT` (onaltılık).

- **Varsayılan anahtar yok.** Depoda duran bir anahtarla şifrelemek, hiç
  şifrelememekle aynı şey (JWT'de bunu yaşadık, [ADR 0010](0010-canliya-cikis-oncesi-guvenlik.md)).
  Değerler yoksa ya da geçersizse özellik kapalı: uygulama açılır, uçlar
  yerelleştirilmiş açık bir mesajla `503` döner, pencere bunu söyler.
- **GCM:** her şifreleme rastgele bir başlangıç vektörü kullanır, aynı adres iki
  kez farklı görünür; şifreli metin değiştirilirse çözme başarısız olur.
  Veritabanına yazabilen biri adresi fark ettirmeden başkasına çeviremez.
- **Tarayıcıya hiç dönmez.** Yanıtta yalnızca son 4 karakter (`url_hint`).
  Yönetici hangi webhook'un bağlı olduğunu tanır; yanıtı gören biri (eklenti,
  ekran paylaşımı) kanala yazamaz. Düzenlerken adres alanı boştur, boş kalırsa
  kayıtlı adres değişmez.
- **Hash değil, şifre:** gönderirken adresin kendisine ihtiyaç var.

Anahtar değişirse eski kayıtlar çözülemez. Bağlantı `UNREADABLE` nedeniyle
durur, yönetici adresi yeniden yapıştırır. Anahtar döndürme (eski anahtarla
çözüp yenisiyle yazma) bu sürümde yok.

### 3. İzin listesi (SSRF koruması)

Sunucu yalnızca üç uygulamanın gerçek webhook adreslerine POST eder:

| Uygulama | Desen |
|---|---|
| Slack | `^https://hooks\.slack\.com/services/[A-Za-z0-9/_-]+$` |
| Discord | `^https://(discord\|discordapp)\.com/api/webhooks/\d+/[\w-]+$` |
| Teams | `https`, ana bilgisayar `*.api.powerplatform.com` ya da `*.logic.azure.com`, isteğe bağlı `:443` |

Desenin yanında yapı denetimi de var: adres ayrıştırılabilir ve mutlak olmalı,
şeması `http(s)`, kullanıcı bilgisi (`kişi@sunucu`), parça (`#...`), ters bölü,
boşluk ve kontrol karakteri olmamalı. Böylece bir desen yanlışlıkla gevşek
yazılsa bile "`https://evil@hooks.slack.com`" türü ayrıştırıcı farkları açık
kalmaz.

- Adres **kaydederken ve her gönderimden önce** denetlenir. Liste sonradan
  daraltılırsa eski kayıt `BLOCKED` nedeniyle durur.
- **Yönlendirme izlenmez** (`HttpClient.Redirect.NEVER`): izinli bir adres bizi
  listede olmayan bir yere yönlendiremesin. 3xx başarısız sayılır.
- Bağlantı 3 sn, yanıt 5 sn içinde gelmeli; gövde dahil üst sınır 6 sn.
- Desenler `app.integrations.allowed-urls.{slack,teams,discord}` ile
  değiştirilebilir. Bunu yalnızca testler ve yerel deneme yapar (yerel bir
  alıcıya izin vermek için); canlıda varsayılanlar kalır.

### 4. İşlem kaydedildikten sonra, arka planda

Kart ve yorum servisleri işlemin içinde bir `CardActivityEvent` yayınlar.
`ChatNotifier` onu `@TransactionalEventListener(AFTER_COMMIT)` ve `@Async` ile
dinler:

- **AFTER_COMMIT:** reddedilen ya da geri alınan bir taşıma kanala "taşıdı" diye
  yazılmaz. Olmamış bir şeyi duyurmayız.
- **@Async:** kartı taşıyan kişi Slack'in cevabını beklemez. Kanal yavaşsa ya da
  hata dönerse kişinin işlemi etkilenmez; dinleyicideki her hata yalnızca loglanır.
- Panonun bağlı kanalı yoksa dinleyici ilk sorgudan sonra döner. Kişi adı, pano
  adı ve "son kolon mu?" sorusu yalnızca gidecek bir mesaj varsa sorulur.
- Gönderim veritabanı işlemi tutmaz; sonuçlar tek satırlık güncellemelerle
  yazılır (sayacı "oku, artır, yaz" ile değil, `failure_count + 1` ile).

### 5. Olaylar ve "bitti" kuralı

Her bağlantı dinleyeceği olayları seçer: yeni kart, kart son kolona geçti,
yorum, atama.

- **Bitti = panonun son kolonu**, ana ekran ve akış ölçümüyle aynı tanım
  ([ADR 0015](0015-tanitim-sayfasi-ve-ana-ekran-ozeti.md)). Mesaj yalnızca kart
  o kolona başka bir kolondan **girerken** gider; o kolonun içinde sıralama
  mesaj değildir. Tek kolonlu panoda son kolon "bitti" sayılmaz.
- **Atama** yalnızca atanan kişi değişip biri atandığında: başlık düzeltmek
  kanala "atadı" yazdırmaz. Kişi kartı kendisi üstlendiyse "üstlendi".
- **Yorumun metni gitmez**, yalnızca "yorum yazdı". Yorumlar kartın altındaki
  konuşma; kanala taşımak kişinin kararı olmalı.

Kartlar başlıkla ve bağlantıyla anılır (`<APP_BASE_URL>/?board=<id>&card=<id>`).
Kart anahtarları (STR-42) bu işten bağımsız.

### 6. Mesaj: tek satır, kanalın dilinde, kaçışlanmış

"Deniz Arslan, “Ödeme sayfası” kartını Done kolonuna taşıdı · Web Sitesi" ve
kartı açan bağlantı. Dil bağlantının kendi dili (`locale`): kanalın konuştuğu
dil panoyu kullananlarınkinden farklı olabilir. Metinler `messages_*.properties`
içinde (`chat.*`). Türkçede kişi adına ek gelmesin diye "Ayşe Yılmaz
kullanıcısını atadı" (Ayşe'ye / Mert'e ayrımı adın son harfine bakmayı isterdi).

Kişiden gelen her şey (kart başlığı, pano ve kişi adı) uygulamanın biçim diline
göre kaçışlanır:

| Uygulama | Biçim | Kaçış |
|---|---|---|
| Slack | `text` + bir `section` bloğu (mrkdwn) | `&`, `<`, `>` → `&amp;` `&lt;` `&gt;`. "`<!channel>`" başlıklı kart kimseyi dürtmez |
| Discord | `content`, `allowed_mentions: {parse: []}`, önizleme kapalı | Biçim karakterlerinin önüne `\`. "@everyone" yazan başlık bildirim düşürmez |
| Teams | Adaptive Card 1.4: sarılan bir `TextBlock` ve `Action.OpenUrl` "Kartı aç" | `\`, `*`, `_`, `[`, `]` önüne `\`. "[Şifreni yenile](https://...)" başlıklı kart resmi görünen bir bağlantıya dönüşmez |

### 7. Hatalar: sayaç, durdurma, yöneticiye haber

| Kanalın cevabı | Sonuç |
|---|---|
| 2xx | Hata sayacı sıfırlanır, "son mesaj" güncellenir |
| 404, 410 | Bağlantı hemen durur (`GONE`): webhook silinmiş ya da kanal arşivlenmiş |
| 403 | Bağlantı hemen durur (`FORBIDDEN`) |
| 429, 5xx, zaman aşımı, bağlantı hatası, 3xx, diğer 4xx | Sayaç +1; üst üste 5'te durur (`FAILING`) |

Duran bağlantı için panonun yöneticilerine (etkin rolü OWNER olanlar; çalışma
alanı sahibi ve yöneticisi dahil) uygulama içinde bildirim gider
(`CHAT_HOOK_DISABLED`, V22'de `notifications.detail` kanalın adını taşır).
Aynı anda iki gönderim başarısız olsa da haber bir kez gider: durdurma
`WHERE disabled_reason IS NULL` ile yapılır, satırı değiştiren çağrı haber verir.

Yönetici sorunu düzeltip **Yeniden aç** der; adres değiştiyse yenisini
yapıştırmak bağlantıyı kendiliğinden açar. Yeniden deneme kuyruğu bu sürümde yok.

### 8. Yetki ve sınırlar

- Listelemek, eklemek, değiştirmek, denemek, silmek: yalnızca panonun etkin
  yöneticisi (`BoardAccessService.requireOwner`). Editör, izleyici ve yabancı `403`.
  Başka panonun bağlantısı bu panonun adresinden `404`.
- Yetki önce, kurulum sonra: anahtarlar yoksa bile yönetici olmayan `403` alır,
  sunucunun ayarını öğrenmez.
- Bir panoya en fazla 5 bağlantı: her olay her bağlantıya ayrı bir istek.
- **Deneme mesajı** gerçek bir kanala yazar; iki deneme ucu
  (`/chat-hooks/test`, `/chat-hooks/{id}/test`) IP başına 10 dakikada 10 ile
  sınırlı (`RateLimitPolicy.CHAT_HOOK_TEST`). Sınırsız olsa sunucumuz, adresini
  bildiğin bir kanala mesaj yağdırmanın aracı olurdu.
- Kayıtlı bağlantıyı denemek durumunu değiştirmez (durmuş bağlantı "düzeldi mi?"
  diye denenebilir).

### 9. Arayüz

Pano adının menüsünde ve komut paletinde **Bildirim kanalları**, yalnızca
yöneticiye. Pencere bağlantıları listeler (uygulama, ad, `…a9F2`, olaylar,
durum, son mesaj), yerinde düzenler ve siler. Form uygulamayı, adresi, adı,
mesaj dilini ve olayları alır; **Deneme mesajı gönder** kaydetmeden önce
adresi dener ve kanalın cevabını söyler. Her uygulama için 3-4 adımlık açılır
bir rehber var. Tek satırlık bir uyarı: o kanaldaki herkes kart başlıklarını görür.

## Sonuçlar

**Kazandıklarımız**
- Ekip, panoda olan biteni konuştuğu yerde görüyor; kurulum bir adres yapıştırmak.
- Adres sızmaz: veritabanında şifreli, yanıtlarda yok, loglarda yok.
- Sunucu yalnızca üç uygulamanın adreslerine istek atar; yönlendirme izlemez.
- Kanal ne yaparsa yapsın kişinin işlemi etkilenmez (testle sabit: 8 sn
  bekleten bir kanal varken kart eklemek ve yorum yazmak 2 sn'nin altında).

**Feda ettiklerimiz / bilinen sınırlar**
- **Yeniden deneme yok.** Kanal o an düşmüşse o mesaj kaybolur; sayaç artar.
  Gönderimler bellekte bekler: sunucu tam o sırada yeniden başlarsa gidecek
  mesaj gitmez. Kalıcı bir kuyruk (outbox tablosu) ileride.
- **Anahtar döndürme yok.** Anahtarı değiştirmek, bütün adresleri yeniden
  yapıştırmak demek.
- Kanaldaki herkes kart başlıklarını görür; gizli bir pano için kanal ona göre
  seçilmeli (pencerede tek satırlık uyarı).
- Bağlantı eklemek ve silmek "Son hareketler"e yazılmıyor; kimin eklediği
  yalnızca `created_by`'da duruyor.
- Teams'te kaçış ters bölüye dayanıyor; Teams'in bir istemcisi kaçışı
  tanımazsa başlıkta `\[` gibi işaretler görünebilir (biçim ya da sahte
  bağlantı yerine).
- "En fazla 5" denetimi aynı anda gelen iki ekleme isteğinde aşılabilir.
- Deneme sınırı IP başına ve bellekte ([ADR 0010](0010-canliya-cikis-oncesi-guvenlik.md)
  ile aynı kabul): iki sunucuda etkin sınır iki katı.
