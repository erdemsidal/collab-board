# ADR 0018 — Google ile giriş

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-24

---

## Bağlam

Kayıt olmak için bugün form doldurup postadaki bağlantıya tıklamak gerekiyor.
"Google ile devam et" bu iki adımı tek tıka indirir: adresi Google zaten
doğrulamış, şifre de gerekmez.

Uygulamanın üç özelliği kararı şekillendiriyor:

- **Oturumsuz.** Kimlik JWT ile taşınıyor, sunucuda HttpSession yok ve
  uygulama birden çok kopya hâlinde çalışabiliyor (ADR 0004). Spring'in OAuth2
  girişi ise varsayılan olarak bekleyen akışı HttpSession'da tutar.
- **Vekil arkasında.** Canlıda HTTPS'i Railway çözüyor; uygulamaya gelen istek
  `http://`. İstekten kurulan dönüş adresi Google'ın beklediğiyle tutmaz.
- **Jetonlar bellekte.** Arayüz oturum jetonlarını `Authorization` başlığıyla
  gönderiyor (ADR 0005); çerezle oturum yok.

## Karar

### 1. Sunucu tarafı yetkilendirme kodu akışı (OpenID Connect)

Tarayıcı `/oauth2/authorization/google` adresine gider, Google kişiyi
`/login/oauth2/code/google` adresine geri yollar; kodu jetona çevirmek ve
kimlik jetonunu (ID token) doğrulamak sunucunun işi. Spring Security imzayı,
yayıncıyı, hedef kitleyi ve nonce'u doğrular; üstüne PKCE eklendi.

Neden Google'ın JavaScript düğmesi (kimlik jetonunu tarayıcıda alıp sunucuya
yollamak) değil? Akış tamamen sunucuda kalınca istemci gizli anahtarı hiç
tarayıcıya inmez, aynı kod GitHub ya da Microsoft için de çalışır (yalnızca
uç adresleri ve bilgi eşleyicisi değişir, `OAuthProvider`) ve arayüze Google
betiği yüklenmez.

Google kurulu değilse (`GOOGLE_CLIENT_ID` boş) OAuth2 girişi hiç açılmaz;
`/api/auth/providers` boş liste döner, arayüz düğmeyi göstermez. Bu yüzden
kayıt Spring Boot'un `spring.security.oauth2.client.*` ayarlarından değil
koddan kurulur: o ayarlar boş kimlikle açılışta hata verir.

Dönüş adresi istekten değil `APP_BASE_URL`'den kurulur:
`<APP_BASE_URL>/login/oauth2/code/google`. Postalardaki bağlantılar da aynı
adresten geliyor; Google'a kaydedilen adres her zaman bununla aynı olur.

### 2. Bekleyen akış Redis'te, tarayıcıya bağlı

Google'a giden istek `state` değeriyle Redis'e yazılır (10 dakika). Dönüşü hangi
sunucu karşılarsa karşılasın akışı bulur; okurken siler, yani dönüş adresi
ikinci kez işe yaramaz. Saklanan şey sabit biçimli bir JSON; tarayıcıdan gelen
hiçbir şey çözümlenmez.

HttpSession'ın bir yan faydası vardı: akışı onu başlatan tarayıcıya bağlıyordu.
`state` tek başına bunu yapmaz. Saldırgan kendi Google hesabıyla bir akış
başlatıp dönüş adresini kurbana açtırırsa, kurban saldırganın hesabına girmiş
olur ve oraya yazdıklarını saldırgan okur (login CSRF). Bu yüzden akış başlarken
kısa ömürlü, `HttpOnly`, `SameSite=Lax` bir çerez (`cb_oauth`) yazılır; özeti
akışla birlikte saklanır, dönüşte ve kod takasında aynı tarayıcıdan gelmesi
şarttır. Çerez oturum değildir: sunucuda tutulmaz, yalnızca karşılaştırılır.

### 3. Adreste jeton değil, tek kullanımlık kod

Dönüş bir tarayıcı yönlendirmesi; sunucunun tarayıcıya bir şey iletebileceği
tek yer adres. Jetonlar adrese yazılsa tarayıcı geçmişine, vekil loglarına ve
`Referer` başlığına düşerdi. Bunun yerine `/?oauth=<kod>` yazılır: 256 bit,
60 saniye ömürlü, tek kullanımlık (Redis `GETDEL`), aynı tarayıcı çerezine bağlı.
Arayüz kodu `POST /api/auth/oauth/exchange` ile şifreli girişin döndürdüğü
yanıtın aynısına çevirir. Hata durumunda `/?oauth_error=` `cancelled`,
`email_unverified` ya da `failed`; ayrıntı yalnızca logda.

### 4. Hesap eşleştirme: önce kalıcı kimlik, sonra doğrulanmış adres

Tek işlemde, sağlayıcıdan bağımsız (`OAuthAccountService`):

1. Google adresi doğrulamamışsa (`email_verified` değilse) dur.
2. Bu Google hesabı (`sub`) daha önce bağlandıysa o hesap. Adres değişse de
   `sub` değişmez.
3. Aynı adresle (büyük/küçük harf fark etmez) bir hesap varsa ona bağla ve
   sahibine "Google ile giriş bağlandı, sen yapmadıysan şifreni değiştir"
   postası gönder.
4. Hiçbiri yoksa kayıt formuyla aynı yoldan (`AccountRegistrar`) yeni hesap
   aç, ama etkin ve şifresiz. Karşılama postası gönder. Kişisel alan ve bu
   adrese gönderilmiş bekleyen davetler, kayıt formundaki gibi ilk ihtiyaçta
   gelir.

Doğrulanmış adresle otomatik bağlama bilinçli bir tercih: adresi Google
doğruladı, bizim doğrulama postamızın kanıtladığı şeyin aynısı. Kişiye "önce
şifrenle gir, sonra bağla" demek, Google'ı seçmesinin sebebini ortadan kaldırırdı.

Bir istisna: adresi hiç doğrulanmamış (pasif) bir hesaba bağlanırken hesap
etkinleşir ama **kayıtta konan şifre silinir.** O şifreyi koyan kişi adresin
sahibi olduğunu kanıtlamadı; başkasının adresiyle önceden kayıt olup bekleyen
biri olabilir. Şifre kalsaydı, gerçek sahip Google ile girdiği an o kişi de
aynı hesaba şifreyle girebilirdi (pre-account takeover).

### 5. Şifresiz hesaplar

`users.password` artık boş olabilir (V16); bağlı dış hesaplar
`user_identities` tablosunda. Şifresiz hesapla şifreyle giriş, yanlış şifreyle
tıpatıp aynı reddedilir: aynı mesaj ve aynı süre (açılışta üretilen,
eşleşmeyen bir BCrypt özeti kullanılır). Kişi şifreyi "Şifremi unuttum"
postasıyla ya da profilden, mevcut şifre sorulmadan belirleyebilir; bundan
sonra eski kurallar geçerlidir. `/api/users/me` ve giriş yanıtı `hasPassword`
ve `providers` alanlarını taşır.

## Sonuçlar

**Kazandıklarımız**
- Tek tıkla kayıt ve giriş; doğrulama postası beklemek yok.
- Oturumsuz yapı korunuyor: giriş akışı hiçbir sunucuda durum bırakmıyor,
  hangi kopyanın karşıladığı önemsiz.
- Jetonlar hiçbir zaman adreste görünmüyor.
- Yeni sağlayıcı: bir eşleyici sınıfı ve iki ortam değişkeni.

**Feda ettiklerimiz / bilinen sınırlar**
- Google hesabı ele geçirilen kişinin CollabBoard hesabı da ele geçer
  (aynı adresle otomatik bağlandığı için). Güvenlik postası bunu görünür
  kılıyor ama engellemiyor.
- Bağlı Google hesabını kaldırmanın bir yolu henüz yok.
- Akış başka bir sekmede başlatılıp bitirilirse sorun yok; başka bir
  tarayıcıda bitirilirse (çerez yok) `failed` döner. Çerezleri tamamen kapalı
  tarayıcıda Google girişi çalışmaz.
- Açık oturumu ele geçiren biri, şifresiz hesaba şifre ekleyebilir. Diğer
  oturumlar o an kapanır; sahibi bunu çıkış yapılmasından fark eder ve
  "Şifremi unuttum" ile şifreyi değiştirir.
