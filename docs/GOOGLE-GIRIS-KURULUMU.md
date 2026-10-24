# Google ile giriş kurulumu

Giriş ekranındaki **"Google ile devam et"** düğmesinin çalışması için Google'dan
iki değer almamız gerekiyor:

| Değer | Neye benzer | Nereye gider |
|---|---|---|
| **Client ID** (istemci kimliği) | `1234567890-abc...apps.googleusercontent.com` | `GOOGLE_CLIENT_ID` |
| **Client secret** (gizli anahtar) | `GOCSPX-...` | `GOOGLE_CLIENT_SECRET` |

Bu ikisi verilmezse uygulama yine açılır; yalnızca Google düğmesi görünmez.

Toplam süre ~10 dakika. Google'ın paneli zaman zaman değişiyor; aşağıdaki
adımlar panelin bugünkü hâline (**Google Auth Platform**: Branding, Audience,
Clients, Data Access) göre yazıldı. Panelin dili Türkçe açılıyorsa düğme
adları çevrilmiş görünebilir; yerleri aynıdır.

> **İpucu:** Yerel ve canlı için ayrı proje açmana gerek yok. Tek bir istemci
> (client) oluşturup iki dönüş adresini birden eklemek yeterli; aynı Client ID
> ve secret iki yerde de kullanılır.

---

## 1. Proje oluştur

1. Tarayıcıda 👉 https://console.cloud.google.com adresini aç, Google hesabınla gir.
   İlk kez giriyorsan kullanım koşullarını kabul etmeni ister: ülkeni seç,
   kutuyu işaretle, **Agree and continue**.
2. Sayfanın en üstünde, Google Cloud logosunun sağında bir **proje seçici** var
   (proje yoksa *Select a project* yazar). Tıkla.
3. Açılan pencerenin sağ üstünde **New project** → *Project name* kutusuna
   `CollabBoard` yaz → **Create**.
4. Birkaç saniye sonra sağ üstte bir bildirim çıkar. Proje seçicide
   `CollabBoard` yazdığından emin ol; yazmıyorsa seçiciye tıklayıp onu seç.

## 2. Google Auth Platform'u başlat (uygulamanın kimliği)

1. Sayfanın üstündeki arama kutusuna `Google Auth Platform` yaz ve çıkan
   sonuca tıkla. (Ya da sol üstteki **☰** menü → **APIs & Services** →
   **OAuth consent screen**; aynı yere gider.)
2. *"Google Auth Platform not configured yet"* yazısını ve **Get started**
   düğmesini görürsün. **Get started**'a tıkla. Dört adımlı bir form açılır:
   - **App Information**
     - *App name*: `CollabBoard`
     - *User support email*: açılır listeden kendi Gmail adresini seç
     - **Next**
   - **Audience**: **External** seç (Internal yalnızca şirket hesapları için) → **Next**
   - **Contact Information**: kendi e-posta adresini yaz (Google proje
     hakkındaki duyuruları buraya yollar) → **Next**
   - **Finish**: *I agree to the Google API Services: User Data Policy*
     kutusunu işaretle → **Continue** → **Create**
3. Sol tarafta yeni bir menü belirir: **Overview, Branding, Audience,
   Clients, Data Access, Verification Center**. Bundan sonra hep bu menüyü
   kullanacağız.

> **Data Access**'e bir şey eklemen gerekmez. Uygulama yalnızca temel
> bilgileri ister (`openid`, `email`, `profile`: ad, e-posta, profil); bunlar
> "hassas" sayılmaz.

## 3. İstemciyi (client) oluştur

1. Sol menüde **Clients** → üstte **+ Create client**.
2. *Application type* listesinden **Web application** seç. Altında yeni alanlar açılır.
3. *Name*: `CollabBoard web` (yalnızca panelde görünür, kullanıcı görmez).
4. **Authorized JavaScript origins**: **boş bırak.** Google ile konuşan
   tarayıcı değil sunucu.
5. **Authorized redirect URIs** bölümünde **+ Add URI**'ye tıkla ve şunu
   yapıştır:

   ```
   http://localhost:8080/login/oauth2/code/google
   ```

   Tekrar **+ Add URI** → canlı adres:

   ```
   https://collab-board-production-36db.up.railway.app/login/oauth2/code/google
   ```

   Harfi harfine aynı olmalı: `http` ile `https` farklı, sondaki `/` farklı,
   `localhost` ile `127.0.0.1` farklı adreslerdir.
6. En altta **Create**.
7. *"OAuth client created"* penceresi açılır. Burada **Client ID** ve
   **Client secret** yazar.
   - İkisini de şimdi kopyala (yanlarındaki kopyalama simgesi) ya da
     **Download JSON** ile indir.
   - ⚠️ **Gizli anahtar yalnızca bu pencerede tam görünür.** Kapattıktan
     sonra panel yalnızca son dört karakterini gösterir. Kaybedersen sorun
     değil: **Clients** → istemcinin adı → **Add secret** ile yenisini üret,
     eskisini sonra sil.
   - Gizli anahtarı sohbete, ekran görüntüsüne ya da GitHub'a koyma.

> Google, dönüş adresi değişikliklerinin yerleşmesinin **5 dakika ile birkaç
> saat** sürebileceğini söylüyor. Hemen denediğinde `redirect_uri_mismatch`
> alırsan biraz bekle.

## 4. Kimler girebilir? (Audience)

1. Sol menüde **Audience**. *Publishing status* **Testing** yazar.
2. *Test users* bölümünde **+ Add users** → kendi Gmail adresini (ve denemek
   istediğin diğer adresleri) yaz → **Save**. En fazla 100 kişi.
3. Herkese açmaya hazır olduğunda aynı sayfada **Publish app** → açılan
   pencerede **Confirm**. Durum **In production** olur.

Bilmen gerekenler:
- Google'ın güncel belgesine göre uygulama yalnızca temel bilgileri
  (`openid`, `email`, `profile`) istediğinde test listesinde olmayanlar da
  Testing durumunda girebiliyor. Yine de `access_denied` görürsen (aşağıdaki
  tabloya bak) kişiyi test listesine ekle ya da uygulamayı yayınla.
- Temel bilgilerle yayınlamak (Publish) genelde Google incelemesi istemez.
  İnceleme (brand verification) yapılmadıkça Google'ın izin ekranında
  `CollabBoard` adı ve logosu yerine **uygulamanın alan adı** görünür. Bu normal.
- Panel *Branding* sayfasında **Authorized domains** isteyebilir. İsterse
  `collab-board-production-36db.up.railway.app` ekle (yerel adres için
  gerekmez).

## 5. Değerleri uygulamaya ver

### Yerelde (`.env`)

Proje kökündeki `.env` dosyasını aç (yoksa `cp .env.example .env`) ve en alta ekle:

```env
GOOGLE_CLIENT_ID=1234567890-abc...apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=GOCSPX-...
```

Tırnak koyma, `=` işaretinin iki yanında boşluk bırakma. `.env` git'e gitmez
(`.gitignore`'da).

Uygulamayı yeniden başlat. Açılış loglarında şu satırı görmelisin:

```
Google girişi açık — dönüş adresi: http://localhost:8080/login/oauth2/code/google
```

Bunun yerine `Dış hesapla giriş kapalı` yazıyorsa değerler okunmamıştır: dosya
adı `.env` mi, uygulama proje kökünden mi çalışıyor, iki değer de dolu mu?

Docker ile (`docker compose up -d`) çalıştırıyorsan da aynı `.env` okunur;
değişiklikten sonra `docker compose up -d` yeterli.

### Canlıda (Railway)

1. 👉 https://railway.com → projen → **collab-board** kutusu → **Variables**.
2. **+ New Variable** → sol kutuya `GOOGLE_CLIENT_ID`, sağ kutuya değeri → **Add**.
3. Tekrar **+ New Variable** → `GOOGLE_CLIENT_SECRET` → değeri → **Add**.
4. Aynı listede `APP_BASE_URL` değerinin tam olarak
   `https://collab-board-production-36db.up.railway.app` olduğunu kontrol et
   (`https`, sonda `/` yok). Dönüş adresi bundan üretilir.
5. Sayfanın üstünde **"Apply N changes"** çubuğu çıkar → **Deploy**.
6. collab-board → **Deployments** → en üstteki satır → **View logs** → arama
   kutusuna `Google girişi` yaz. Görmen gereken:
   `Google girişi açık — dönüş adresi: https://collab-board-production-36db.up.railway.app/login/oauth2/code/google`

## 6. Dene

1. Siteyi aç → giriş ekranında **Google ile devam et**.
2. Google hesap seçme ekranı açılır → hesabını seç → (ilk seferde) izin
   ekranında **Continue / Devam**.
3. Uygulamaya giriş yapmış olarak dönersin.
   - Bu adresle daha önce hesabın yoksa yeni hesap açılır ve "CollabBoard
     hesabın açıldı" postası gelir.
   - Bu adresle şifreli bir hesabın varsa ona bağlanır ve "CollabBoard
     hesabına Google ile giriş bağlandı" postası gelir. Eski şifren de
     çalışmaya devam eder.

---

## Sık karşılaşılan hatalar

| Ne görüyorsun | Sebep | Çözüm |
|---|---|---|
| Google sayfasında **Error 400: redirect_uri_mismatch** | Uygulamanın gönderdiği dönüş adresi Google'a kaydettiğinle harfi harfine aynı değil | Hata sayfasındaki **error details** bağlantısına tıkla: `redirect_uri=` sonrasında gönderilen adres yazar. **Clients** → istemci → *Authorized redirect URIs*'e tam olarak onu ekle → **Save**. Canlıda adres `http://` ile başlıyorsa `APP_BASE_URL` yanlıştır. Değişiklik birkaç dakika sonra geçerli olur |
| **Error 403: access_denied**, "uygulama Google doğrulama sürecini tamamlamadı" ya da "geliştirici size erişim izni vermedi" | Uygulama *Testing* durumunda ve bu Google hesabı test listesinde değil | **Audience** → *Test users* → **+ Add users** → adresi ekle. Ya da herkese açmak için **Publish app** |
| **Error 401: invalid_client** | Client ID ya da secret yanlış kopyalanmış (boşluk, eksik karakter) ya da başka bir istemcinin | Değerleri yeniden kopyala. Secret'ı kaybettiysen **Add secret** ile yenisini üret |
| Google'dan dönünce giriş ekranında "vazgeçtin" benzeri bir mesaj (`?oauth_error=cancelled`) | İzin ekranında **Cancel / İptal**'e basıldı | Tekrar dene, bu kez **Continue** |
| `?oauth_error=email_unverified` | Google bu hesabın e-posta adresini doğrulamamış (Gmail dışı adresle açılmış ve onaylanmamış Google hesapları) | Google hesabında adresi doğrula ya da başka hesap kullan |
| `?oauth_error=failed` | Google'da 10 dakikadan fazla beklendi, akış bir tarayıcıda başlayıp başka birinde bitti, tarayıcı çerezleri engelliyor ya da sunucu Google'a ulaşamadı | Aynı tarayıcıda baştan dene. Devam ederse loglarda `Dış hesapla giriş yarıda kaldı — kod:` satırına bak; `invalid_client` yazıyorsa yukarıdaki satır |
| **"Google ile devam et" düğmesi hiç görünmüyor** | `GOOGLE_CLIENT_ID` ya da `GOOGLE_CLIENT_SECRET` eksik | Loglarda `Dış hesapla giriş kapalı` ya da `client-id ve client-secret birlikte verilmeli` yazar. İkisini de ver, yeniden başlat |
| İzin ekranında `CollabBoard` yerine bir alan adı yazıyor | Google, ad ve logoyu ancak marka incelemesinden (brand verification) sonra gösteriyor | Sorun değil. İstersen **Verification Center**'dan inceleme başlatılabilir |
