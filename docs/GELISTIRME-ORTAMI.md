# Geliştirme ortamı: Sturnio'yu kendi bilgisayarında çalıştırmak

Bu belge Sturnio'yu Docker ile yerelde nasıl açıp kapatacağını, kaç kopya
çalıştırabileceğini, postaları nerede göreceğini ve Docker Desktop'ta gördüğün
konteynerlerin ne olduğunu anlatır.

Hepsi tek bir betikle yönetilir:

| Nerede | Komut |
|---|---|
| PowerShell | `.\scripts\dev.ps1 <komut>` |
| Git Bash | `./scripts/dev.sh <komut>` |

Komutları her zaman **proje klasöründen** (`C:\Users\Erdem-Victus\collab-board`)
çalıştır.

## Kısaca

```powershell
.\scripts\dev.ps1 up        # derle ve başlat → http://localhost:8080
.\scripts\dev.ps1 mail      # yakalanan postaları aç (Mailpit)
.\scripts\dev.ps1 db        # veritabanı bağlantı bilgileri (DBeaver)
.\scripts\dev.ps1 status    # ne çalışıyor?
.\scripts\dev.ps1 down      # durdur (verin kalır)
```

> **Bu bilgisayarda ilk kez mi?** Önce aşağıdaki
> [Eski kurulumdan geçiş](#1-bir-kez-eski-kurulumdan-geçiş) bölümünü yap. Bir
> kez yapılır.

---

## 1. Bir kez: eski kurulumdan geçiş

### Ne değişiyor?

| | Eski | Yeni |
|---|---|---|
| Konteyner adları | `collabboard-app`, `collabboard-postgres`, `collabboard-redis`, `collabboard-mailpit` | `sturnio-app-1`, `sturnio-postgres-1`, `sturnio-redis-1`, `sturnio-mailpit-1` |
| Docker Desktop'taki grup | `collab-board` | `sturnio` |
| Nasıl başlatılıyordu | `docker compose ... up -d` + Temp klasöründeki iki ek dosya (`port5433.yml`, `mailpit-test.yml`) | `.\scripts\dev.ps1 up` — ek dosya gerekmez |
| Veritabanı verisi | `collab-board_postgres_data` | **aynı birim**: `collab-board_postgres_data` |
| Redis verisi (oturumlar) | `collab-board_redis_data` | **aynı birim**: `collab-board_redis_data` |
| Kapılar | 8080, 5433, 8025 | **aynı**: 8080, 5433, 8025 |

Verin **taşınmaz, kopyalanmaz, silinmez**: yeni konteynerler aynı veri birimini
(volume) kullanır. Betik ana kopyayı açarken `collab-board_postgres_data`
birimini görürse onu kullanır ve Compose'a "bu birim dışarıdan" der; böylece
hiçbir Compose komutu onu kendiliğinden silemez. DBeaver bağlantını da
değiştirmen gerekmez.

### Adımlar

Docker Desktop açık olsun. PowerShell'de proje klasörüne geç:

```powershell
cd C:\Users\Erdem-Victus\collab-board
```

**Adım 1 — (önerilir) yedek al.** Veri birimine dokunulmuyor ama yine de bir
kopyası elinde olsun:

```powershell
docker exec collabboard-postgres pg_dump -U postgres -d collabboard -f /tmp/yedek.sql
docker cp collabboard-postgres:/tmp/yedek.sql "$HOME\sturnio-yedek.sql"
```

`C:\Users\Erdem-Victus\sturnio-yedek.sql` dosyası oluşur. (Yedeği PowerShell'in
`>` işaretiyle almıyoruz: o, dosyayı Postgres'in okuyamadığı bir kodlamayla yazar.)

**Adım 2 — eski konteynerleri kaldır.** Veri birimleri **kalır**:

```powershell
docker compose -p collab-board down
```

Bu komut dört eski konteyneri (`collabboard-*`) ve onların ağını
(`collab-board_default`) kaldırır. Sonuna **`-v` ekleme** — `-v` veri
birimlerini de siler.

**Adım 3 — yeni yığını başlat:**

```powershell
.\scripts\dev.ps1 up
```

İlk sefer imaj yeniden derlendiği için birkaç dakika sürebilir. Sonunda şuna
benzer bir özet görürsün:

```
Sturnio hazır — kopya: sturnio
  Uygulama         http://localhost:8080
  Posta (Mailpit)  http://localhost:8025  — kayıt ve doğrulama postaları burada
  Veritabanı       localhost:5433  (DBeaver; ayrıntı: .\scripts\dev.ps1 db)
  Veri             collab-board_postgres_data  (eski kurulumdaki verin)
```

**"eski kurulumdaki verin"** yazısını görmen önemli: doğru birim kullanılıyor demek.

**Adım 4 — kontrol et.** Tarayıcıda http://localhost:8080 → giriş yap →
panoların yerinde mi? DBeaver'daki bağlantın (localhost:5433) olduğu gibi çalışmalı.

**Adım 5 — (isteğe bağlı) artıkları temizle:**

```powershell
docker image rm collab-board-app      # eski uygulama imajı, ~600 MB
```

Temp klasöründeki `port5433.yml` ve `mailpit-test.yml` dosyalarına artık gerek yok.

### Bir şey ters giderse

- `up` "veri birimini şu an collabboard-postgres kullanıyor" derse: Adım 2
  yapılmamış. Betik bilerek durur — iki Postgres aynı veriyi aynı anda açarsa
  veri bozulur.
- `up` "Bu kapılar dolu" derse: eski konteynerler hâlâ çalışıyor (Adım 2) ya da
  başka bir program o kapıyı tutuyor; mesaj hangisi olduğunu yazar.
- Panolar boş görünüyorsa: büyük ihtimalle yığını betik yerine düz
  `docker compose up` ile açtın (bkz. [Neden düz docker compose değil?](#neden-düz-docker-compose-değil)).
  `.\scripts\dev.ps1 up` ile yeniden aç; verin geri gelir. Arada oluşan boş
  birimleri silebilirsin: `docker volume rm sturnio_postgres_data sturnio_redis_data`.
- Yedekten geri yüklemek gerekirse (yalnızca **boş** bir veritabanına):

  ```powershell
  docker cp "$HOME\sturnio-yedek.sql" sturnio-postgres-1:/tmp/yedek.sql
  docker exec sturnio-postgres-1 psql -U postgres -d collabboard -f /tmp/yedek.sql
  ```

---

## 2. Hangi konteyner ne?

Docker Desktop → **Containers** sekmesi. Her kopya kendi adıyla bir grup olarak
görünür (ana kopya: **sturnio**). Grubun okuna tıklayınca dört konteyner açılır:

| Konteyner | Ne işe yarar | Makineden adresi |
|---|---|---|
| `sturnio-app-1` | Uygulamanın kendisi (Spring Boot). Depodaki `Dockerfile`'dan derlenir — canlıdaki (Railway) imajın aynısı | http://localhost:8080 |
| `sturnio-postgres-1` | Veritabanı: kullanıcılar, panolar, kartlar, geçmiş. Asıl verin burada | `localhost:5433` (DBeaver) |
| `sturnio-redis-1` | Hızlı yardımcı bellek: oturumlar (giriş jetonları), önbellek, canlı yayın, kim çevrimiçi | Dışarı kapalı; yalnızca uygulama erişir |
| `sturnio-mailpit-1` | Posta yakalayıcı: uygulamanın gönderdiği postaları gerçek kutulara yollamak yerine yakalar | http://localhost:8025 |

Bir de bunlar var, Docker Desktop'ta başka sekmelerde görünürler:

- **Volumes** sekmesi: `collab-board_postgres_data` ve `collab-board_redis_data` —
  ana kopyanın verisi. Konteyner silinse de bunlar kalır.
- **Images** sekmesi: `sturnio-app` — derlenen uygulama imajı.
- Ağ: `sturnio_default` — dört konteynerin birbirini adıyla bulduğu iç ağ
  (Docker Desktop'ta görünmez; `docker network ls` ile görülür).

Her konteynerde Sturnio etiketleri var. Docker Desktop'ta konteynere tıkla →
**Inspect** → `Labels`:

```
com.sturnio.instance = sturnio      ← hangi kopya
com.sturnio.app-port = 8080
com.sturnio.db-port  = 5433
com.sturnio.mail-port = 8025
```

### Testlerin konteynerleri (gelip giden, garip adlı olanlar)

`./mvnw test` çalışırken Docker Desktop'ta bir anda birkaç konteyner belirir ve
test bitince kaybolur. Bunlar **Testcontainers**'ın:

| Görünen | Ne |
|---|---|
| `postgres:16-alpine` imajından, `nifty_mclaren` gibi rastgele adlı | Testlere özel, boş bir veritabanı |
| `redis:7-alpine` imajından, rastgele adlı | Testlere özel Redis |
| `testcontainers-ryuk-…` | **Ryuk**: temizlikçi. Testi çalıştıran programı izler; o bitince (ya da çökünce) diğer test konteynerlerini birkaç saniye içinde siler, sonra kendini de |

Bu yüzden gelip giderler. Senin verine dokunmazlar: kendi boş veritabanlarıyla
çalışırlar, rastgele kapılar kullanırlar ve işleri bitince yok olurlar.
Test çalışmıyorken bunlardan biri duruyorsa (ör. bilgisayar test sırasında
kapandıysa) silmek güvenlidir.

---

## 3. Başlatmak, durdurmak, temizlemek

| Komut | Ne yapar | Verin |
|---|---|---|
| `.\scripts\dev.ps1 up` | İmajı derler (yalnızca değişen kısmı), konteynerleri başlatır, uygulama hazır olunca adresleri yazar | Kalır |
| `.\scripts\dev.ps1 down` | Konteynerleri durdurur ve kaldırır | Kalır |
| `.\scripts\dev.ps1 restart` | Konteynerleri yeniden başlatır (kod değişikliği almaz) | Kalır |
| `.\scripts\dev.ps1 logs` | Uygulamanın günlüklerini canlı gösterir; çıkmak için **Ctrl+C**. Başka servis: `-Service postgres` | — |
| `.\scripts\dev.ps1 status` | Bu kopyanın konteynerleri + bilgisayardaki bütün Sturnio kopyaları | — |
| `.\scripts\dev.ps1 clean` | Konteynerleri, ağı ve derlenen imajı kaldırır | Kalır |
| `.\scripts\dev.ps1 clean -Volumes` | Üstekine ek olarak veri birimlerini de **siler**. Önce silinecekleri listeler, onay için kopyanın adını yazmanı ister | **Silinir** |
| `.\scripts\dev.ps1 config` | Hiçbir şey başlatmadan, kopyanın hangi ad, kapı ve veri birimiyle açılacağını gösterir | — |

**Kodu değiştirdin mi?** `.\scripts\dev.ps1 up` yeterli: yeni kodla imajı
yeniden derler ve uygulamayı yeniler. Bağımlılıklar değişmediyse bir iki dakika sürer.

**Docker Desktop'taki ▶ ve ■ düğmeleri** var olan konteynerleri başlatıp
durdurur; bunları kullanmak güvenli. Yığını sıfırdan *oluşturmak* için ise
betiği kullan.

**"running scripts is disabled on this system" hatası** alırsan PowerShell
betik çalıştırmaya izin vermiyordur. Bir kez şunu çalıştır:

```powershell
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
```

### Neden düz `docker compose` değil?

`docker-compose.yml` doğrudan da çalışır, ama ana kopyanın eski verisini
(`collab-board_*` birimleri) kullanacağını ona betik söyler. Düz
`docker compose up -d` yazarsan ana kopya **yeni ve boş** `sturnio_*`
birimleriyle açılır: verin yerinde durur ama görünmez. Betik ayrıca kapıların
boş olduğunu ve verinin başka bir konteynerde açık olmadığını denetler.

---

## 4. İkinci bir kopya çalıştırmak

Bir özelliği ana kopyana dokunmadan denemek, ya da bir ajanın kendi kopyasını
açması için:

```powershell
.\scripts\dev.ps1 up -Name deneme -Port 8090
```

Bu kopyanın **her şeyi ayrı**: kendi boş veritabanı, kendi Redis'i, kendi
Mailpit'i, kendi ağı. Docker Desktop'ta ayrı bir **deneme** grubu olarak
görünür; konteynerleri `deneme-app-1`, `deneme-postgres-1` …

Diğer kapılar uygulama kapısıyla aynı miktarda kayar, çakışmazlar:

| | Ana kopya (`-Port 8080`) | `-Port 8090` (fark 10) | `-Port 8094` (fark 14) |
|---|---|---|---|
| Uygulama | 8080 | 8090 | 8094 |
| Veritabanı | 5433 | 5443 | 5447 |
| Mailpit | 8025 | 8035 | 8039 |

`-Port` vermezsen kapı addan hesaplanır (8081–8129 arası) ve özet ekranında
yazılır. Kopya bir kez açıldıktan sonra sonraki komutlarda kapıyı tekrar
vermen gerekmez; betik konteynerlerin etiketinden okur:

```powershell
.\scripts\dev.ps1 status -Name deneme
.\scripts\dev.ps1 mail   -Name deneme
.\scripts\dev.ps1 down   -Name deneme
.\scripts\dev.ps1 clean  -Name deneme -Volumes    # verisiyle birlikte sil
```

Kopya adında küçük harf, rakam, `-` ve `_` kullanılabilir. `collab-board` adı
eski kurulumla karışmasın diye kapalı.

**Ajanlar için:** ajanlar onay soramayan bir ortamda çalışır. Kendi kopyalarını
`clean -Name <ad> -Volumes -Force` ile onaysız silebilirler. `-Force` **ana
kopyada (sturnio) çalışmaz**; ana kopyanın verisi yalnızca sen elle onay
verince silinir.

Bütün kopyaları görmek için: `.\scripts\dev.ps1 status` ya da

```powershell
docker ps -a --filter label=com.sturnio.instance
```

---

## 5. Postalar: Mailpit ve gerçek posta

### Varsayılan: Mailpit

Yerelde uygulamanın gönderdiği **her posta Mailpit'te yakalanır**; hiçbiri
gerçek bir posta kutusuna gitmez. Kayıt, doğrulama, şifre sıfırlama, davet,
bahsetme postalarının hepsi buraya düşer.

1. `.\scripts\dev.ps1 up`
2. http://localhost:8080 → **Kayıt ol** → herhangi bir adres yaz
   (ör. `deneme@ornek.com`; gerçek olması gerekmez).
3. `.\scripts\dev.ps1 mail` → tarayıcıda Mailpit açılır (http://localhost:8025).
4. Gelen postaya tıkla → **doğrulama bağlantısına** tıkla → hesap etkinleşir.

Postalar bellekte tutulur; Mailpit konteyneri yeniden başlayınca silinir.

Bu modun ayarları `docker/posta-mailpit.env` dosyasında; `.env`'deki posta
ayarlarının üstüne yazar.

### Gerçek posta (Brevo ya da Gmail)

`.env` dosyandaki posta ayarlarıyla gerçek gönderim:

- **Bu seferlik:** `.\scripts\dev.ps1 up -RealMail`
- **Kalıcı:** `.env` dosyasına şu satırı ekle, sonra `.\scripts\dev.ps1 up`:

  ```
  STURNIO_MAIL=gercek
  ```

Özet ekranında `Posta  GERÇEK gönderim` yazar. Mailpit'e geri dönmek için
`-RealMail` olmadan `up` (ve `.env`'e eklediysen o satırı sil ya da
`STURNIO_MAIL=mailpit` yap).

Gerçek gönderim için `.env`'de ya `MAIL_PROVIDER=brevo` + `BREVO_API_KEY`, ya da
Gmail için `MAIL_USERNAME` + `MAIL_PASSWORD` dolu olmalı:
[MAIL-KURULUMU.md](MAIL-KURULUMU.md).

---

## 6. Veritabanına bağlanmak (DBeaver)

```powershell
.\scripts\dev.ps1 db
```

```
  Sunucu (Host)   localhost
  Kapı (Port)     5433
  Veritabanı      collabboard
  Kullanıcı       postgres
  Parola          postgres
  JDBC adresi     jdbc:postgresql://localhost:5433/collabboard
```

Mevcut DBeaver bağlantın bu bilgilerle zaten tanımlı; değiştirmen gerekmez.
Yeni bir bağlantı için: DBeaver → **Database** → **New Database Connection** →
**PostgreSQL** → **Next** → yukarıdaki değerler → **Test Connection** → **Finish**.

İkinci bir kopyanın veritabanına bağlanacaksan `-Name` ver; kapısı farklıdır
(ör. 5443).

---

## 7. (İsteğe bağlı) Java'yı kendi bilgisayarında çalıştırmak

Kodu çok sık değiştiriyorsan uygulamayı Docker yerine doğrudan Java ile
çalıştırmak daha hızlıdır. Altyapıyı Docker açar, uygulamayı sen açarsın:

```powershell
.\scripts\dev.ps1 up -InfraOnly
```

Betik Postgres, Redis ve Mailpit'i açar, uygulama konteynerini durdurur ve
çalıştırman gereken komutu yazar. Ana kopya için:

```powershell
$env:DB_PORT="5433"; $env:REDIS_PORT="6380"; $env:MAILPIT_SMTP_PORT="1025"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev,mailpit"
```

Bu modda Redis (`127.0.0.1:6380`) ve Mailpit'in SMTP kapısı yalnızca bu
bilgisayara açılır. Bitince Java'yı **Ctrl+C** ile durdur, sonra
`.\scripts\dev.ps1 down`. Normal moda dönmek için `.\scripts\dev.ps1 up`.

**İki sunucuyla ölçekleme denemesi** (README'deki): `-InfraOnly` ile altyapıyı
aç, aynı komutu iki ayrı PowerShell penceresinde çalıştır; ikincisinin sonuna
`"-Dspring-boot.run.arguments=--server.port=8081"` ekle. İkisi aynı Redis'i
kullanır.

---

## 8. Başka projelerin durmuş konteynerleri

Docker Desktop'taki listede Sturnio dışında, başka projelerden kalmış **durmuş
(Exited)** konteynerler de görürsün (`protein_*`, `orchestra-*` …). Bunlar
işlemci ya da bellek kullanmaz, yalnızca diskte biraz yer tutar. Sturnio'yu
etkilemezler.

**Listelemek:**

```powershell
docker ps -a --filter status=exited --format "table {{.Names}}\t{{.Image}}\t{{.Status}}"
```

Docker Desktop'ta: **Containers** → üstteki **Only show running containers**
anahtarı kapalıyken hepsi görünür.

**Silmek — yalnızca istersen:**

- Tek bir konteyner: `docker rm <ad>` (ör. `docker rm nice_jepsen`) ya da
  Docker Desktop'ta satırdaki çöp kutusu.
- Durmuş **bütün** konteynerler birden:

  ```powershell
  docker container prune
  ```

  Ne yapar: o an çalışmayan **her projenin** bütün konteynerlerini siler
  (`protein_db` gibi başka projelerinkiler dahil). Önce `y/N` diye sorar.
  Veri birimlerini (volumes) ve imajları **silmez**; o projeler kendi
  `docker compose up` komutlarıyla konteynerlerini yeniden oluşturabilir. Yine
  de emin değilsen tek tek sil.

Disk kullanımını görmek için: `docker system df`. `docker system prune --volumes`
gibi komutlar **veri birimlerini de siler** — ne yaptığından emin değilsen kullanma.

---

## 9. Sorun giderme

| Betiğin mesajı | Ne demek | Ne yapmalı |
|---|---|---|
| `Docker'a ulaşılamadı` | Docker Desktop kapalı ya da açılıyor | Docker Desktop'ı aç, sol alttaki durum yeşile dönünce tekrar dene |
| `Bu kapılar dolu: ... kullanıyor` | Kapıyı başka bir konteyner ya da program tutuyor; mesaj kim olduğunu yazar | O şeyi durdur ya da `-Port` ile başka kapı seç |
| `... veri birimini şu an ... kullanıyor` | Aynı veri başka bir konteynerde açık | Önce o konteyneri durdur (eski kurulumsa: [Adım 2](#adımlar)) |
| Derleme sırasında `Could not transfer artifact ... repo.maven.apache.org` ya da `lookup registry-1.docker.io: no such host` | İnternet ya da DNS anlık gitmiş: derleme bağımlılık ya da temel imaj bilgisini indiremedi. Çalışan kopyan etkilenmez, hiçbir şey yarım kalmaz | İnterneti kontrol et, `up`'ı tekrar çalıştır |
| `Uygulama açılırken durdu` | Uygulama hata verip kapandı; son satırlar yazılır | `.\scripts\dev.ps1 logs` ile tamamına bak |
| `env file ... posta-....env not found` | `.env`'deki `STURNIO_MAIL` değeri yanlış yazılmış (ör. `gerçek`) | `mailpit` ya da `gercek` (ç'siz) yaz |

### Ayarlar (çoğu zaman gerekmez)

Betik bunları kendisi verir; `docker-compose.yml`'yi elle çalıştıracak olursan
anlamları:

| Değişken | Varsayılan | Anlamı |
|---|---|---|
| `STURNIO_APP_PORT` | `8080` | Uygulamanın makinedeki kapısı |
| `STURNIO_DB_PORT` | `5433` | Postgres'in makinedeki kapısı |
| `STURNIO_MAIL_UI_PORT` | `8025` | Mailpit web arayüzü |
| `STURNIO_MAIL` | `mailpit` | Posta modu: `mailpit` ya da `gercek` (`.env`'e yazılabilir) |
| `STURNIO_VOLUME_PREFIX` | kopyanın adı | Veri birimi adlarının öneki (ana kopyada betik `collab-board` verir) |
| `STURNIO_VOLUME_EXTERNAL` | `false` | `true`: birimi Compose oluşturmaz ve silemez |
| `STURNIO_REDIS_PORT`, `STURNIO_MAIL_SMTP_PORT` | `6380`, `1025` | Yalnızca `-InfraOnly` |

Canlı ortam (Railway) bunların hiçbirini kullanmaz; yalnızca `Dockerfile`'ı kullanır.
