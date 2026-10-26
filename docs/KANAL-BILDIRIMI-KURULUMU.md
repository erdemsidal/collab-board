# Kanal bildirimleri kurulumu (Slack, Microsoft Teams, Discord)

Panoda seçilen bir şey olunca (yeni kart, kart son kolona geçti, yorum, atama)
Slack, Microsoft Teams ya da Discord kanalına kısa bir mesaj gider:

> Deniz Arslan, “Ödeme sayfası” kartını Done kolonuna taşıdı · Web Sitesi
> [Kartı aç]

İki ayrı iş var:

| Kim yapar | Ne | Ne zaman |
|---|---|---|
| Sunucuyu yöneten (sen) | İki şifreleme değeri üretip uygulamaya vermek | Bir kez: yerelde ve canlıda |
| Pano yöneticisi | Kanalın **webhook adresini** alıp panoya yapıştırmak | Her kanal için |

Uygulamanın ihtiyaç duyduğu iki değer:

| Değer | Neye benzer | Nereye gider |
|---|---|---|
| Şifreleme parolası | 64 karakterlik onaltılık: `3f9c…e21a` | `INTEGRATIONS_ENC_PASSWORD` |
| Şifreleme tuzu (salt) | 32 karakterlik onaltılık: `a17b…09cd` | `INTEGRATIONS_ENC_SALT` |

Bu ikisi verilmezse uygulama yine açılır, yalnızca kanal bildirimleri kapalı
kalır; pencere bunu açıkça söyler.

> **Neden şifre?** Webhook adresi bir paroladır: adresi bilen herkes o kanala
> mesaj yazabilir. Bu yüzden veritabanında şifreli durur ve tarayıcıya bir daha
> hiç gönderilmez, yalnızca son 4 karakteri gösterilir (`…a9F2`).

> **Önemli:** Bu iki değeri **değiştirir ya da kaybedersen** kayıtlı adresler
> çözülemez. Bağlantılar "Kayıtlı adres okunamadı" diye durur ve her birinin
> adresini yeniden yapıştırman gerekir. Bir kez üret, bir parola yöneticisinde
> sakla. Yerel ve canlı için **ayrı** çift üret: yerel `.env` bir gün sızsa bile
> canlıdaki adresler açılmasın.

---

## 1. İki değeri üret

### Windows (PowerShell)

Başlat menüsünde `PowerShell` yaz, aç. Aşağıdaki satırı yapıştır, **Enter**.
Çıkan 64 karakter parola:

```powershell
$b = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); -join ($b | ForEach-Object { $_.ToString("x2") })
```

Bu satır da tuz (32 karakter):

```powershell
$b = New-Object byte[] 16; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); -join ($b | ForEach-Object { $_.ToString("x2") })
```

### Git Bash, macOS ya da Linux

```bash
openssl rand -hex 32   # parola
openssl rand -hex 16   # tuz
```

İkisini bir yere not et. Canlı için aynı iki komutu bir kez daha çalıştır.

## 2. Yerelde (`.env`)

Proje kökündeki `.env` dosyasını aç (yoksa `cp .env.example .env`) ve en alta ekle:

```env
INTEGRATIONS_ENC_PASSWORD=birinci-komutun-cikardigi-64-karakter
INTEGRATIONS_ENC_SALT=ikinci-komutun-cikardigi-32-karakter
```

Tırnak koyma, `=` işaretinin iki yanında boşluk bırakma. `.env` git'e gitmez
(`.gitignore`'da).

Uygulamayı yeniden başlat:

- Docker ile: `docker compose up -d` (değerler `docker-compose.yml` üzerinden geçer).
- Kod yazarken: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` (`.env` kendiliğinden okunur).

Açılış loglarında şunu görmelisin:

```
Kanal bildirimleri açık: webhook adresleri şifreli saklanacak.
```

Bunun yerine **KANAL BİLDİRİMLERİ KAPALI: ŞİFRELEME ANAHTARI GEÇERSİZ** kutusu
çıkıyorsa kutunun içinde nedeni yazar (parola çok kısa, tuz onaltılık değil...).
Değerleri 1. adımdaki komutlarla yeniden üret.

## 3. Canlıda (Railway)

1. 👉 https://railway.com → projen → **collab-board** kutusu → **Variables**.
2. **+ New Variable** → sol kutuya `INTEGRATIONS_ENC_PASSWORD`, sağ kutuya
   canlı için ürettiğin parola → **Add**.
3. Tekrar **+ New Variable** → `INTEGRATIONS_ENC_SALT` → canlı için ürettiğin
   tuz → **Add**.
4. Aynı listede `APP_BASE_URL` değerinin tam olarak
   `https://collab-board-production-36db.up.railway.app` olduğunu kontrol et
   (`https`, sonda `/` yok). Mesajlardaki "Kartı aç" bağlantısı bundan üretilir.
5. Sayfanın üstünde **"Apply N changes"** çubuğu çıkar → **Deploy**.
6. collab-board → **Deployments** → en üstteki satır → **View logs** → arama
   kutusuna `Kanal bildirimleri` yaz. Görmen gereken:
   `Kanal bildirimleri açık: webhook adresleri şifreli saklanacak.`

> Başka bir ayara dokunma. Özellikle `APP_INTEGRATIONS_ALLOWEDURLS_...` diye
> bir değişken **ekleme**: o, izin verilen adres listesini değiştirir ve
> yalnızca testler içindir. Canlının listesi kodda ve sıkı.

## 4. Kanalın webhook adresini al

Pano yöneticisinin işi. Her uygulamada adres bir kez alınır; Sturnio'daki
pencerede **"… adres nasıl alınır?"** başlığına tıklayınca aynı adımlar görünür.

### Slack

1. 👉 https://api.slack.com/apps → **Create New App** → **From scratch**.
   Uygulamaya bir ad ver (ör. `Sturnio`), mesajların gideceği çalışma alanını
   seç → **Create App**.
2. Soldaki menüden **Incoming Webhooks** → sağ üstteki anahtarı **On** yap.
3. Sayfanın altında **Add New Webhook** → mesajların gideceği kanalı seç →
   **Allow**.
4. Listeye eklenen adresin yanındaki **Copy**. Adres
   `https://hooks.slack.com/services/T…/B…/…` biçimindedir.

### Microsoft Teams

Teams'te webhook artık **Workflows** ile alınıyor. Eski "Incoming Webhook"
bağlayıcısının adresleri (`…webhook.office.com/…`) Mayıs 2026'da kapatıldı ve
Sturnio onları kabul etmez.

1. Teams'te mesajların gideceği kanalın adının yanındaki **•••** → **Workflows**.
2. Şablonlardan **Post to a channel when a webhook request is received**.
3. Akışa bir ad ver (ör. `Sturnio`) → **Next** → ekibi ve kanalı onayla →
   **Add workflow**.
4. Son ekranda gösterilen adresi kopyala. Adres
   `https://….logic.azure.com:443/workflows/…` ya da
   `https://….api.powerplatform.com:443/…` biçimindedir.

### Discord

1. Sunucunun adına tıkla → **Server Settings** → **Integrations** → **Webhooks**.
2. **New Webhook** → webhook'a bir ad ver (ör. `Sturnio`) → mesajların gideceği
   kanalı seç.
3. **Copy Webhook URL**. Adres `https://discord.com/api/webhooks/…/…` biçimindedir.
4. Sayfanın altında **Save Changes** çıkarsa tıkla.

## 5. Panoya bağla ve dene

1. Sturnio'da yöneticisi olduğun panoyu aç.
2. Üstte pano adının yanındaki **⌄** → **Bildirim kanalları**.
   (Ya da **Ctrl + K** → `Bildirim kanalları`.) Bu seçenek yalnızca pano
   yöneticisine görünür.
3. Uygulamayı seç (**Slack**, **Microsoft Teams** ya da **Discord**).
4. **Webhook adresi** kutusuna 4. adımda kopyaladığın adresi yapıştır.
5. **Ad** kutusuna kanalın adını yaz (ör. `#genel`). İsteğe bağlı; listede bu
   adla görünür.
6. **Mesaj dili**: kanalın konuştuğu dil (Türkçe ya da English).
7. **Hangi olaylarda mesaj gitsin?** kutularını işaretle.
8. **Deneme mesajı gönder** → kanala bak:
   *"Sturnio bu kanala bağlandı. “Pano” panosunda seçilen olaylar buraya gelecek."*
   Pencerede yeşil **Deneme mesajı gitti** yazar.
9. **Kaydet**. Listede kanalın adı, adresin son 4 karakteri (`…a9F2`) ve
   yeşil **Açık** görünür.
10. Panoda bir kart ekle ya da bir kartı son kolona taşı. Birkaç saniye içinde
    kanalda mesaj belirir; listede "son mesaj şimdi" yazar.

## Ne gider, ne gitmez

| Olay | Örnek mesaj |
|---|---|
| Yeni kart | Deniz Arslan, “Ödeme sayfası” kartını To Do kolonuna ekledi · Web Sitesi |
| Kart son kolona geçti | Deniz Arslan, “Ödeme sayfası” kartını Done kolonuna taşıdı · Web Sitesi |
| Yorum | Deniz Arslan, “Ödeme sayfası” kartına yorum yazdı · Done · Web Sitesi |
| Atama | Deniz Arslan, “Ödeme sayfası” kartına Ayşe Yılmaz kullanıcısını atadı · In Progress · Web Sitesi |

- **Son kolon** panonun en sağdaki kolonudur ("Done", "Bitti"…). Mesaj yalnızca
  kart o kolona **girerken** gider; o kolonun içinde sıra değiştirmek mesaj değildir.
- **Yorumun metni gitmez**, yalnızca "yorum yazdı".
- **O kanaldaki herkes** kart başlıklarını ve kimin ne yaptığını görür. Gizli
  bir pano için kanalı ona göre seç.
- Bir panoya en fazla **5** kanal bağlanabilir.

## Sık karşılaşılan hatalar

| Ne görüyorsun | Sebep | Çözüm |
|---|---|---|
| Pencerede **"Kanal bildirimleri kapalı"** | Sunucuda iki değer yok | 2. ya da 3. adım; ikisini de ver, yeniden başlat |
| Loglarda **ŞİFRELEME ANAHTARI GEÇERSİZ** kutusu | Parola 16 karakterden kısa, tuz onaltılık değil ya da tek uzunlukta | 1. adımdaki komutlarla yeniden üret |
| **"Bu bir Slack webhook adresine benzemiyor"** | Yanlış uygulama seçili, adres eksik kopyalanmış ya da eski Teams bağlayıcısının adresi | Uygulamayı kontrol et, adresi baştan sona yeniden kopyala |
| Deneme: **"Kanal bu adresi tanımıyor (HTTP 404)"** | Webhook silinmiş ya da kanal arşivlenmiş | Uygulamada yeni webhook al, **Düzenle** ile yeni adresi yapıştır |
| Bağlantı **Durdu** | Kanal 404, 410 ya da 403 döndü ya da üst üste 5 mesaj gidemedi. Pano yöneticilerine zilde haber gider | Nedeni satırda yazar. Sorunu düzelt, **Yeniden aç**. Adres değiştiyse **Düzenle** ile yenisini yapıştır (bağlantı kendiliğinden açılır) |
| **"Kayıtlı adres okunamadı"** | Şifreleme değerleri değişmiş | **Düzenle** → adresi yeniden yapıştır → **Kaydet** |
| **"Çok fazla deneme mesajı gönderildi"** | 10 dakikada 10'dan fazla deneme | Birkaç dakika bekle |
| Mesaj hiç gelmiyor, bağlantı **Açık** | Seçili olaylar arasında o olay yok | **Düzenle** → kutuları kontrol et |

Teknik ayrıntı ve kararların gerekçesi: [ADR 0023](adr/0023-kanal-bildirimleri.md).
