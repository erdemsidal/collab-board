# E-posta kurulumu

CollabBoard, kayıt olan kullanıcılara doğrulama bağlantısı gönderir. Bu ayar
yapılmadan **yeni kayıtlar giriş yapamaz** — hesap açılır ama pasif kalır.

Uygulama herhangi bir SMTP sunucusuyla çalışır. Aşağıda Gmail anlatılıyor;
sonundaki tabloda diğer sağlayıcıların ayarları var.

---

## Gmail ile kurulum (~5 dakika)

### 1. İki adımlı doğrulamayı aç

Uygulama şifresi üretebilmenin ön koşulu bu. Kapalıysa Google şifre üretmene
izin vermez.

👉 https://myaccount.google.com/security → **İki Adımlı Doğrulama** → Aç

### 2. Uygulama şifresi üret

> ⚠️ **Normal Gmail şifren çalışmaz.** Google, 2022'den beri üçüncü taraf
> uygulamaların hesap şifresiyle bağlanmasını engelliyor. Uygulama şifresi,
> yalnızca bu uygulamaya özel, istediğin zaman iptal edebileceğin ayrı bir şifre.

👉 https://myaccount.google.com/apppasswords

- Uygulama adı olarak `CollabBoard` yaz → **Oluştur**
- Ekranda **16 haneli** bir şifre çıkar: `abcd efgh ijkl mnop`
- Kopyala. **Boşlukları sil** → `abcdefghijklmnop`
- Bu ekran bir kez gösterilir; kaybedersen yenisini üretirsin.

### 3. `.env` dosyasını oluştur

Proje kökünde:

```bash
cp .env.example .env
```

`.env` dosyasını aç. Doldurman gereken tek bölüm posta:

```env
MAIL_USERNAME=senin.adresin@gmail.com
MAIL_PASSWORD=abcdefghijklmnop
MAIL_FROM=CollabBoard <senin.adresin@gmail.com>
```

Geri kalan her şey yorum satırı olarak bırakılmıştır; `application.yml`
varsayılanları yerelde `docker-compose` ile birebir uyuşur. **Yorum
satırlarını açma** — içlerindeki yer tutucular (örneğin `JWT_SECRET`)
gerçek değer değildir ve uygulamanın açılmasını engeller.

> `.env` dosyası `.gitignore`'da — **asla commit edilmez.** Uygulama şifreni
> yanlışlıkla GitHub'a itmezsin.

### 4. Uygulamayı başlat

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Açılış loglarında şunu ara:

```
Posta sunucusu bağlantısı doğrulandı — smtp.gmail.com (senin.adresin@gmail.com)
```

Bunu gördüysen kurulum tamam. Görmediysen aşağıdaki tabloya bak.

### 5. Dene

Uygulamada gerçek bir adresle kayıt ol. Doğrulama postası o adrese düşer.

> İlk postalar **spam klasörüne** düşebilir. Kişisel Gmail hesabından gönderilen
> otomatik postalar için bu normaldir; "Spam değil" dersen sonrakiler gelen
> kutusuna gelir.

---

## Açılışta hata alıyorsan

Uygulama, posta sunucusuna bağlanmayı açılışta dener ve sonucu loglar. Hata
mesajına göre:

| Log'daki mesaj | Sebebi | Çözüm |
|---|---|---|
| `Username and Password not accepted` | Normal hesap şifresi kullanılmış | Uygulama şifresi üret (adım 2) |
| `POSTA AYARLARI EKSİK` | `MAIL_USERNAME` boş | `.env` dosyasını doldur |
| `Connection timed out` | 587 portu kapalı (kurum ağı/güvenlik duvarı) | `MAIL_PORT=465`, `MAIL_SMTP_STARTTLS=false`, `MAIL_SMTP_SSL=true` dene |
| `Could not connect to SMTP host` | Adres yanlış | `MAIL_HOST` değerini kontrol et |

Ayarlar yanlışsa uygulama **yine de açılır** — panolar posta olmadan da
çalışıyor ve kayıtlı kullanıcılar giriş yapabiliyor. Yalnızca yeni kayıtlar
doğrulanamaz.

---

## Şablon üzerinde çalışırken: Mailpit

Postanın görünümünü değiştiriyorsan elli deneme postasını gerçek kutuna
yollamanın anlamı yok. **Mailpit** yerel bir posta yakalayıcıdır: gerçek posta
göndermez, uygulamanın yolladığı her şeyi bir web arayüzünde gösterir.

```bash
docker compose --profile mailpit up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,mailpit
```

Postalar: **http://localhost:8025**

`mailpit` profili verilmediği sürece devreye girmez; varsayılan her zaman
gerçek gönderimdir.

---

## Canlıya çıkarken

`.env` dosyası sunucuya gitmez. Değişkenler barındırma platformunun kendi
ortam değişkeni panelinden girilir (Railway/Render → Variables).

Ek olarak **`APP_BASE_URL` mutlaka güncellenmeli** — doğrulama bağlantısındaki
adres buradan üretilir:

```env
APP_BASE_URL=https://collab-board.up.railway.app
```

Güncellenmezse postadaki bağlantı `http://localhost:8080` gösterir ve
kullanıcının makinesinde açılmaya çalışır.

> Gmail'in günlük gönderim sınırı ~500 posta. Gerçek bir ürün için Brevo,
> Mailgun ya da Amazon SES gibi bir transactional posta servisine geçilir —
> kod değişmez, yalnızca aşağıdaki değerler değişir.

---

## Gönderen adresi: `info@collabboard.com` gibi görünmek

Kişisel bir Gmail'den çıkan doğrulama postası amatör durur. Bunu düzeltmenin
yolu var ama **`MAIL_FROM` değerini değiştirmek yeterli değildir.**

### Neden yeterli değil

E-postada gönderen adresini yazmak, zarfın üstüne istediğin ismi yazmak gibidir —
kimse doğrulamaz. Bu yüzden posta sunucuları iki şeyden birini yapar:

- **Sessizce değiştirir.** Gmail bunu yapar: `MAIL_FROM=info@collabboard.com`
  yazsan bile postayı kimliğini doğruladığın hesabın adresiyle gönderir.
- **Reddeder.** Çoğu transactional servis, doğrulanmamış bir alan adından
  göndermeyi baştan engeller.

Alıcı tarafında da benzer bir kontrol var: sahip olmadığın bir alan adından
gelen posta **SPF/DKIM** kontrolünü geçemez ve doğrudan spam'e düşer. Yani
zorlasan bile sonuç kötüleşir.

> Uygulama bu uyuşmazlığı açılışta yakalar: `MAIL_FROM` ile `MAIL_USERNAME`
> farklıysa log'da **GÖNDEREN ADRESİ EŞLEŞMİYOR** uyarısı çıkar.

### Gerçekten yapmak için: alan adı + DNS

1. **Bir alan adı al.** (`collabboard.com` alınmış olabilir; `.app`, `.dev`,
   `.io` gibi uzantılara ya da farklı bir isme bakman gerekebilir.)
   Yıllık ~10–15 $.

2. **Transactional posta servisi aç.** Gmail bu iş için uygun değil —
   alan adı doğrulaması sunmuyor. Brevo, Resend, Mailgun ya da Amazon SES.

3. **Alan adını doğrula.** Servis sana birkaç DNS kaydı verir; alan adını
   aldığın yerin paneline eklersin:

   | Kayıt | Ne işe yarar |
   |---|---|
   | **SPF** (TXT) | "Bu sunucular benim adıma posta gönderebilir" |
   | **DKIM** (TXT) | Postayı imzalar; yolda değiştirilmediğini kanıtlar |
   | **DMARC** (TXT) | Doğrulamayı geçemeyen postaya ne yapılacağını söyler |

   Yayılması birkaç dakika ile birkaç saat sürer.

4. **`.env`'i güncelle** — kodda hiçbir değişiklik gerekmez:

   ```env
   MAIL_HOST=smtp-relay.brevo.com
   MAIL_USERNAME=<servisin verdiği kullanıcı>
   MAIL_PASSWORD=<SMTP anahtarı>
   MAIL_FROM=CollabBoard <info@collabboard.com>
   ```

### Alan adı almadan ne yapılabilir?

**Gönderen ADI zaten görünüyor.** `MAIL_FROM=CollabBoard <adres@gmail.com>`
ayarında alıcı gelen kutusunda büyük puntoyla **CollabBoard** görür; adres
küçük yazıyla yanında durur. Etkinin çoğu buradan gelir.

Kalan iyileştirme: kişisel adres yerine **uygulamaya ait ayrı bir Gmail hesabı**
açmak. Ücretsiz ve iki dakika sürer:

```env
MAIL_USERNAME=collabboard.mail@gmail.com
MAIL_PASSWORD=<o hesabın uygulama şifresi>
MAIL_FROM=CollabBoard <collabboard.mail@gmail.com>
```

`erdemsidall@gmail.com` yerine `collabboard.mail@gmail.com` görmek çok daha
derli toplu durur ve kişisel adresini de dışarı vermemiş olursun.

---

## Diğer sağlayıcılar

Kod sağlayıcıdan bağımsız; yalnızca `.env` değişir.

| Sağlayıcı | `MAIL_HOST` | Port | STARTTLS | Kullanıcı adı | Şifre |
|---|---|---|---|---|---|
| **Gmail** | `smtp.gmail.com` | 587 | true | Gmail adresin | Uygulama şifresi |
| **Brevo** | `smtp-relay.brevo.com` | 587 | true | Giriş adresin | SMTP anahtarı |
| **Mailgun** | `smtp.mailgun.org` | 587 | true | `postmaster@alan.adi` | SMTP şifresi |
| **SendGrid** | `smtp.sendgrid.net` | 587 | true | `apikey` (sabit) | API anahtarı |
| **Amazon SES** | `email-smtp.<bölge>.amazonaws.com` | 587 | true | SES SMTP kullanıcısı | SES SMTP şifresi |

> **Outlook/Hotmail çalışmaz.** Microsoft, kişisel hesaplarda SMTP şifre
> girişini (basic auth) kapattı. Hotmail adresin varsa bile *gönderim* için
> yukarıdakilerden birini kullanman gerekir — alıcı olarak Hotmail sorunsuz.
