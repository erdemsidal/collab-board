# ADR 0010 — Canlıya çıkış öncesi güvenlik sıkılaştırmaları

- **Durum:** Kabul edildi
- **Tarih:** 2026-08-21

---

## Bağlam

Proje yereldeyken kabul edilebilir olan üç şey, canlıya çıkar çıkmaz açığa
dönüşüyor. Üçü de tek tek küçük; birlikte, uygulamayı halka açmayı sorumsuz
kılıyorlar.

1. **WebSocket herkese açıktı.** `setAllowedOriginPatterns("*")` — kodda
   `TODO(prod)` notuyla duruyordu.
2. **JWT anahtarı depoda açıktı.** `application.yml`'deki varsayılan değer
   GitHub'ı açan herkesin görebileceği bir yerde.
3. **Hiç hız sınırı yoktu.** Giriş, kayıt ve doğrulama postası uçları sınırsız
   çağrılabiliyordu.

Üçü de "sonra hallederiz" diye bırakılmıştı. Deploy adımı yaklaştığı için
sonrası şimdi.

## Karar 1 — Origin listesi daraltıldı

WebSocket el sıkışması, tarayıcının kullanıcı kimliğiyle gönderdiği sıradan bir
HTTP isteğidir. Liste herkese açıkken, kullanıcının ziyaret ettiği **herhangi
bir sayfa** arka planda bizim sunucumuza bağlantı açabilirdi.

Kimlik bir sonraki adımda (CONNECT çerçevesi, ADR 0005) doğrulandığı için tek
başına hesap ele geçirmeye yetmezdi. Ama origin denetimi ilk savunma hattıdır
ve bedeli sıfırdır — bir yapılandırma satırı.

```
app.cors.allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:8080,http://localhost:8081}
```

`setAllowedOrigins` kullanıldı, `setAllowedOriginPatterns` değil: ilki joker
kabul etmez, dolayısıyla `*` yazma hatası sessizce geçemez.

**REST tarafına CORS EKLENMEDİ.** Arayüz backend ile aynı adresten sunuluyor;
yapılandırma yokken tarayıcı zaten çapraz kaynaklı istekleri engelliyor. Oraya
bir CORS yapılandırması koymak, olmayan bir sorunu çözmek için kapı açmak olurdu.

## Karar 2 — Üretimde varsayılan JWT anahtarı yasak

Varsayılanın kendisi bilinçliydi: projeyi klonlayan biri hemen çalıştırabilsin
diye. Tehlikeli olan varsayılan değil, **onunla canlıya çıkmak** — o anahtarla
isteyen kendine istediği kullanıcı için geçerli token üretir, şifreye gerek
kalmaz.

Seçenekler:

| Yaklaşım | Neden seçilmedi |
|---|---|
| Varsayılanı tamamen kaldır | Klonlayan herkes önce anahtar üretmek zorunda kalır; kurulum sürtünmesi artar |
| Yoksa rastgele üret | Her yeniden başlatmada herkes çıkış yapar; geliştirmede sinir bozucu |
| **Profile göre davran** | **Seçilen** |

- `dev`: uyarı yeter, geliştirme aksamasın.
- `prod`: **açılış durdurulur.**

Uyarı yeterli değil çünkü dağıtım logları uzundur; sessiz bir satır kaybolur ve
açık aylarca fark edilmeden durur. **Açılmayan bir uygulama fark edilir.**

Kontrol `@PostConstruct` içinde, `ApplicationReadyEvent` içinde değil: ikincisi
tetiklendiğinde bağlam kurulmuş ve bağlantı noktaları açılmış olur, oradan hata
fırlatmak uygulamayı gerçekten durdurmaz.

## Karar 3 — Kimliksiz uçlara hız sınırı

### Neden uca göre farklı sınırlar?

Kötüye kullanımın maliyeti uca göre değişiyor:

| Uç | Sınır | Gerekçe |
|---|---|---|
| `login` | 10 / dakika | Şifre deneme saldırısı. İnsan şifresini yanlış yazar; dakikada onlarca deneme yazmaz |
| `register` | 5 / saat | Her istek veritabanına satır **yazar** ve posta gönderir |
| `resend-verification` | 3 / saat | **En pahalısı.** Bedeli bizim değil, saldırganın seçtiği kişinin gelen kutusu |
| `refresh` | 30 / dakika | Meşru istemci 15 dakikada bir çağırır; cömert sınır bile çalınmış jetonla deneme yanılmayı görünür kılar |

`resend` en sıkı olan, çünkü tek gerçek zararı **başkasına** veriyor: uygulama
bir spam aracına dönüştürülebilir.

### Neden kendi sayacımızı yazmadık?

Jeton kovası kolay görünür ama zaman penceresi kaymaları ve eşzamanlılık
ayrıntıları sessizce yanlış çalışır — ve yanlış çalıştığını fark etmezsin,
çünkü test etmesi zordur. **Bucket4j** eklendi.

Doldurma "greedy": jetonlar periyot boyunca damla damla geri gelir, sonunda
toptan değil. Toptan yenileme, sınıra takılan herkesin aynı anda yeniden
denemesine yol açardı (gürleyen sürü).

### Neden filtre zincirinin en başında?

`@Order(HIGHEST_PRECEDENCE)` — sınıra takılan istek kimlik doğrulama zincirine
hiç girmesin. BCrypt karşılaştırması **kasten pahalıdır**; saldırganın işlemci
zamanımızı harcamasına izin vermenin anlamı yok.

### Sayaç anahtarı: IP

Kusurlu bir seçim. Aynı ofisten çıkan herkes tek IP paylaşır; mobil ağlarda IP
sık değişir. Yine de kimlik gerektirmeyen uçlarda elimizdeki tek ölçüt bu ve
amaç kesin adalet değil, **ucuz kötüye kullanımı pahalı hâle getirmek**.

Vekil arkasında `getRemoteAddr()` yük dengeleyicinin adresini verir ve herkes
tek IP'den geliyormuş gibi görünür — sınır de facto küresel olurdu. Bu yüzden
`X-Forwarded-For`'un ilk değeri tercih ediliyor.

## Sonuçlar

**Kazandıklarımız**

- Üç açık da kapandı; deploy adımının önündeki güvenlik engeli kalktı.
- Yanlış yapılandırma artık sessiz değil: origin listesi açılışta loglanıyor,
  varsayılan anahtar üretimde uygulamayı durduruyor.
- Doğrulama postası ucu artık kötüye kullanılamıyor — ADR 0009'da "bilinen
  sınır" olarak bıraktığımız madde kapandı.

**Feda ettiklerimiz**

- Arayüz backend'den ayrı bir adreste sunulmak istenirse `CORS_ALLOWED_ORIGINS`
  verilmesi **ve** REST tarafına ayrıca CORS eklenmesi gerekir. Şu an bilinçli
  olarak yalnızca aynı-adres kurulumu destekleniyor.
- Bir bağımlılık daha: `bucket4j_jdk17-core`.
- Entegrasyon testlerinde sınır kapatılmak zorunda kaldı — hepsi aynı IP'den
  onlarca hesap açıyor. Sınırın kendisi ayrı bir sınıfta, ayarı tekrar açarak
  sınanıyor; yani kapatma testleri kör bırakmıyor.

**Bilinen sınırlar**

- **Kovalar bellekte.** Her sunucu kendi sayacını tutuyor; iki sunucuda etkin
  sınır iki katına çıkar. Redis zaten var, bucket4j'in Redis desteği de var;
  sunucu sayısı arttığında geçilmeli.
- **Kova haritası temizlenmiyor.** Politika sayısı sabit ve IP sayısı pratikte
  sınırlı, ama uzun ömürlü bir süreçte girdiler birikir. Süresi dolanları atan
  bir temizlik gerekiyor.
- **`X-Forwarded-For` uydurulabilir.** Vekil arkasında değilsek `getRemoteAddr()`
  zaten doğru, vekil arkasındaysak başlığı vekil yazar; kalan risk, sınırı
  atlatmak için başlık uyduran saldırgandır. Bu uçlarda kabul edilen artık risk.
- **WebSocket operasyonlarında hız sınırı yok.** Kimlik doğrulanmış bir kullanıcı
  saniyede binlerce operasyon gönderebilir. Kimliksiz uçlar kadar acil değil
  (saldırgan önce doğrulanmış bir hesap edinmeli) ama açık duruyor.
