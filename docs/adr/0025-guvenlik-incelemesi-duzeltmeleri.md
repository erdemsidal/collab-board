# ADR 0025 — Güvenlik incelemesi düzeltmeleri: canlı bağlantı izin listesi, gerçek istemci IP'si, oturum aileleri, bağlantıların kapatılması

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-26
- **Kapsam:** güvenlik incelemesindeki K1, Y1, Y3, O1, O3, O4, O5 ve O6'nın kod kısmı

---

## Bağlam

Canlıyı yeniden açmadan önce yapılan bir güvenlik incelemesi, birbirine bağlı
birkaç açık buldu. Önem sırasıyla:

1. **Joker abonelik (K1).** Abonelik süzgeci yalnızca `/topic/board.<sayı>`
   biçimini denetliyor, geri kalan her adresi serbest bırakıyordu. Spring'in
   bellek içi broker'ı ise içinde `*`, `**`, `?` ya da `{..}` olan adresi
   **kalıp** olarak kaydeder. `/topic/**`'a abone olan biri bütün panoların,
   `/queue/**`'a abone olan biri herkesin bildirimlerini alıyordu.
2. **Broker adresine doğrudan gönderim (Y1).** İstemci `SEND`'i sunucu ucuna
   değil doğrudan `/topic/board.42`'ye yollayabiliyordu; broker mesajı sunucudan
   geçirmeden bütün abonelere dağıtıyordu. Üye olmayan biri panoya sahte bir
   yorum ("Faturayı şu IBAN'a yatırın") basabilirdi.
3. **Jetonsuz bağlanma (incelemede yoktu, bu çalışmada bulundu).** STOMP
   1.2'de bağlanma çerçevesinin iki adı var: `CONNECT` ve `STOMP`. Spring
   ikisini de bağlantı sayıyor; bizim kimlik süzgecimiz yalnızca `CONNECT`'e
   bakıyordu. `STOMP` çerçevesiyle bağlanan (ya da hiç bağlanmadan doğrudan
   `SUBSCRIBE` yollayan) bir istemci kimliksiz içeri giriyordu. Eski kodda
   sınandı: **hesabı bile olmayan** biri `/topic/**`'a abone olup başka bir
   kişinin panosundaki kartı canlı okudu. Yani K1, kayıt bile gerektirmiyordu.
4. **Uydurulabilen IP ve hesap başına sınır yokluğu (Y3).** Giriş sınırı
   `X-Forwarded-For`'un ilk değerine bakıyordu; o değeri istemci yazar. Ham
   adres karşılaştırıldığı için `/api/auth/%6cogin` sınırsız giriş ucuna
   ulaşıyordu. Kova haritası hiç boşalmıyordu. Hesap başına sınır yoktu.
5. **Profile bağlı koruma (O1).** Uygulama depodaki herkese açık JWT
   anahtarıyla yalnızca `prod` profilinde açılmayı reddediyordu. Profil
   unutulursa açılıyor, metrikler ve Swagger da açık kalıyordu.
6. **Çıkış ve refresh token (O3).** "Çıkış yap" sunucuya haber vermiyordu.
   Döndürme üç ayrı adımdı (atomik değil). Çalınan bir jetonun geri gelmesi
   fark edilmiyordu.
7. **Açık bağlantılar (O4).** Kimlik yalnızca CONNECT'te, üyelik yalnızca
   SUBSCRIBE'da denetleniyordu. Panodan çıkarılan ya da şifresi değişen
   kişinin açık bağlantısı yayını almaya devam ediyordu.
8. **Başlıklar (O5).** HSTS yazılmıyordu (uygulama vekilin arkasında HTTPS'i
   göremiyordu); Referrer-Policy ve Permissions-Policy yoktu.
9. **Dependabot kapalı (O6).**

## Karar 1 — Canlı bağlantıda "varsayılan olarak reddet" (K1, Y1, 3. madde)

`StompAccessInterceptor` (eski `WebSocketSubscriptionAuthInterceptor`) artık
bir **izin listesi** uyguluyor. Liste, uygulamanın gerçekten kullandığı
adreslerden çıkarıldı (`collab-api.js`'teki abonelikler ve sunucudaki
`@MessageMapping` uçları):

| Çerçeve | İzin verilen | Ek koşul |
|---|---|---|
| SUBSCRIBE | `/topic/board.<id>`, `/topic/board.<id>/presence`, `/topic/board.<id>/cursors` | panonun üyesi olmak |
| SUBSCRIBE | `/user/queue/errors`, `/user/queue/notifications` (yalnızca bu tam biçimler) | — |
| SEND | `/app/board/<id>/ops`, `/app/board/<id>/cursor`, `/app/board/<id>/presence/join`, `/app/session/renew` | yetkiyi uçların kendisi denetler |
| UNSUBSCRIBE, DISCONNECT | serbest (yalnızca kendi oturumu) | — |
| ACK, NACK, BEGIN, COMMIT, ABORT | **reddedilir** (uygulama kullanmıyor) | — |

- Pano numarası `[1-9][0-9]{0,17}`: başında sıfır yok, Long'a sığar, harf yok.
- Listede olmayan her şey reddedilir: joker ve kalıplar, sayı olmayan numara,
  başkasının kuyruğu (`/user/<başkası>/queue/...`, `/queue/...-user<oturum>`).
- `STOMP` çerçevesi `CONNECT` gibi doğrulanıyor; CONNECT dışındaki her çerçeve
  kimliği belli bir oturum istiyor.

**Neden Spring Security'nin mesaj yetkilendirmesi değil?**
`@EnableWebSocketSecurity` CONNECT'e CSRF jetonu şartı getiriyor; kimliği
başlıkta taşıyan bu uygulamada elle yazılmış, tek dosyalık bir liste daha sade
ve okunur.

**Ret nasıl görünür?** STOMP'ta bir isteği reddetmenin yolu ERROR çerçevesidir;
kural gereği sunucu ardından bağlantıyı kapatır. Meşru arayüz yalnızca listedeki
adresleri kullandığı için bundan etkilenmez. Ama arayüzün **neden** kapandığını
bilmesi gerekiyordu: eskiden her kapanmayı "jeton bitti" sanıp yeniler ve yeniden
bağlanırdı; panodan çıkarılan biri için bu sonsuz bir döngü olurdu. Bu yüzden
ERROR'a bir başlık eklendi (`StompErrorHandler`, `CloseReason`):

| `x-reason` | Anlamı | Arayüz ne yapar |
|---|---|---|
| `forbidden` | adres ya da çerçeve listede yok | (meşru arayüz görmez) |
| `unauthenticated` | jeton yok, geçersiz ya da iptal | yeniler, yeniden bağlanır; yenileyemezse çıkış |
| `board-access` | panoya erişim yok ya da kalktı | yeniden bağlanmaz, panodan çıkar |
| `session-revoked` | şifre değişti/sıfırlandı | yenilemeden yeniden bağlanmayı dener (aşağıda) |
| `token-expired` | bağlantının jetonu bitti, yenilenmedi | yeniler, yeniden bağlanır |

## Karar 2 — Gerçek istemci IP'si ve güvenilen vekil (Y3, O5)

`server.forward-headers-strategy: native` açıldı. Tomcat'in `RemoteIpValve`'ı:

- `X-Forwarded-For`'u **sağdan sola** okur;
- yalnızca **güvenilen vekilden** gelen istekte başlığı dikkate alır;
- güvenilen vekillerin eklediği adresleri atlar ve ilk güvenilmeyen adresi
  istemci sayar;
- `X-Forwarded-Proto: https` ise isteği HTTPS bilir (HSTS bu sayede yazılır).

`RateLimitFilter` artık yalnızca `request.getRemoteAddr()` kullanıyor.

**Güvenilen vekil kararı.** İki uç da tehlikeli:

- Railway'in vekiline güvenmezsek uygulama her isteği vekilin adresinden gelmiş
  sanar: **bütün kullanıcılar tek bir kovayı paylaşır**, on kişi dakikada bir
  kez yanlış şifre yazınca herkes kilitlenir.
- Her şeye güvenirsek başlık yine uydurulabilir.

Seçilen: **Tomcat'in varsayılanı**, yani özel ağ adresleri (10/8, 172.16/12,
192.168/16, 100.64/10, 127/8, IPv6 `fc00::/7` ve yerel adresler). Railway'in
vekilinin uygulamaya özel ağdan bağlanması bekleniyor (başkalarının paylaştığı
örneklerde 100.64/10); bu buradan **doğrulanamadı**, yayından sonra kontrol
edilmeli. Herkese açık bir adres hiçbir zaman güvenilen sayılmaz. Saldırgan sol
tarafa ne yazarsa yazsın, vekilin sağa eklediği gerçek adres istemci sayılır.

Ayar değiştirilebilir, çünkü Railway'in iç yapısını buradan göremiyoruz:

- `TRUSTED_PROXIES`: ek güvenilen vekiller, düzenli ifade (ör. önüne Cloudflare
  konursa onun aralıkları).
- `CLIENT_IP_HEADER`: vekil gerçek adresi başka bir başlıkta veriyorsa
  (ör. `X-Real-IP`).

Yayından sonra doğrulama adımları `docs/CANLIYA-ALMA.md`'de: 429 logundaki
`ip:` değeri kendi IP'n olmalı (vekilin özel adresi değil) ve uydurma
`X-Forwarded-For`'la 11. deneme yine 429 almalı.

**Diğer Y3 kararları:**

- Yol, çözülmüş hâliyle eşleşiyor (`UrlPathHelper`); `%6c` hilesi kapandı.
- Kovalar sınırlı bir Caffeine önbelleğinde: en fazla 50.000, bir saat
  kullanılmayan atılır. Bir saat boşta kalan kova zaten tamamen dolmuştur;
  atmak hiçbir sınırı gevşetmez.
- **Hesap başına sınır** (`AccountLoginThrottle`): bir e-posta için 15 dakikalık
  sabit pencerede 10 hatalı giriş, sonra pencere bitene kadar 429. IP'ye bakmaz.
  - **Hesap kilitleme yok:** pencere uzamaz, katlanmaz. Saldırgan bir hesabı
    en fazla 15 dakika bekletebilir; kişi "Şifremi unuttum"la hemen girebilir
    (sıfırlama sayacı siler). Kalıcı kilit, saldırganın elinde bir hizmet
    engelleme aracı olurdu.
  - Kilitliyken şifre hiç denetlenmez; doğru şifre de bekler.
  - Sayaç Redis'te, anahtar e-postanın SHA-256 özeti (küçük harf, boşluksuz).
- **Yaygın şifre listesi:** kayıtta, şifre değişiminde ve sıfırlamada en yaygın
  ~10.000 şifre (SecLists NCSC listesi, 8+ karakterliler) ve kısa bir Türkçe ek
  liste reddediliyor. En az 8 karakter kuralı aynen duruyor; büyük harf/rakam/
  sembol kuralı **eklenmedi** (NIST 800-63B'nin önerdiği gibi).

## Karar 3 — Koruma profilden bağımsız (O1)

Depodaki JWT anahtarıyla uygulama yalnızca **açıkça yerel** bir çalışmada
açılır; üç koşulun üçü de tutmalı:

1. `dev` ya da `test` profili açık,
2. `prod` açık değil,
3. `APP_BASE_URL` bu makineyi gösteriyor (`http://localhost`, `127.x`, `::1`).

Profil unutulursa da uygulama **açılmaz**. Ayrıca:

- `Dockerfile` varsayılan olarak `SPRING_PROFILES_ACTIVE=prod` ile açılır.
  Railway zaten `prod` veriyor; değişken ortamdan ezilebilir.
- Yerel yığın (`scripts/dev.ps1`, `docker-compose.yml`) `dev` profilini ve
  `http://localhost:<kapı>` adresini kendisi veriyor; hiçbir şey değişmedi.
  Seçilen yol "compose dev'i açıkça söyler" yolu: korumaya "yerel compose'u
  tanı" diye bir istisna koymak, canlıda yanlışlıkla tetiklenebilecek bir
  kapı açmak olurdu.
- Ortak ayar artık dışarıya yalnızca `/actuator/health`'i açıyor ve Swagger'ı
  kapalı tutuyor; metrikleri ve Swagger'ı yalnızca `dev` profili açıyor.
- Entegrasyon testleri `test` profiliyle çalışıyor.

## Karar 4 — Oturum aileleri ve yeniden kullanımın yakalanması (O3)

- **Çıkış sunucuya gidiyor.** Arayüzdeki "Çıkış yap" önce `/api/auth/logout`'u
  çağırır; ağ hatası çıkışı engellemez. Uç erişim jetonu istemiyor: kimlik
  kanıtı refresh token'ın kendisi (süresi dolmuş bir sekme de çıkabilsin).
- **Aile (family).** Her giriş yeni bir oturum ailesi açar; jeton
  `<aile>.<sır>` biçiminde (16 + 32 rastgele bayt). Her yenilemede sır değişir,
  aile aynı kalır. Redis'te aile başına tek kayıt: sahibi ve **şu anki jetonun
  SHA-256 özeti**. Jetonun kendisi Redis'e yazılmıyor.
- **Atomik döndürme.** Karşılaştırma ve değiştirme tek bir Lua betiğinde.
  Aynı jetonla aynı anda gelen iki istekten yalnızca biri başarılı olur.
- **Yeniden kullanım.** Ailesi açıkken ESKİ bir jeton gelirse, jeton iki kişinin
  elinde demektir; hangisinin gerçek kullanıcı olduğunu bilemeyiz. **Ailenin
  tamamı kapanır** ve `GÜVENLİK:` önekiyle uyarı loglanır. İki taraf da yeniden
  giriş yapar; şifreyi bilmeyen saldırgan dışarıda kalır. Kişinin başka
  cihazlardaki oturumları (başka aileler) etkilenmez.
- **Tolerans payı yok.** Yanıtı ağda kaybolan bir yenileme o tarayıcıyı bir kez
  çıkışa düşürür. Pay koymak, saldırganın jetonu kullanıcıdan hemen önce
  kullandığı durumu gizlerdi. Arayüz aynı tarayıcıdaki sekmelerin yenilemesini
  zaten sıraya koyuyor (Web Locks); meşru istemci aynı jetonu iki kez göndermez.

**Geçiş:** eski biçimdeki jetonlar (düz UUID) tanınmıyor. Yayından sonra herkes
bir kez yeniden giriş yapar.

## Karar 5 — Açık bağlantıların kapatılması (O4)

Üç tetik; hepsi istemciye bir ERROR (`x-reason` ile) gönderir, Spring ardından
WebSocket'i kapatır (`RealtimeSessions`):

1. **Panodan çıkarılma:** kişinin **o panoya abone** bağlantıları kapanır
   (`board-access`). Çalışma alanı üyeliğiyle erişimi sürüyorsa dokunulmaz.
   Kişisel kanalı (bildirimler) açık kalır.
2. **Şifre değişimi ve sıfırlama** (`SessionTermination`): bütün refresh
   token'lar kapanır, kişinin **bütün** bağlantıları kapanır
   (`session-revoked`) ve o ana kadar verilmiş erişim jetonları canlı bağlantı
   için geçersiz sayılır (`AccessTokenCutoff`, Redis'te jeton ömrü kadar). Böylece
   koparılan bağlantı 15 dakika boyunca eski jetonla geri gelemez.
   - Şifreyi değiştiren cihaz yeni jetonunu hemen alır; bağlantısı da kapanır
     ama arayüz **yenilemeden** birkaç saniye içinde yeni jetonla bağlanır.
     (Yenilemeye kalksa, şifre değişikliğinin yanıtı henüz gelmemişse eski
     refresh token'la reddedilip çıkışa düşebilirdi.)
   - Diğer cihazlar eski jetonla bağlanmayı dener, reddedilir, yenileyemez ve
     çıkış ekranına düşer.
3. **Jetonun süresi:** CONNECT'te jetonun bitiş anı oturuma yazılır. 30
   saniyede bir yoklanır; bitişin üzerinden 60 saniye geçmiş ve yenilenmemiş
   bağlantı kapanır (`token-expired`).

**Neden bağlantıyı her 15 dakikada koparmıyoruz?** En basit yol "jeton
bitince kapat, istemci yeniden bağlansın" olurdu; ama her kullanıcı 15 dakikada
bir "Bağlantı koptu" uyarısı görür, çevrimiçi listesinde çıkıp girer, imleci
kaybolurdu. Bunun yerine arayüz, jetonunu her yenilediğinde (zaten ömrünün
%80'inde yeniliyor) yenisini açık bağlantılara da bildiriyor
(`/app/session/renew`). Sunucu yalnızca aynı kişiye ait ve kesimden sonra
verilmiş jetonu kabul ediyor. Sonuç: meşru kullanıcı hiç kopmuyor; jetonunu
yenileyemeyen (oturumu kapatılmış) bağlantı en geç jetonun bitişi + 60 saniye +
30 saniyelik yoklama aralığında kapanıyor.

**Birden çok sunucu:** 1 ve 2 Redis'teki ayrı bir kanaldan (`collabboard:
session-control`) bütün sunuculara gider; her sunucu kendi bağlantılarını
kapatır (ADR 0004'teki köprü gibi). 3 her sunucuda yereldir.

**Yeniden kullanım (Karar 4) bağlantı kapatmıyor:** aile, canlı bağlantılarla
eşleşmiyor. Saldırganın elindeki erişim jetonu en fazla ömrü kadar yaşar;
canlı bağlantısı da jetonunu yenileyemeyeceği için en geç o sürede kapanır.

## Karar 6 — Güvenlik başlıkları (O5)

- **HSTS:** 1 yıl, `includeSubDomains`, **preload yok**. Yalnızca HTTPS olarak
  görülen isteğe yazılır; vekilin `X-Forwarded-Proto: https`'i sayesinde artık
  görülüyor. Güvenilmeyen bir adresten gelen sahte `X-Forwarded-Proto` işe yaramaz.
- **Referrer-Policy:** `strict-origin-when-cross-origin`.
- **Permissions-Policy:** kamera, mikrofon, konum, ödeme, USB ve benzerleri kapalı.
- `X-Content-Type-Options: nosniff` ve `X-Frame-Options: DENY` zaten vardı.
- **CSP bilerek yok.** Önce CDN betikleri ve fontlar yerele alınmalı ve satır
  içi betik taşınmalı (O2); ayrı bir iş.

## Karar 7 — Dependabot (O6)

`.github/dependabot.yml`: Maven haftalık, GitHub Actions aylık. Yalnızca PR
açar; uyarılar ve güvenlik güncellemeleri GitHub ayarlarından açılmalı. Spring
Boot yükseltmesi bu değişikliğin parçası değil.

## Sonuçlar (feda ettiklerimiz)

- **Herkes bir kez yeniden giriş yapar** (refresh token biçimi değişti).
- REST'te eski erişim jetonu şifre değişiminden sonra en fazla 15 dakika
  geçerli kalıyor; her istekte Redis'e sormak bu ölçekte gereksiz bulundu.
  Canlı bağlantıda ise hemen kesiliyor.
- Hesap başına sınır, bir saldırganın bir hesabı 15 dakikalık aralıklarla
  bekletmesine izin veriyor (15 dakikada 10 hatalı deneme yeter). Kalıcı kilitten
  kat kat iyi; gerçek bir saldırı görülürse ikinci bir anahtar (hesap + IP)
  eklenebilir.
- IP sınırı hâlâ sunucu başına bellekte; iki sunucuda etkin sınır iki katı.
  Hesap başına sınır Redis'te, sunucu sayısından bağımsız.
- Vekil ayarının doğruluğu ancak canlıda sınanabilir (CANLIYA-ALMA.md).

## Kapsam dışı

O2 (CSP, CDN dosyalarının yerele alınması), Spring Boot yükseltmesi, D
maddeleri, O8 (gizlilik sayfası) ve "Part B" özellikleri ayrı işler.
