# ADR 0005 — WebSocket kimlik doğrulama: JWT, STOMP CONNECT frame'inde

- **Durum:** Kabul edildi
- **Tarih:** 2026-07-30
- **Bağlam fazı:** Faz 4 (auth / operasyonel olgunluk)

---

## Bağlam

Faz 1–3'te pano uçları ve WebSocket herkese açıktı (`permitAll`) — presence'ta
kimlikler sunucunun uydurduğu geçici adlardı ("Panda", "Kaplan"). Artık gerçek
kullanıcılar istiyoruz: **kim düzenliyor, panoda gerçekten kim var.**

REST tarafı zaten çözülü: istemci `Authorization: Bearer <token>` başlığını
gönderir, `JwtAuthenticationFilter` doğrular.

**WebSocket'te ise bir engel var:** Tarayıcının `WebSocket` API'si, el sıkışma
(handshake) isteğine **özel HTTP başlığı eklemeye izin vermez.** Yani REST'te
kullandığımız `Authorization` başlığını el sıkışmaya koyamayız.

## Karar vericiler

- **Token sızmamalı:** URL'ler sunucu loglarına, tarayıcı geçmişine, referrer
  başlıklarına yazılır.
- **Stateless kalmalı:** JWT tabanlı yapımızı bozmadan.
- **Mevcut parçaları kullanmalı:** `JwtTokenProvider`, `CustomUserDetailsService`.
- **Kimliği mesaj katmanında kullanabilmeliyiz:** presence ve audit için
  "bu mesajı kim gönderdi" bilgisi gerekli.

## Seçenekler

### 1) Token'ı URL'de sorgu parametresi olarak (`/ws?token=...`)
- ➕ En basit; el sıkışmada okunur.
- ➖ Token **loglara ve tarayıcı geçmişine** düşer; kazara paylaşılabilir.
  Güvenlik açısından kötü alışkanlık. **Elendi.**

### 2) Cookie tabanlı oturum
- ➕ Tarayıcı cookie'yi el sıkışmaya otomatik ekler.
- ➖ Mimarimiz **stateless JWT**; sunucu tarafı oturum tutmuyoruz ve CSRF'i bu
  yüzden kapattık. Cookie'ye dönmek bu kararları geri almak olurdu. **Elendi.**

### 3) Token'ı STOMP `CONNECT` frame'inin başlığında  ← SEÇİLEN
El sıkışma **anonim** kalır (sadece boruyu açar). Kimlik doğrulama bir üst
katmanda, **STOMP protokolünde** yapılır: istemci `CONNECT` frame'ine
`Authorization: Bearer <token>` başlığını koyar (bu, HTTP başlığı değil, STOMP
frame başlığıdır — tarayıcı kısıtlaması buna işlemez).

Sunucuda bir `ChannelInterceptor` gelen mesajları süzer; `CONNECT` görürse
token'ı doğrular ve oturuma bir `Principal` (kimlik) bağlar. Bu kimlik artık
o bağlantının tüm mesajlarında elimizdedir.

- ➕ Token URL'de/logda görünmez.
- ➕ Stateless JWT korunur; mevcut `JwtTokenProvider` yeniden kullanılır.
- ➕ Kimlik mesaj katmanında hazır: presence gerçek adı, audit gerçek kullanıcıyı
  yazabilir.
- ➖ El sıkışma kendisi anonim olduğu için `/ws` yolu Spring Security'de açık
  kalır; asıl kapı STOMP `CONNECT` olur. Bunu bilerek yapıyoruz (aşağıda not).

## Karar

**JWT, STOMP `CONNECT` frame başlığında taşınacak;** `WebSocketAuthInterceptor`
onu doğrulayıp oturuma `Principal` bağlayacak. Kimliksiz `CONNECT` reddedilir.

Bununla birlikte:
- `/api/boards/**` artık **kimlik doğrulaması ister** (Faz 1'de koyduğumuz
  `permitAll` kaldırıldı).
- `/ws` el sıkışması açık kalır — çünkü kimlik bir sonraki adımda (CONNECT)
  doğrulanır. Kimliksiz bağlantı hiçbir şey yapamaz: CONNECT reddedilir, dolayısıyla
  ne abone olabilir ne mesaj gönderebilir.
- Presence artık **gerçek kullanıcı adını** gösterir; renk kullanıcı kimliğinden
  türetilir (aynı kişi her girişte aynı renk). Geçici hayvan adları kaldırıldı.
- Aynı kullanıcı iki sekme açarsa presence listesinde **bir kez** görünür
  (kullanıcıya göre tekilleştirme) — iki sekme "iki kişi" demek değildir.

## Sonuçlar

- ➕ "Kim düzenliyor?" sorusu cevaplanır; presence anlamlı hale gelir.
- ➕ Pano verisi artık herkese açık değil.
- ➕ Audit (kim ne zaman ne yaptı) için sağlam bir kimlik zemini oluştu.
- ➖ Demo akışı uzadı: kullanıcı önce kayıt olup giriş yapmalı (frontend'e giriş
  ekranı eklendi).
- ➖ Access token'ın süresi dolarsa açık WebSocket bağlantısı **kendiliğinden
  düşmez** (kimlik CONNECT anında doğrulanır). Uzun oturumlar için istemcinin
  token'ı yenileyip yeniden bağlanması gerekir; kapsamı büyütmemek adına
  bilinen sınırlama olarak bırakıldı.
- ➖ Yetkilendirme henüz kaba: giriş yapan **her** kullanıcı **her** panoya
  erişebilir. Pano üyeliği/rolleri bu projenin kapsamı dışında.

## İlgili

- Protokol: [ADR 0002](0002-gercek-zamanli-protokol-stomp.md)
- Ölçekleme: [ADR 0004](0004-cok-sunucuya-olcekleme-redis-pubsub.md)
