# Canlıya alma rehberi

CollabBoard'u Railway'e çıkarmanın adımları ve canlıda karşılaşılabilecek
sorunlar. Önce büyük resim, sonra adımlar, en sonda "şu oldu, ne yapmalıyım"
tablosu.

---

## 1. Büyük resim: kod nasıl siteye dönüşür?

```
 Sen                GitHub                  Railway
 ───                ──────                  ───────
 git push  ──►  PR ──► CI testleri
                       │ (yeşil)
               merge ──► main  ──zil──►     1. DERLEME   Dockerfile ile imaj (~3-4 dk)
                                            2. AÇILIŞ    Değişkenler (Variables) konteynere verilir,
                                                         Flyway yeni migration'ları uygular
                                            3. YAYIN     Sağlık kontrolü geçince trafik yeni sürüme döner

 Kullanıcı ──https──► collab-board-production-xxxx.up.railway.app
                        │
                        ├─► collab-board (uygulama) ──özel ağ──► Postgres  (veri)
                        │                            ──özel ağ──► Redis     (oturumlar, canlı yayın)
                        └─► Brevo (HTTPS) ──► kullanıcının e-posta kutusu
```

**Akılda kalacak cümle:** `main`'e giren her commit canlıya gider. Canlıya
çıkmasını istemediğin şeyi `main`'e birleştirme.

**"Zil" (webhook):** GitHub, `main`'e kod girince Railway'e "yeni kod var" diye
haber verir; Railway de kodu çekip yayınlar. GitHub'ın bu haberi verebilmesi
için hesabında **Railway GitHub uygulaması** kurulu ve bu depoya erişimi açık
olmalı. Kurulu değilse Railway kodu yine okuyabilir (depo herkese açık), ama
yeni kodu kendiliğinden fark etmez — bkz. 6.1.

---

## 2. Yerelde ve canlıda neler farklı?

| | Yerelde (Docker) | Canlıda (Railway) |
|---|---|---|
| Adres | `http://localhost:8080` | `https://...up.railway.app` |
| Canlı bağlantı | `ws://` | `wss://` (şifreli) — kod adresi sayfadan türettiği için ayar gerekmez |
| Ayarlar | `.env` dosyası | Railway → Variables. `.env` **asla** sunucuya gitmez |
| Profil | `dev` (ayrıntılı log, Swagger ve metrikler açık) | `prod`, imajın varsayılanı (sade log, Swagger kapalı, varsayılan JWT anahtarıyla **açılmaz**; profil unutulsa da açılmaz, ADR 0025) |
| Güvenlik başlıkları | HSTS yok (HTTPS değil) | HSTS, Referrer-Policy, Permissions-Policy (bkz. 5.1) |
| Postgres / Redis | `docker compose` konteynerleri | Ayrı Railway servisleri, özel ağdan (`*.railway.internal`) |
| Posta | Gmail SMTP | **Brevo** (Railway SMTP portlarını kapatıyor) |
| Önündeki vekil (proxy) | Yok | Var: HTTPS'i o çözer, isteği uygulamaya o iletir |
| Veri | İstediğin zaman silinir | Gerçek kullanıcı verisi — geri alınamaz |

---

## 3. Durum

**Hazır**
- Dockerfile (çok aşamalı derleme, root olmayan kullanıcı, belleğe göre JVM ayarı)
- `PORT` değişkenini dinleme, `prod` profili, JWT anahtar koruması
- Origin denetimi (`CORS_ALLOWED_ORIGINS`), hız sınırı, vekil arkasında istemci IP'si
- Migration'lar otomatik (Flyway); `clean` kapalı
- Oturum: süresi dolmadan yenileme, 30 günlük kayan oturum
- Posta: `MAIL_PROVIDER=brevo` ile HTTPS üzerinden gönderim
- Statik dosyalar önbelleğe alınmıyor (Spring Security `no-store` ekliyor) — yeni sürüm hemen görünür
- Canlıda dışarıya yalnızca `/actuator/health` açık; iç metrikler kapalı, olmayan adresler 404

**Senin yapacakların** (kod değil, hesap/panel işi)
- Brevo hesabı, onaylı gönderen, IP kısıtlamasının kapatılması, API anahtarı
- Railway'de sağlık kontrolü yolu: `/actuator/health` (panelden, bkz. adım 6)
- *İsteğe bağlı:* "Google ile devam et" için Google Cloud Console'da OAuth istemcisi → [GOOGLE-GIRIS-KURULUMU.md](GOOGLE-GIRIS-KURULUMU.md)

**Bilerek sonraya bırakılanlar**
- Kendi alan adı (posta spam'e düşmesin, adres `info@...` olsun diye)
- Otomatik veritabanı yedeği, hata izleme (Sentry anahtarı boş)
- Birden fazla uygulama kopyası (hız sınırı sayaçları bellekte)

---

## 4. Adım adım canlıya alma (Railway paneli)

> Bu bilgisayarda Railway CLI çalışmıyor: Windows'un **Akıllı Uygulama
> Denetimi** `railway.exe`'yi engelliyor (komut hiçbir şey yazmadan kapanır).
> Her şey **railway.com** panelinden yapılır. CLI karşılıkları bölüm sonunda.

Sıra: **Veritabanları → Ayarlar → Kod → İzle → Sağlık kontrolü → Dene → Kapat**

**Adım 0 — Brevo** (tarayıcıda, bir kez): [MAIL-KURULUMU.md → "Railway'de"](MAIL-KURULUMU.md)
bölümündeki 1-4. maddeler. Sonunda elinde `xkeysib-` ile başlayan bir anahtar olmalı.

**Adım 1 — Önce veritabanları.** Uygulama açılırken Postgres ve Redis'e
bağlanır; onlar kapalıysa çöker ve Railway onu tekrar tekrar açmaya çalışıp
kredi harcar.
1. Projede **Postgres** kutusuna tıkla → sağda açılan panelde **Deployments**.
2. En üstteki satırın (kapatılmışsa `REMOVED` yazar) sağındaki **⋮** → **Redeploy**.
3. Aynısını **Redis** için yap. İkisinde de yeşil **Online** görünene kadar bekle.

**Adım 2 — Ayarlar (Variables).** Canlıda ayarlar `.env`'den değil buradan okunur.
1. **collab-board** kutusu → **Variables** → **+ New Variable**.
2. Sol kutuya isim, sağ kutuya değer → **Add**. Gizli anahtarı sohbete/ekrana değil, doğrudan buraya yapıştır.
3. Aynı isimde bir ayar zaten varsa *"Variable overwrite detected"* çıkar → **Overwrite** (eskisinin yerine yenisi).
4. Railway değişiklikleri biriktirir; sayfanın üstünde **"Apply N changes"** çubuğu çıkar → **Deploy**.

**Google ile giriş (isteğe bağlı).** Aynı yerden iki ayar daha:

| Değişken | Değer |
|---|---|
| `GOOGLE_CLIENT_ID` | Google Cloud Console → Google Auth Platform → **Clients**'taki *Client ID* (`...apps.googleusercontent.com`) |
| `GOOGLE_CLIENT_SECRET` | Aynı istemcinin *Client secret*'ı (`GOCSPX-...`) |

Google'a kayıtlı dönüş adresi
`https://collab-board-production-36db.up.railway.app/login/oauth2/code/google`
olmalı ve `APP_BASE_URL` tam olarak `https://collab-board-production-36db.up.railway.app`
olmalı: uygulama dönüş adresini bundan kurar. İkisi boş kalırsa uygulama yine
açılır, yalnızca Google düğmesi görünmez. Adım adım:
[GOOGLE-GIRIS-KURULUMU.md](GOOGLE-GIRIS-KURULUMU.md).

**Kanal bildirimleri (isteğe bağlı).** Panodan Slack, Teams ve Discord'a mesaj için iki ayar:

| Değişken | Değer |
|---|---|
| `INTEGRATIONS_ENC_PASSWORD` | `openssl rand -hex 32` ile üretilen 64 karakter |
| `INTEGRATIONS_ENC_SALT` | `openssl rand -hex 16` ile üretilen 32 karakter |

Yereldekinden farklı bir çift kullan ve bir yerde sakla: değişirse kayıtlı
webhook adresleri çözülemez. Boş kalırsa uygulama yine açılır, yalnızca kanal
bildirimleri kapalı olur. Loglarda `Kanal bildirimleri açık` yazmalı. Adım adım:
[KANAL-BILDIRIMI-KURULUMU.md](KANAL-BILDIRIMI-KURULUMU.md).

**Adım 3 — Kodu `main`'e al.** GitHub'da PR sayfası → **Merge pull request** →
**Confirm merge**. GitHub testleri (CI) geçince Railway kendiliğinden derlemeye
başlar (collab-board kutusunda *Initializing → Building → Deploying*).
Başlamazsa: Railway sayfasında **Ctrl + K** → `deploy` yaz → **Deploy Latest Commit**
→ servis olarak collab-board; sonra 6.1'deki "zil" sorununu düzelt.

**Adım 4 — İzle.** collab-board → **Deployments** → en üstteki **ACTIVE** satırı →
**View logs**. Arama kutusuna yaz:
- `migration` → hangi tablo değişikliklerinin uygulandığı
- `Brevo` → `Brevo bağlantısı doğrulandı`, `Brevo gönderen adresi onaylı`
- `Started` → uygulamanın açıldığı an

`level_value: 20000` bilgi, `40000` hata. Kutu içinde büyük harfli bir uyarı
görürsen Bölüm 6'da aynı başlığı ara.

**Adım 5 — Sağlık kontrolü** (bir kez): collab-board → **Settings** → **Deploy** →
*Healthcheck Path* = `/actuator/health`. Bundan sonra Railway yeni sürüme
trafiği ancak o "UP" dediğinde verir; her yayında ~20 sn'lik
"Application failed to respond" penceresi kapanır.

**Adım 6 — Bölüm 5'teki deneme listesi.**

**Kapatmak için** (kredi harcamasın) — sıra açarkenkinin **tersi**: önce
collab-board, sonra Postgres ve Redis. Kutu → **Deployments** → **ACTIVE**
satırı → **⋮** → **Remove**. *Remove silmek değildir:* yalnızca çalışan kopya
kalkar; kod, ayarlar, veriler (volume) ve adres yerinde durur. Açmak için Adım 1.

<details>
<summary>CLI karşılıkları (CLI'nin çalıştığı bir bilgisayarda)</summary>

```powershell
railway status                                            # hangi proje/ortam
railway redeploy -s Postgres -y                           # Adım 1
railway variable set MAIL_PROVIDER=brevo -s collab-board --skip-deploys   # Adım 2
Get-Clipboard | railway variable set BREVO_API_KEY --stdin -s collab-board --skip-deploys
railway logs -s collab-board --build                      # Adım 4: derleme
railway logs -s collab-board                              # Adım 4: çalışma
railway down -s collab-board                              # kapatma
```
`--skip-deploys`: ayar değişince hemen yeniden başlatma. `--stdin`: gizli değer
komut geçmişine yazılmasın.
</details>

---

## 5. Canlıda deneme listesi

Bir bilgisayar + bir telefon (ya da normal + gizli pencere) — iki ayrı kişi:

- [ ] `https://.../actuator/health` → `{"status":"UP"}`
- [ ] `https://.../actuator/metrics` → **404** (iç metrikler dışarıya kapalı)
- [ ] Gerçek bir adresle kayıt → posta geldi mi? **Spam klasörüne de bak.**
- [ ] Postadaki bağlantı `https://...railway.app` ile mi başlıyor (localhost değil)?
- [ ] Doğrula → giriş → pano oluştur
- [ ] Sağ üstte "Canlı" yeşil mi? (WebSocket `wss://` ile bağlandı demek)
- [ ] İkinci kişiyi davet et → sağ altta ✓/✗ kutusu **sayfa yenilenmeden** düştü mü?
- [ ] Kabul → kart ekle/taşı → diğer ekranda anında görünüyor mu?
- [ ] Profil fotoğrafı yükle, şifre değiştir
- [ ] "Şifremi unuttum" postası geliyor mu?
- [ ] (Google ayarlıysa) **Google ile devam et** → Google hesabını seç → uygulamaya giriş yapmış dönüyor musun?
- [ ] Sayfayı 20 dakika açık bırak → hâlâ oturum açık mı? Sağ üstteki "Canlı" hâlâ yeşil mi?
- [ ] Güvenlik kontrolü: aşağıdaki 5.1'in dört adımı

### 5.1 Yayından sonra güvenlik kontrolü (ADR 0025)

Güvenlik düzeltmeleri canlıya çıktıktan sonra, siteyi herkese duyurmadan önce
bir kez yap. Dört adım, toplam ~10 dakika. `SITE` yazan yere kendi adresini
koy (ör. `collab-board-production-36db.up.railway.app`).

> **Bilmen gereken:** bu sürümle oturum biçimi değişti; yayından sonra herkes
> (sen de) **bir kez yeniden giriş yapar**. Bu normal.

**Adım 1 — Değişkenler.** Railway → projen → **collab-board** kutusu →
**Variables**:
- `SPRING_PROFILES_ACTIVE` = `prod` olmalı. (Yoksa da imaj artık `prod` ile
  açılır; yine de açıkça yazılı olsun.)
- `JWT_SECRET` tanımlı olmalı. Tanımlı değilse uygulama **açılmaz** ve logda
  `HERKESE AÇIK JWT ANAHTARIYLA AÇILMA REDDEDİLDİ` yazar; bu bilerek böyle.
- `TRUSTED_PROXIES` ve `CLIENT_IP_HEADER` **ekleme**; yalnızca Adım 3 söylerse.

**Adım 2 — Güvenlik başlıkları ve kapalı uçlar.**
1. Tarayıcıda `https://SITE` adresini aç. **F12** → üstte **Network**
   (Ağ) sekmesi → sayfayı yenile (**F5**).
2. Listenin en üstündeki satıra (sitenin adı) tıkla → sağda **Headers** →
   **Response Headers** bölümü. Şunlar görünmeli:
   - `strict-transport-security: max-age=31536000 ; includeSubDomains`
   - `referrer-policy: strict-origin-when-cross-origin`
   - `permissions-policy: camera=(), microphone=(), geolocation=(), ...`
   - `x-content-type-options: nosniff` ve `x-frame-options: DENY`
3. Şu üç adres **404** vermeli (sayfada "Not Found" ya da boş bir hata):
   `https://SITE/actuator/metrics`, `https://SITE/swagger-ui.html`,
   `https://SITE/v3/api-docs`. `https://SITE/actuator/health` ise
   `{"status":"UP"}`.

`strict-transport-security` yoksa uygulama HTTPS'i göremiyor demektir: Adım 3'ü
yap ve sonucunu bana yaz.

**Adım 3 — Hız sınırı ve uydurma IP başlığı.** Bilgisayarında **PowerShell**
aç (Başlat → "PowerShell" yaz → Enter) ve şunu yapıştır (`SITE`'ı değiştir):

```powershell
# Her denemede farklı, uydurma bir IP. 11. istekte 429 gelmeli.
1..11 | % { curl.exe -s -o NUL -w "%{http_code} " -H "X-Forwarded-For: 198.51.100.$_" -H "Content-Type: application/json" -d '{\"email\":\"yok@ornek.com\",\"password\":\"yanlis12345\"}' https://SITE/api/auth/login }
```

- **Beklenen çıktı:** on tane `401`, sonra `429`. Uydurma başlık sınırı
  sıfırlayamıyor demek. ✅
- On bir tane `401` görürsen: sınır atlatılabiliyor. Railway başlığı
  istemcinin yazdığı gibi geçiriyor olabilir. Bir şey değiştirme, çıktıyı bana yaz.

Sonra hangi IP'nin sayıldığına bak:
1. Railway → **collab-board** → **Deployments** → en üstteki yayın →
   **View logs** (ya da **Logs** sekmesi).
2. Arama kutusuna `Hız sınırı aşıldı` yaz. Satırın sonunda `ip: ...` var.
3. Kendi IP'ni öğren: tarayıcıda `https://ifconfig.me` aç.
- **İki adres aynıysa** her şey doğru. ✅
- **Farklıysa** (ör. `100.64...`, `10...` ya da başka bir genel adres): uygulama
  herkesi aynı vekil adresinden gelmiş sanıyor, yani **herkes tek bir hız
  sınırını paylaşıyor**. Logdaki adresi bana yaz; `TRUSTED_PROXIES` değişkeniyle
  birlikte düzeltelim.

Bir dakika bekle, sonra devam et (giriş sınırı dakikalık).

**Adım 4 — Çıkış ve şifre değişimi.**
1. Bir tarayıcıda giriş yap, bir panoyu aç; ikinci bir tarayıcıda (ya da gizli
   pencerede) **aynı hesapla** giriş yap, aynı panoyu aç.
2. Birinci tarayıcıda **Profil** → şifreni değiştir.
3. Birkaç saniye içinde ikinci tarayıcı giriş ekranına düşmeli ("Oturumun
   süresi doldu"). Birincisi panoda kalmalı; "Canlı" yeniden yeşile dönmeli.
4. Birinci tarayıcıda sağ üstten **Çıkış yap**. Tekrar girip panonun
   açıldığını gör.

---

## 6. Karşılaşabileceğin sorunlar

### 6.1 Derleme (build) aşaması

| Belirti | Sebep | Çözüm |
|---|---|---|
| `mvn package` hata verdi | Kod derlenmiyor ya da bağımlılık indirilemedi | `--build` loglarına bak; yerelde `./mvnw package` aynı hatayı verir |
| Derleme çok uzun / bellek hatası | Maven bağımlılıkları her seferinde iniyor | Normal ilk derleme 3-4 dk; tekrar ederse Railway'in derleme önbelleğini temizle |
| `main`'e birleştirdim, CI yeşil, ama Railway **hiç derleme başlatmadı** | Railway GitHub uygulaması hesabında kurulu değil ya da bu depoya erişimi yok — GitHub "yeni kod var" haberini veremiyor. İzi: GitHub'da depo sayfasında hiç *Deployments* kaydı yok | GitHub → profil fotoğrafı → **Settings** → **Applications** → **Installed GitHub Apps** → **Railway** → **Configure** → *Repository access*'te `collab-board` seçili olsun. Liste boşsa Railway'de collab-board → **Settings** → **Source** üzerinden depoyu yeniden bağla, GitHub kurulum ekranı açılır. O ana kadar: Ctrl + K → **Deploy Latest Commit** |

### 6.2 Açılış aşaması — uygulama "Crashed"

| Loglarda | Sebep | Çözüm |
|---|---|---|
| `HERKESE AÇIK JWT ANAHTARIYLA AÇILMA REDDEDİLDİ` | Depodaki herkese açık anahtar yalnızca yerelde (dev/test profili + `http://localhost`) kabul edilir; canlıda bilerek açılmaz (ADR 0025) | `JWT_SECRET` değişkeni tanımlı mı? Logun son satırı nedenini söyler |
| `Connection refused` … `5432` | Postgres ayakta değil ya da `DB_*` yanlış | Adım 3; `DB_HOST=${{Postgres.PGHOST}}` gibi referanslar doğru mu? |
| `NOAUTH Authentication required` | Redis parola istiyor | `REDIS_PASSWORD=${{Redis.REDISPASSWORD}}` |
| `Migration ... failed` / `Validate failed` | Bir migration canlı veriyle çakıştı ya da eski bir migration dosyası değiştirildi | **Uygulanmış migration dosyasını asla değiştirme**; düzeltmeyi yeni bir `V16__...` ile yap |
| `Schema-validation: wrong column type` | Entity ile tablo uyuşmuyor (`ddl-auto: validate`) | Migration eksik; yerelde de aynı hata görünür |
| Exit code **137** / `OOMKilled` | Bellek sınırı aşıldı | Railway → Metrics'e bak; planın bellek sınırını kontrol et |
| Açıldı ama site "Application failed to respond" | Uygulama yanlış portu dinliyor | `PORT` değişkeni ile domain'in hedef portu aynı (8080) olmalı |

### 6.3 Posta

| Belirti | Sebep | Çözüm |
|---|---|---|
| Hiç posta gelmiyor, logda `Connection timed out` … 587 | Railway SMTP'yi kapatıyor; `MAIL_PROVIDER` hâlâ `smtp` | `MAIL_PROVIDER=brevo` |
| `BREVO ANAHTARI REDDEDİLDİ` · `Key not found` | Yanlış anahtar ya da SMTP anahtarı (`xsmtpsib-`) | API anahtarı (`xkeysib-`) |
| `BREVO ANAHTARI REDDEDİLDİ` · `unrecognised IP` | Brevo'nun IP kısıtlaması açık | Brevo → Security → Authorised IPs → kapat |
| `GÖNDEREN ADRESİ BREVO'DA ONAYLI DEĞİL` | `MAIL_FROM` Brevo'da eklenmemiş/onaylanmamış | Brevo → Senders → ekle, onay postasına tıkla |
| Posta gidiyor ama **spam'e** düşüyor | Gönderen `@gmail.com`, ama posta Brevo'dan çıkıyor; alan adı doğrulaması yok | Kısa vadede kullanıcıya "spam'e bak" de; kalıcısı: alan adı + Brevo'da DKIM/DMARC |
| Postadaki bağlantı `localhost` açıyor | `APP_BASE_URL` canlı adres değil | `APP_BASE_URL=https://...railway.app` |
| Günün bir saatinden sonra posta gitmiyor | Brevo ücretsiz plan: günde 300 posta | Brevo panelinde kotaya bak |

### 6.4 Canlı bağlantı ve güvenlik

| Belirti | Sebep | Çözüm |
|---|---|---|
| "Canlı" hiç yeşil olmuyor, tarayıcı konsolunda `/ws` **403** | `CORS_ALLOWED_ORIGINS` sitenin adresiyle birebir aynı değil (`https`, sonda `/` yok) | Değişkeni düzelt |
| Herkes aynı anda "Çok fazla giriş denemesi" alıyor | Hız sınırı herkesi tek IP (vekilin IP'si) sanıyor: vekil güvenilen adreslerde değil | 5.1 Adım 3: loglardaki `ip:` değerine bak; vekilin adresini `TRUSTED_PROXIES` (düzenli ifade) ile ekle |
| Biri hız sınırını aşabiliyor | Uygulama `X-Forwarded-For`'u sağdan okur ve yalnızca özel ağdaki vekile güvenir (ADR 0025). Railway başlığı istemcinin yazdığı gibi geçiriyorsa yine atlatılabilir | 5.1 Adım 3'teki komut 11. denemede 429 almalı; almıyorsa vekilin gerçek adresi koyduğu başlığı `CLIENT_IP_HEADER` ile seç (ör. `X-Real-IP`) |
| "Bu hesapla çok fazla hatalı giriş denemesi" | Hesap başına sınır: 15 dakikada 10 hatalı giriş. IP'den bağımsız | Bilerek. En geç 15 dakikada açılır; "Şifremi unuttum" ile hemen girilir |
| Panodan çıkarılan biri "Panodan çıkarıldın" görüp ana ekrana döndü | Sunucu onun o panodaki canlı bağlantısını kapattı | Bilerek (ADR 0025) |

### 6.5 Oturum

| Belirti | Sebep | Çözüm |
|---|---|---|
| Her yayından sonra herkes çıkış yapıyor | `JWT_SECRET` değişti (eski jetonların imzası tutmuyor) ya da Redis verisi silindi | Anahtarı sabit tut; anahtarı değiştirmek "herkesi çıkar" demektir — bazen bilerek yapılır (anahtar sızdıysa) |
| 15 dakikada bir çıkış | Eski sürüm (403/401 hatası) | PR #4 canlıda mı? |
| Yayından sonra herkes bir kez çıkış yaptı | Oturum (refresh token) biçimi değişti (ADR 0025) | Bir kerelik, beklenen |
| Biri bir anda o tarayıcıdaki oturumdan atıldı; logda `GÜVENLİK: kullanılmış refresh token yeniden geldi` | Aynı oturum jetonu iki yerde kullanıldı: çalınmış olabilir. O oturum (aile) kapatıldı, diğer cihazlar etkilenmez | Kişiye şifresini değiştirmesini söyle. Tek seferlikse ağ kopmasıyla kaybolan bir yenileme de olabilir |
| Şifre değişince diğer cihazlar birkaç saniyede çıkışa düştü | Bilerek: şifre değişimi bütün oturumları ve canlı bağlantıları kapatır | — |
| Google sayfasında `Error 400: redirect_uri_mismatch` | Google'a kayıtlı dönüş adresi ile `APP_BASE_URL` + `/login/oauth2/code/google` harfi harfine aynı değil | Loglarda `Google girişi açık — dönüş adresi:` satırındaki adresi Google Console → **Clients** → istemci → *Authorized redirect URIs*'e aynen ekle |
| "Google ile devam et" düğmesi yok | `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` eksik (ikisi birlikte gerekir) | Loglarda `Dış hesapla giriş kapalı` yazar; iki değişkeni de ekle. Diğer hatalar: [GOOGLE-GIRIS-KURULUMU.md](GOOGLE-GIRIS-KURULUMU.md#sık-karşılaşılan-hatalar) |

### 6.6 Veri

- **Migration geri alınamaz.** Flyway ileri gider; bir sütunu silen migration
  canlıda çalıştıysa o veri gitti. Riskli bir migration'dan önce yedek al:
  `railway connect Postgres` ile bağlanıp kontrol edebilir, `pg_dump` ile
  dışarı alabilirsin.
- **Uygulanmış migration dosyasını düzenleme.** Flyway dosyanın özetini tutar;
  değiştiğini görünce uygulama açılmaz.
- **Volume'u silme.** `railway down` veriyi silmez; servisi silmek siler.

### 6.7 Para

- Trial: 2026-10-01'de başlayan $5 / 30 gün. Bitince servisler durur; devam
  için Hobby planı gerekir.
- Uygulama açık durduğu her dakika bellek ve işlemci kadar ücret yazar —
  kullanıcı olmasa bile. Railway → Usage'dan takip et.
- Denemeler bitince `railway down` (üç servis için de).

---

## 7. Bir şey bozulursa: geri alma

1. **Hızlı:** Railway → collab-board → Deployments → çalışan son sürüm →
   *Redeploy*. Kod eski hâline döner (veritabanı dönmez!).
2. **Kalıcı:** GitHub'da bozan PR'ın sayfasında *Revert* → yeni bir PR açılır
   → birleştir → Railway otomatik yayınlar.
3. Bozan şey bir **migration** ise kod geri alınsa bile tablo yeni hâlinde
   kalır; eski kod yeni tabloyla açılmayabilir. Bu yüzden migration'lar
   "geriye uyumlu" yazılır: önce sütun eklenir, kod geçer, eskisi sonra silinir.

---

## 8. Akılda kalsın

1. `main` = canlı. Birleştirdiğin an yayındadır.
2. Sırlar (`.env`, anahtarlar) asla git'e girmez; canlıda Variables'a girer.
3. Önce veritabanı, sonra uygulama.
4. Loglar ilk bakılacak yer: `railway logs`. Uygulama sorununu orada kutu içinde yazar.
5. Uygulanmış migration'a dokunma; düzeltme yeni migration'dır.
6. Canlıda **gerçek** bir kullanıcı gibi baştan sona dene — iki ayrı kişiyle.
7. İşin bitince `railway down`.
