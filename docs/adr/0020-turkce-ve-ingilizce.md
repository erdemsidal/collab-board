# ADR 0020 — Türkçe ve İngilizce

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-24

---

## Bağlam

Ürün yalnızca Türkçeydi: arayüzün metinleri `index.html` ve `landing.js` içine
gömülüydü, sunucunun hata mesajları ve postaları Türkçe yazılmıştı. Türkçe
bilmeyen biri bir panoya davet edildiğinde neyle karşılaştığını anlamıyordu.

İstenen: ürünün tamamı iki dilde; Türkçe tarayıcıya Türkçe, diğerlerine
İngilizce; üçüncü bir dil eklemek bir sözlük dosyası yazmaktan ibaret olsun.
Ürünün adı da yakında değişecek, ad her yerde tek bir sabitten gelmeli.

Cevaplanacak üç soru vardı: metinler nerede durur, dil nereden seçilir, sunucunun
Türkçe cümle olarak sakladığı geçmiş satırları ne olur.

## Karar

### 1. Arayüz: tek sözlük modülü

`i18n.js`: dil başına bir sözlük (`tr`, `en`), anahtarlar ekranlara göre iç içe.
Ekran kodu metin yazmaz, `t("home.cards", { count })` çağırır. Çoğul biçimler
`Intl.PluralRules` ile (`{ one, other }`), tarih, sayı, süre ve "2 dk önce" /
"2 min ago" `Intl.DateTimeFormat`, `Intl.NumberFormat`, `Intl.RelativeTimeFormat`
ile, o anki dilde. İngilizcede olmayan anahtar Türkçesine düşer ve konsola bir
kez yazılır. Çerçeve ya da kütüphane eklenmedi; arayüz zaten çerçevesiz.

Sözlük metni güvenilir sayılır (içinde `<b>` olabilir); kişiden gelen değerler
(ad, pano adı) parametre olarak verilmeden önce kaçışlanır.

### 2. Dil nereden seçilir

Sırayla: hesabın kayıtlı tercihi (`users.locale`), tarayıcıda saklanan seçim,
`navigator.languages` (desteklenen ilk dil, hiçbiri yoksa İngilizce).
`<html lang>` seçimi izler.

TR / EN düğmesi tanıtım sayfasının üst çubuğunda ve mobil menüsünde, giriş
ekranında, hesap menüsünde ve profilde. Seçim o anki ekranı sayfa yenilemeden
yeniden çizer; yazılmış alanlar yerinde kalır. Giriş yapılmışsa tercih hesaba
kaydedilir.

Giriş yapmadan seçilen dil sekmede tutulur ve girişten sonra hesaba yazılır:
kişinin az önce kendi eliyle yaptığı seçim, kayıtlı tercihten yenidir. Google
girişi sayfadan ayrılıp geri döndüğü için seçim bellekte değil `sessionStorage`'da.

### 3. Sunucu: anahtar taşıyan hatalar, `MessageSource`

Metinler `messages_tr.properties` ve `messages_en.properties` içinde (UTF-8).
Hata sınıfları metin değil anahtar taşır (`LocalizedException`); metne çeviren
yer dili bilir:

| Nerede | Dil |
|---|---|
| REST hatası, başarı mesajı, alan doğrulama, hız sınırı | İsteğin `Accept-Language` başlığı (arayüz her istekte gönderir). Başlık yoksa Türkçe, desteklenmeyen dilde İngilizce |
| Canlı kanaldaki reddetmeler (`/user/queue/errors`) | Gönderenin kayıtlı dili; STOMP'ta başlık yok |
| Postalar | Alıcının kayıtlı dili. Hesabı olmayan adrese davet: davet edenin dili |

Durum kodları ve JSON biçimleri değişmedi. Her mesaj MessageFormat'tan geçer
(kesme işareti dosyada `''`). İki dosyanın aynı anahtarları ve parametreleri
taşıdığını, koddaki her anahtarın dosyada olduğunu `MessageBundlesTest` sınar;
eksik çeviri derlemede yakalanmaz, kullanıcı anahtarın kendisini görürdü.

Hesabın dili kayıtta isteğin dilinden yazılır (V18): kayıt formunda arayüzün
dili, Google ile kayıtta dönüş isteğinin `Accept-Language`'ı. `PATCH /api/users/me`
`{"locale": "en"}` kabul eder; `tr` ve `en` dışındaki her değer `400`.

### 4. Geçmiş satırları parçalarıyla

Sunucu "'Süt al' kartını Done kolonuna taşıdı" cümlesini Türkçe yazıyor. Olayın
kendi verisi (`payload`) çevirmeye yetmiyor: taşıma olayında yalnızca kimlikler
var, kartın ve kolonun o anki adı yok. Bu yüzden satıra cümlenin değişen parçaları
da yazılır (V19, `board_activities.params`): `{"card": "Süt al", "column": "Done"}`.
Arayüz cümleyi türden ve parçalardan kendi dilinde kurar (son hareketler, ana
ekran, zaman yolculuğu). Parçası olmayan eski satırda ya da tanınmayan türde
Türkçe açıklama gösterilir; açıklama yazılmaya devam ediyor.

Okurken çevirmek olmazdı: kolonun adı bugünkü adı olurdu, silinmiş kart hiç
bulunamazdı.

### 5. Ürün adı tek yerde

Arayüzde `BRAND` (i18n.js), sunucuda `app.brand-name` (`APP_BRAND_NAME`,
varsayılan CollabBoard): sekme başlığı, tanıtım sayfası, giriş ekranı, üst
çubuk, postaların başlığı, konusu, düğmesi ve gönderen adı. Java paketleri ve
depo adı değişmedi. Google'ın izin ekranında görünen ad kodda değil, Google
Cloud Console'da.

## Üçüncü bir dil (ör. Almanca)

1. `i18n.js`: `en` sözlüğünü `de` olarak kopyalayıp çevir, `DICTIONARIES`'e ekle.
   Dil düğmesi "DE"yi kendiliğinden gösterir.
2. `messages_de.properties` yaz (`messages_en.properties`'in kopyası).
3. `AppLocale.SUPPORTED`'a `Locale.forLanguageTag("de")` ekle.
4. `MessageBundlesTest`'e üçüncü dosyayı ekleyip testleri çalıştır.

Sütun 5 karakter: "pt-BR" gibi bölgeli bir dil de sığar.

## Sonuçlar

**Kazandıklarımız**
- Arayüz, hata mesajları ve postalar iki dilde; dil hesapta durur, cihazdan cihaza taşınır.
- Geçmiş satırları dil değişince yeniden kurulur; eski satırlar kaybolmaz.
- Yeni dil kod değişikliği değil, çeviri işi.

**Feda ettiklerimiz / bilinen sınırlar**
- Kişinin yazdığı veri (pano, kolon, kart adları) çevrilmez; yeni panonun
  varsayılan kolonları hâlâ To Do, In Progress, Done. (Düzeldi: yeni panonun
  kolonları artık açan kişinin dilinde, [ADR 0021](0021-kart-anahtari-ve-pano-sablonlari.md).)
- Kişisel çalışma alanının adı açıldığı anda kişinin dilinde yazılır
  ("(kişisel)" / "(personal)"); dil sonradan değişse de ad değişmez.
- Türkçe metinlerde ürün adına gelen ekler ("CollabBoard'da", "CollabBoard'u")
  ad değişince elle gözden geçirilmeli.
- Postadaki saat Türkiye saatinde; kişinin saat dilimi bilinmiyor.
- Tarih seçicinin (`<input type="date">`) görünümü tarayıcının dilinden gelir.
- Postadaki İngilizce çoğul MessageFormat'ın `choice` biçimiyle; çoğul kuralı
  karmaşık bir dil (ör. Rusça) ICU gerektirir.
- `i18n.js` büyük bir dosya (iki dil, ~620 anahtar). Büyürse dil başına ayrı dosyaya bölünür.
