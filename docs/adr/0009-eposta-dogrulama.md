# ADR 0009 — E-posta doğrulama ve posta gönderimi

- **Durum:** Kabul edildi
- **Tarih:** 2026-08-21

---

## Bağlam

Kayıt olan herkes anında içeri girebiliyordu. E-posta adresinin gerçekten o kişiye
ait olduğu hiç doğrulanmıyordu. Bu üç somut soruna yol açıyor:

1. **Davet akışı kırılgan.** Panoya üye eklerken adresle arama yapıyoruz
   (`BoardMemberService`). Adres doğrulanmamışsa yanlış kişiyi panoya davet etmek
   mümkün — hatta adresin sahibi olmayan biri o adresle kayıt olup gelecek davetleri
   toplayabilir.
2. **Sahte hesap maliyeti sıfır.** Var olmayan adreslerle sınırsız hesap açılabilir.
3. **Bildirim gönderemeyiz.** Son tarih hatırlatması, atama bildirimi gibi
   özelliklerin ön koşulu, adresin çalıştığını bilmek.

Ayrıca uygulamanın hiç posta gönderme yeteneği yoktu; bu, doğrulamanın da
ötesinde bir eksiklikti.

## Karar vericiler

- **Kayıt akışı kırılmamalı.** Posta sunucusu çökse bile kayıt tamamlanmalı.
- **Mevcut kullanıcılar dışarıda kalmamalı.** Kural yalnızca bundan sonrası için.
- **Postalar gerçekten gitmeli.** Doğrulamanın anlamı, adresin sahibine ulaşan
  bir bağlantıdır; yerel bir yakalayıcı bunu taklit eder, sağlamaz.
- **Kural tek yerde durmalı.** Girişe elle "doğrulandı mı" kontrolü eklemek, o
  kontrolü unutulabilecek ikinci bir yol yaratırdı.

## Seçenekler

### Doğrulama nerede zorlanacak?

#### 1) Login servisinde elle kontrol
`AuthService.login` içinde `if (!user.isEnabled()) throw ...`
- ➖ Kural kodun içinde bir noktaya gömülü kalır. Yarın ikinci bir giriş yolu
  (OAuth, API anahtarı) eklenirse kontrol oraya taşınmayı bekler. **Elendi.**

#### 2) Spring Security'nin kendi mekanizması — **seçilen**
`users.enabled` alanı zaten vardı ve `CustomUserDetailsService` onu zaten
`.disabled(!user.isEnabled())` olarak bildiriyordu — sadece hiç `false` olmuyordu.
- ➕ Kayıtta `enabled(false)` demek yetti; `DaoAuthenticationProvider` girişi
  kendiliğinden reddediyor.
- ➕ Kural kimlik doğrulamanın merkezinde; her giriş yolu için geçerli.
- ➖ Fırlatılan `DisabledException`'ın varsayılan mesajı kullanıcıya "kimlik
  bilgileri hatalı" gibi görünüyordu. `GlobalExceptionHandler`'a ayrı bir dal
  eklendi: şifresi doğru olan birine "şifren yanlış" demek, onu yanlış yöne iter.

### Jeton nerede saklanacak?

Refresh token'lar Redis'te (ADR 0004'ün altyapısı). Doğrulama jetonu için de
Redis akla gelir ama:

- Doğrulama bağlantısı e-postada **günlerce** bekleyebilir.
- Redis bizde önbellek ve mesaj köprüsü; yeniden başlatılması olağan bir olay.
- Jeton uçarsa kullanıcı hesabına erişemez hâle gelir.

**Karar: Postgres.** Kalıcı olması gereken şey kalıcı bir yerde durur.

`used_at` sütunu satırı silmek yerine işaretliyor. "Bu bağlantı zaten kullanılmış"
ile "böyle bir bağlantı yok" kullanıcı için farklı durumlar: ilkinde kişi doğru
yoldadır, ikincisinde bir yanlışlık vardır. Aynı mesajı vermek yardımcı olmaz.

### Posta gönderimi kritik yolda mı?

#### 1) Senkron — kayıt postanın gitmesini bekler
- ➖ SMTP kesintisi **kayıt kesintisine** dönüşür. Kullanıcı üç saniye düğmeye
  bakar; hesabı çoktan açılmıştır. **Elendi.**

#### 2) Asenkron ve hataya toleranslı — **seçilen**
`@Async` + gönderim hatası yalnızca loglanır.
- ➕ Kayıt postadan bağımsız.
- ➕ Posta gitmezse kullanıcı "yeniden gönder" diyebilir; çıkmaz yok.
- ➖ Kullanıcı postanın gitmediğini anında öğrenemez. Kabul edildi: alternatifi,
  gidip gitmediğini öğrenmek için beklemek.

### Varsayılan gönderim gerçek mi, yerel mi?

İlk kurulumda varsayılanı yerel bir posta yakalayıcıya (**Mailpit**) bağlamıştık:
kimlik bilgisi gerekmediği için projeyi klonlayan herkes hemen çalıştırabiliyordu.

**Bu yanlış varsayılandı.** Doğrulama özelliğinin bütün amacı postanın kullanıcının
GERÇEK adresine ulaşması; varsayılanı yakalayıcıya bağlamak, özelliğin kendisini
varsayılan olarak devre dışı bırakmak anlamına geliyordu. Üstelik sessizce: her şey
çalışıyor görünüyor, postalar tek bir yerel gelen kutusuna düşüyordu.

**Düzeltilmiş karar:**

- **Varsayılan gerçek SMTP'dir.** Kimlik bilgileri `.env` dosyasından gelir.
- **Mailpit isteğe bağlı bir profile taşındı** (`--profile mailpit`). Yalnızca
  posta şablonu üzerinde çalışırken anlamlı: elli deneme postasını gerçek
  kutulara yollamanın anlamı yok.
- **Ayar eksikse sessiz kalınmaz.** Açılışta SMTP bağlantısı sınanır
  (`MailConnectionCheck`) ve sonuç açıkça loglanır.

Son madde şundan önemli: gönderim asenkron ve hataya toleranslı. Ayarlar yanlışsa
uygulama sorunsuz açılır, kayıt başarılı görünür, ama **hiç kimse içeri giremez**
ve sorun tek bir log satırında kalır. Açılış sınaması bu sessizliği bozuyor —
yanlış yapılandırma ilk kullanıcı denemeden anlaşılıyor.

Sınama uygulamayı **durdurmaz**: panolar postadan bağımsız çalışıyor ve kayıtlı
kullanıcılar giriş yapabiliyor. Aynı sebeple posta, sağlık kontrolüne de dahil
edilmedi (`management.health.mail.enabled: false`) — SMTP'ye ulaşılamaması
uygulamayı DOWN gösterseydi barındırma platformu onu yeniden başlatmaya kalkardı.

## Karar

1. Kayıt hesabı **pasif** açar (`enabled = false`) ve doğrulama postası gönderir.
2. Doğrulama jetonu Postgres'te, tek kullanımlık, **24 saat** ömürlü, 256 bit
   rastgelelikte (`SecureRandom`, Base64-URL).
3. Bağlantı `GET /api/auth/verify?token=...` — postadan gelip tarayıcıda açıldığı
   için kimliksiz ve GET.
4. Yeniden gönderim, kullanıcının önceki jetonlarını **geçersiz kılar.**
5. Yeniden gönderim ucu, adres kayıtlı olmasa da **aynı cevabı** döner.
6. Mevcut kullanıcılar etkilenmez; göç kimseyi pasife çekmez.

### Jeton neden URL'de? ADR 0005 bunun tersini söylemiyor muydu?

ADR 0005'te JWT'yi URL'ye koymayı reddetmiştik: URL'ler sunucu loglarına, tarayıcı
geçmişine ve proxy kayıtlarına düz metin düşer. Burada bilinçli olarak ödünç
veriyoruz, çünkü bu jeton farklı bir şey:

| | JWT (ADR 0005) | Doğrulama jetonu |
|---|---|---|
| Ömür | 15 dakika, sürekli yenilenir | 24 saat, tek sefer |
| Yetki | Hesabın tamamı | Yalnızca hesabı etkinleştirmek |
| Kullanım | Her istekte | Bir kez |
| Sızarsa | Hesap ele geçer | Hesap etkinleşir, o kadar |

Ayrıca jeton işlendikten sonra arayüz adres çubuğundan siliyor
(`history.replaceState`), böylece tarayıcı geçmişinde kalmıyor.

## Sonuçlar

**Kazandıklarımız**

- Adresler artık gerçek. Davet akışı güvenilir hâle geldi.
- Uygulamanın posta gönderme yeteneği var — bildirimler bunun üzerine kurulabilir.
- Yanlış posta yapılandırması açılışta yakalanıyor, ilk kullanıcı denemeden.

**Feda ettiklerimiz**

- Kayıt akışı bir adım uzadı. Kullanıcı artık kaydolur olmaz içeri giremiyor;
  dönüşüm oranı düşer. Doğrulanmamış adreslerin getirdiği sorunlara karşı
  kabul edilebilir bir bedel.
- Testlerin ortak yardımcısı (`registerAndLogin`) artık doğrulama adımını da
  atıyor. Bunu test'e özel bir arka kapıyla değil, **gerçek `/api/auth/verify`
  ucunu çağırarak** yaptık; böylece her test aynı zamanda akışı da doğruluyor.
- İki bağımlılık daha: `spring-boot-starter-mail` ve `spring-dotenv`. İkincisi,
  repoda zaten var olan `.env` sözleşmesini (`.env.example`, `.gitignore`,
  `docker-compose`) uygulamanın da okuyabilmesi için eklendi — o dosya belgeleniyor
  ama çalışma anında kimse okumuyordu.
- Projeyi klonlayan biri artık çalıştırmadan önce bir SMTP kimliği kurmak zorunda.
  Kayıt akışını denemenin başka yolu yok; doğrulama zaten bunu gerektiriyor.

**Bilinen sınırlar**

- Süresi dolmuş jetonlar tabloda birikiyor; temizleyen bir zamanlanmış iş yok.
  İndeks hazır (`idx_email_verification_expires_at`), iş henüz yazılmadı.
- Yeniden gönderim ucunda **hız sınırı yok.** Aynı adrese arka arkaya istek
  atılabilir. Bu, planlanan hız sınırı çalışmasının (Bucket4j) ilk müşterisi olmalı.
- Şifre sıfırlama yok. Altyapı (posta + jeton deseni) artık hazır; akış yazılmadı.
- Posta şablonu kodun içinde düz metin. İkinci ve üçüncü posta geldiğinde bir
  şablon motoru gerekecek.
