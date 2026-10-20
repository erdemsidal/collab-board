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
               merge ──► main  ──────────►  1. DERLEME   Dockerfile ile imaj (~3-4 dk)
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

---

## 2. Yerelde ve canlıda neler farklı?

| | Yerelde (Docker) | Canlıda (Railway) |
|---|---|---|
| Adres | `http://localhost:8080` | `https://...up.railway.app` |
| Canlı bağlantı | `ws://` | `wss://` (şifreli) — kod adresi sayfadan türettiği için ayar gerekmez |
| Ayarlar | `.env` dosyası | Railway → Variables. `.env` **asla** sunucuya gitmez |
| Profil | `dev` (ayrıntılı log, Swagger açık) | `prod` (sade log, Swagger kapalı, varsayılan JWT anahtarıyla **açılmaz**) |
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

**Senin yapacakların** (kod değil, hesap/panel işi)
- Brevo hesabı, onaylı gönderen, IP kısıtlamasının kapatılması, API anahtarı
- Railway'de sağlık kontrolü yolu: `/actuator/health` (panelden, bkz. adım 6)

**Bilerek sonraya bırakılanlar**
- Kendi alan adı (posta spam'e düşmesin, adres `info@...` olsun diye)
- Otomatik veritabanı yedeği, hata izleme (Sentry anahtarı boş)
- Birden fazla uygulama kopyası (hız sınırı sayaçları bellekte)

---

## 4. Adım adım canlıya alma

> Komutlar proje klasöründe, PowerShell'de çalıştırılır. Her komuttan sonra
> çıktıyı oku; hata varsa bir sonrakine geçme.

**Adım 0 — Brevo** (tarayıcıda, bir kez): [MAIL-KURULUMU.md → "Railway'de"](MAIL-KURULUMU.md)
bölümündeki 1-4. maddeler. Sonunda elinde `xkeysib-` ile başlayan bir anahtar olmalı.

**Adım 1 — Doğru projeye bağlı mıyım?**
```powershell
railway status
```
Proje `collab-board`, ortam `production` görünmeli.

**Adım 2 — Posta değişkenleri** (`--skip-deploys`: değişken değişince
uygulamayı hemen yeniden başlatma, hepsini bitirince başlatacağız)
```powershell
railway variable set MAIL_PROVIDER=brevo -s collab-board --skip-deploys
railway variable set "MAIL_FROM=CollabBoard <onayladigin.adres@gmail.com>" -s collab-board --skip-deploys
```
Anahtarı ekrana yazmadan, panodan ver (komut geçmişinde kalmaz). Önce anahtarı kopyala:
```powershell
Get-Clipboard | railway variable set BREVO_API_KEY --stdin -s collab-board --skip-deploys
```
Kontrol (bu komut değerleri **açıkça** gösterebilir — ekran paylaşıyorsan dikkat):
```powershell
railway variable list -s collab-board
```

**Adım 3 — Önce veritabanları.** Uygulama açılırken Postgres ve Redis'e
bağlanır; onlar yoksa açılamaz.
```powershell
railway redeploy -s Postgres -y
railway redeploy -s Redis -y
railway service status
```
İkisi de `SUCCESS` olana kadar bekle.

**Adım 4 — Kodu `main`'e al.** GitHub'da sırayla birleştir: #3 → #4 → #5.
Her birleştirme Railway'de derleme başlatır; sonuncusu geçerli olur.

**Adım 5 — İzle.**
```powershell
railway logs -s collab-board --build    # derleme
railway logs -s collab-board            # açılış ve çalışma
```
Görmen gerekenler: `Successfully applied 3 migrations` (V13-V15),
`Brevo bağlantısı doğrulandı`, `Brevo gönderen adresi onaylı`, `Started CollabBoardApplication`.
Kutu içinde büyük harfli bir uyarı görürsen Bölüm 6'da aynı başlığı ara.

**Adım 6 — Sağlık kontrolü** (panelde, bir kez): collab-board → Settings →
Deploy → *Healthcheck Path* = `/actuator/health`. Bundan sonra Railway yeni
sürüme trafiği ancak o "UP" dediğinde verir; her yayında ~20 sn'lik
"Application failed to respond" penceresi kapanır.

**Adım 7 — Bölüm 5'teki deneme listesi.**

**Durdurmak için** (kredi harcamasın): `railway down -s collab-board`, sonra
aynısı Postgres ve Redis için. Veriler (volume) silinmez.

---

## 5. Canlıda deneme listesi

Bir bilgisayar + bir telefon (ya da normal + gizli pencere) — iki ayrı kişi:

- [ ] `https://.../actuator/health` → `{"status":"UP"}`
- [ ] Gerçek bir adresle kayıt → posta geldi mi? **Spam klasörüne de bak.**
- [ ] Postadaki bağlantı `https://...railway.app` ile mi başlıyor (localhost değil)?
- [ ] Doğrula → giriş → pano oluştur
- [ ] Sağ üstte "Canlı" yeşil mi? (WebSocket `wss://` ile bağlandı demek)
- [ ] İkinci kişiyi davet et → sağ altta ✓/✗ kutusu **sayfa yenilenmeden** düştü mü?
- [ ] Kabul → kart ekle/taşı → diğer ekranda anında görünüyor mu?
- [ ] Profil fotoğrafı yükle, şifre değiştir
- [ ] "Şifremi unuttum" postası geliyor mu?
- [ ] Sayfayı 20 dakika açık bırak → hâlâ oturum açık mı?
- [ ] Hız sınırı sahte başlıkla aşılabiliyor mu? (bkz. 6.4)

---

## 6. Karşılaşabileceğin sorunlar

### 6.1 Derleme (build) aşaması

| Belirti | Sebep | Çözüm |
|---|---|---|
| `mvn package` hata verdi | Kod derlenmiyor ya da bağımlılık indirilemedi | `--build` loglarına bak; yerelde `./mvnw package` aynı hatayı verir |
| Derleme çok uzun / bellek hatası | Maven bağımlılıkları her seferinde iniyor | Normal ilk derleme 3-4 dk; tekrar ederse Railway'in derleme önbelleğini temizle |

### 6.2 Açılış aşaması — uygulama "Crashed"

| Loglarda | Sebep | Çözüm |
|---|---|---|
| `ÜRETİMDE HERKESE AÇIK JWT ANAHTARI` | `prod` profili depodaki herkese açık anahtarı reddeder (bilerek) | `JWT_SECRET` değişkeni tanımlı mı? |
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
| Herkes aynı anda "Çok fazla giriş denemesi" alıyor | Hız sınırı herkesi tek IP (vekilin IP'si) sanıyor | Uygulama `X-Forwarded-For`'un ilk değerini kullanıyor; loglardaki `ip:` değerine bak |
| Biri hız sınırını aşabiliyor | `X-Forwarded-For` istemcinin yazdığı değeri taşıyorsa sahte IP ile sınır atlatılır. Railway kendi personelinin cevaplarında bile çelişkili | **Dene:** aşağıdaki komut 11. denemede 429 almalı |

```powershell
# Her denemede farklı sahte IP. 11. istekte 429 gelmiyorsa sınır atlatılabiliyor demektir.
1..11 | % { curl.exe -s -o NUL -w "%{http_code} " -H "X-Forwarded-For: 10.0.0.$_" -H "Content-Type: application/json" -d '{\"email\":\"yok@ornek.com\",\"password\":\"yanlis12345\"}' https://SITE/api/auth/login }
```

### 6.5 Oturum

| Belirti | Sebep | Çözüm |
|---|---|---|
| Her yayından sonra herkes çıkış yapıyor | `JWT_SECRET` değişti (eski jetonların imzası tutmuyor) ya da Redis verisi silindi | Anahtarı sabit tut; anahtarı değiştirmek "herkesi çıkar" demektir — bazen bilerek yapılır (anahtar sızdıysa) |
| 15 dakikada bir çıkış | Eski sürüm (403/401 hatası) | PR #4 canlıda mı? |

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
