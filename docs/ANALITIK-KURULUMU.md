# Yönetim paneli ve ziyaretçi analitiği kurulumu

İki ayrı şey var:

| Ne | Neyi gösterir | Nereden | Ne gerekir |
|---|---|---|---|
| **Yönetim paneli** (Sturnio içinde) | Kim kayıt oldu, kim kullanıyor, kaç pano, kart ve yorum var | Sturnio'nun kendi veritabanı | Bir değişken: `APP_ADMIN_EMAILS` |
| **Umami** (ayrı bir site) | Siteye kaç kişi geldi, nereden geldi, hangi ülke, telefon mu bilgisayar mı | Ziyaretçinin tarayıcısı | Ücretsiz bir Umami hesabı ve bir değişken: `ANALYTICS_UMAMI_WEBSITE_ID` |

İkisi birbirinden bağımsız. Yalnızca birini de kurabilirsin. Neden böyle
yapıldığı: [ADR 0024](adr/0024-yonetim-paneli-ve-ziyaretci-analitigi.md).

---

## 1. Yönetim panelini aç: `APP_ADMIN_EMAILS`

Paneli yalnızca bu listedeki e-postalar görür. Diğer herkes için panel yoktur:
menüde görünmez, adresi bilen biri denese bile sunucu "böyle bir adres yok" der.

Önce şunu kontrol et: bu e-postayla Sturnio'da bir hesabın olmalı ve e-postan
**doğrulanmış** olmalı (kayıt sonrası gelen bağlantıya tıklamış, ya da Google ile
girmiş olmalısın).

### Yerelde (`.env`)

1. Proje kökündeki `.env` dosyasını aç (yoksa `cp .env.example .env`).
2. En alta şu satırı ekle, kendi adresinle:

   ```env
   APP_ADMIN_EMAILS=senin.adresin@gmail.com
   ```

   Birden fazla kişi olacaksa virgülle ayır:
   `APP_ADMIN_EMAILS=senin.adresin@gmail.com,ortagin@ornek.com`.
   Büyük/küçük harf fark etmez. Tırnak koyma.
3. Uygulamayı yeniden başlat:
   - Docker ile: `docker compose up -d`
   - Kod yazarken: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`
4. Açılış loglarında şu satırı görmelisin:
   `Yönetim paneli açık: 1 sahip adresi tanımlı.`

### Canlıda (Railway)

1. 👉 https://railway.com → projen → **collab-board** kutusu → **Variables**.
2. **+ New Variable** → sol kutuya `APP_ADMIN_EMAILS`, sağ kutuya e-posta
   adresin → **Add**.
3. Sayfanın üstünde **"Apply N changes"** çubuğu çıkar → **Deploy**.
4. collab-board → **Deployments** → en üstteki satır → **View logs** → arama
   kutusuna `Yönetim paneli` yaz. Görmen gereken:
   `Yönetim paneli açık: 1 sahip adresi tanımlı.`
   `kapalı` yazıyorsa değişken boş ya da yanlış yere eklenmiş.

## 2. "Yönetim"i aç

1. Sturnio'da sağ üstteki **adına** (avatarına) tıkla.
2. Açılan menüde **Profilim**'in hemen altında **Yönetim** var. Tıkla.

Menüde göremiyorsan: sayfayı yenile (F5). Hâlâ yoksa çıkış yapıp yeniden gir
ve `APP_ADMIN_EMAILS`'teki adresin giriş yaptığın adresle aynı olduğunu kontrol et.

### Sayfada ne var

| Bölüm | Ne anlatır |
|---|---|
| **Kullanıcılar** | Toplam hesap; bugün, son 7 ve son 30 günde yeni kayıtlar |
| **Günlük kayıtlar** | Son 30 günün grafiği. Bir çubuğun üstüne gel (ya da grafiğe tıklayıp ok tuşlarını kullan), o günün sayısı çıkar |
| **Etkinlik** | Şu an bir panoyu açık tutan kişi; bugün, 7 ve 30 günde siteyi kullananlar |
| **Geri gelenler** | 7 ile 30 gün önce kayıt olanlardan son 7 günde yeniden gelenlerin oranı |
| **Giriş yöntemi, doğrulama, dil** | Hesapların dağılımı |
| **İçerik** | Çalışma alanı, pano, kart ve yorum sayısı; son 7 günde eklenenler |
| **Son kayıtlar** | En yeni 50 hesap: ad, e-posta, ne zaman, nasıl kayıt oldu, doğrulandı mı, en son ne zaman geldi, kaç panosu var |
| **En hareketli panolar** | Son 7 günde en çok işlem yapılan 10 pano (yalnızca ad ve sayılar) |

Bilmen gerekenler:

- "Bugün" ve "son 7 gün" **senin saatine** göre (tarayıcın Türkiye saatindeyse
  gece yarısı Türkiye'de başlar). Son 7 gün bugünü de içerir.
- "Son görülme" en fazla 5 dakika geriden gelir; her tıklamada yazılmaz.
- Bu özellik yayınlanmadan önce açılmış hesapların "son görülme"si, panolardaki
  son hareketlerinden tahmin edildi. Hiç hareketi olmayanlar bir sonraki girişlerine
  kadar "Henüz yok" görünür.
- Silinmiş pano, kart ve yorumlar sayılmaz.
- Telefonda tablolar kendi kutusunda yana kayar.

> **Gizlilik:** Bu sayfada kullanıcıların adı ve e-postası görünüyor. Gizlilik
> politikasında "site sahibi hesap bilgilerini (ad, e-posta, kayıt ve son giriş
> tarihi) hizmeti işletmek için görür" gibi bir cümle bulunmalı (KVKK/GDPR).

## 3. Umami: ziyaretçileri gör

Umami çerez kullanmayan, sade bir ziyaretçi sayacı. Çerez olmadığı için
"çerezleri kabul et" bandına genelde gerek kalmaz.

### 3.1 Ücretsiz hesap aç

1. 👉 https://cloud.umami.is/signup adresine git, hesap aç (e-posta ve şifre).
2. **Önemli:** ücretsiz planın sınırlarına kayıt olurken
   👉 https://umami.is/pricing sayfasından bak. Bugün (Ekim 2026) yazanlar
   kabaca: **ayda 100 bin olay** (her sayfa açılışı ve her özel olay bir
   olay), **1 site**, verinin **yaklaşık 6 ay** saklanması. Bu sınırlar
   değişebilir; buradaki rakamlara değil, o sayfaya güven.

### 3.2 Siteyi ekle

1. Umami'de soldaki menüden **Websites**'e tıkla.
2. Sağ üstteki **Add website** düğmesine bas.
3. **Name:** `Sturnio` (istediğin bir ad).
   **Domain:** sitenin adresi, `https://` ve sondaki `/` olmadan:
   `collab-board-production-36db.up.railway.app`
   (Kendi alan adını bağladıysan onu yaz.)
4. **Save**.

### 3.3 Website ID'yi kopyala

1. Listede siteni bul, yanındaki **Edit** düğmesine bas.
2. **Tracking code** bölümünde şuna benzer bir satır var:

   ```html
   <script defer src="https://cloud.umami.is/script.js" data-website-id="4fb7c5a0-1234-4d1e-9a7b-0c2d3e4f5a6b"></script>
   ```

3. Yalnızca `data-website-id="…"` içindeki değeri kopyala (tırnaklar hariç):
   `4fb7c5a0-1234-4d1e-9a7b-0c2d3e4f5a6b`.

   Betik satırının tamamını bir yere yapıştırmana **gerek yok**; Sturnio onu
   kendisi ekler (ve gizlilik için birkaç ayarla birlikte).

### 3.4 Railway'e ver

1. 👉 https://railway.com → projen → **collab-board** → **Variables**.
2. **+ New Variable** → sol kutuya `ANALYTICS_UMAMI_WEBSITE_ID`, sağ kutuya
   kopyaladığın değer → **Add**.
3. `ANALYTICS_UMAMI_SCRIPT_URL`'i **ekleme**: varsayılanı Umami Cloud'un adresi.
   Yalnızca ileride Umami'yi kendi sunucunda çalıştırırsan gerekir.
4. **"Apply N changes"** → **Deploy**.
5. **Deployments** → en üstteki satır → **View logs** → arama kutusuna
   `Ziyaretçi analitiği` yaz. Görmen gereken:
   `Ziyaretçi analitiği açık (Umami): cloud.umami.is`
   `geçersiz` yazıyorsa değeri yanlış kopyalamışsın (boşluk, tırnak).

### 3.5 Çalıştığını gör

1. Reklam engelleyicisi olmayan bir tarayıcıda (ya da gizli pencerede) siteyi aç.
2. Umami'de siteni aç. Birkaç saniye içinde ziyaretçi sayısında görünürsün
   (üstte canlı ziyaretçi sayısı da var).
3. Sitenin tanıtım sayfasında **Kayıt ol**'a bas. Umami'de **Events**
   bölümünde `cta-start` görünür.

Sturnio'nun gönderdiği özel olaylar:

| Olay | Ne zaman |
|---|---|
| `cta-start` | Tanıtım sayfasındaki büyük **Kayıt ol** düğmelerinden birine basıldı (`place`: `hero` üstteki, `bottom` alttaki) |
| `signup` | Biri e-postayla kayıt oldu |
| `signup-google` | **Google ile devam et**'e basıldı (`screen`: kayıt ya da giriş ekranı) |

Olaylarla birlikte e-posta, ad ya da herhangi bir kimlik **gitmez**. Adresin
`?...` kısmı da gitmez: bağlantılardaki kart numaraları ve şifre sıfırlama
jetonları Umami'ye ulaşmaz.

### Kendi ziyaretlerini sayma

Siteyi en çok sen açacaksın; kendini saymamak için:

1. Siteyi kendi tarayıcında aç.
2. **F12** → **Console** sekmesi.
3. Şunu yapıştır, **Enter**:

   ```js
   localStorage.setItem('umami.disabled', 1)
   ```

Her tarayıcı ve cihaz için bir kez. Geri açmak için:
`localStorage.removeItem('umami.disabled')`.

### Yerelde

Yerelde `ANALYTICS_UMAMI_WEBSITE_ID`'yi **boş bırak**: yoksa kendi denemelerin
de canlı sitenin ziyaretçisi sayılır. Boşken sayfalar hiçbir betik yüklemez.

> **Gizlilik:** Umami çerez kullanmaz ve kişiyi tanımlayan bir şey saklamaz.
> Yine de gizlilik sayfasında anılmalı: "Ziyaret istatistikleri için çerezsiz
> Umami kullanıyoruz; sayfa, geldiğin site, ülke ve cihaz türü toplanır."

## Sorun giderme

| Belirti | Neden | Çözüm |
|---|---|---|
| Menüde **Yönetim** yok | Adres listede değil ya da farklı yazılmış | Logda `Yönetim paneli açık` var mı bak; adresi giriş yaptığın adresle karşılaştır, sonra sayfayı yenile |
| Logda `Yönetim paneli kapalı` | Değişken boş ya da başka bir servise eklenmiş | Değişkeni **collab-board** kutusuna ekle, yeniden yayınla |
| Panelde "Panel yüklenemedi" | Sunucu ya da veritabanı geçici olarak cevap vermedi | **Yenile**'ye bas; sürerse Railway loglarına bak |
| "Şu an bir panoda" kutusunda "Okunamadı" | Redis'e ulaşılamadı | Railway'de Redis servisinin çalıştığını kontrol et |
| Umami'de ziyaretçi yok | Reklam engelleyici betiği engelliyor ya da değişken eksik | Gizli pencerede, engelleyicisiz dene; logda `Ziyaretçi analitiği açık` var mı bak |
| Umami'de kendi ziyaretlerin görünüyor | `umami.disabled` ayarlanmamış | Yukarıdaki "Kendi ziyaretlerini sayma" adımları |
