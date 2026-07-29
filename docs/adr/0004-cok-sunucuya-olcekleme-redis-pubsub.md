# ADR 0004 — Çok sunucuya ölçekleme: Redis Pub/Sub köprüsü

- **Durum:** Kabul edildi
- **Tarih:** 2026-07-30
- **Bağlam fazı:** Faz 3 (ölçekleme)

---

## Bağlam

Tek sunucuda her şey çalışıyor. Ama uygulamadan **ikinci bir kopya** çalıştırdığımız
an gerçek zamanlı senkron kırılıyor. Bunu ölçtük (lokalde 8080 ve 8081):

```
Ayşe (Sunucu 1) bir kart ekledi:
  Ayşe (Sunucu 1) aldı mı?  ✓ EVET
  Cem  (Sunucu 2) aldı mı?  ✗ HAYIR — hiçbir şey gelmedi
  Sunucu 2'nin veritabanından okuduğu kartlar: ['Merhaba']   ← veri ORTAK
```

**Sebep:** Abonelik defteri (kim hangi topic'i dinliyor) her sunucunun **kendi
belleğinde**. Sunucu 1, Sunucu 2'ye bağlı istemcileri tanımaz. Veri paylaşılıyor
(aynı Postgres) ama **canlı haber** paylaşılmıyor.

Aynı hastalık presence'ta da var: bellekteki çevrimiçi listesi yarım kalır.

## Karar vericiler

- **Doğruluk:** Hangi sunucuya bağlanırsa bağlansın herkes her olayı görmeli.
- **Az kod değişikliği:** ADR 0002'de STOMP'u tam bu geçiş kolay olsun diye seçtik.
- **Altyapı sadeliği:** Redis zaten Docker Compose'da ve cache için kullanılıyor.
- **Öğrenme değeri:** Dağıtık gerçek zamanlı sistemlerin klasik problemi.

## Seçenekler

### 1) Sticky session (yapışkan oturum)
Yük dağıtıcı, bir kullanıcıyı hep aynı sunucuya yönlendirir.
- ➕ Kod değişikliği yok.
- ➖ **Problemi çözmez!** Aynı panodaki iki kullanıcı farklı sunucularda olabilir;
  yapışkanlık sadece "aynı kullanıcı aynı sunucuda kalsın" der. Ayrıca sunucu
  çökerse o kullanıcılar dağılır, yük dengesiz olur. **Elendi.**

### 2) Gerçek STOMP broker relay (RabbitMQ / ActiveMQ)
Spring'in `enableStompBrokerRelay` ile harici bir STOMP broker'a devretmek.
- ➕ Üretim seviyesinde en olgun çözüm; kalıcılık, ack, akış kontrolü hazır.
- ➖ Yeni bir ağır bileşen (RabbitMQ) kurmak/işletmek gerekir; bu projenin
  ihtiyacının çok üstünde. Redis zaten elimizde. **Şimdilik elendi**
  (üretimde ölçek büyürse doğru adım budur — README'de not edilecek).

### 3) Redis Pub/Sub köprüsü  ← SEÇİLEN
Yerleşik (bellek-içi) broker her sunucuda kalır, ama yayınlar **Redis üzerinden**
dolaşır:

```
Sunucu 1: operasyonu uygula → olayı REDIS kanalına publish et
                                      │
                        ┌─────────────┴─────────────┐
                   Sunucu 1 dinler            Sunucu 2 dinler
                        │                           │
              kendi istemcilerine push      kendi istemcilerine push
```

- ➕ Redis zaten var; ek altyapı yok.
- ➕ Uygulama mantığı aynı kalıyor: sadece "yayınla" çağrısı `convertAndSend`
  yerine `broadcast` oluyor.
- ➖ Redis artık **kritik bileşen**: çökerse canlı senkron durur (veri kaybolmaz,
  REST/veritabanı çalışmaya devam eder; istemci yenilerse günceli görür).
- ➖ Redis Pub/Sub **"ateşle ve unut"**: mesajı o an dinlemeyen almaz, kalıcılık yok.
  Bizim için sorun değil — kaçıran istemci zaten snapshot resync yapıyor (ADR 0003).

## Karar

**Redis Pub/Sub köprüsü** kuruyoruz.

- Tüm yayınlar tek bir Redis kanalından geçer: `collabboard:broadcast`.
- Redis'e giden zarf: `{ destination, payload }` — `destination` STOMP adresi
  (`/topic/board.42` veya `/topic/board.42/presence`), `payload` olayın JSON'u.
  Böylece **tek köprü** hem pano olaylarını hem presence'ı taşır.
- Yayınlayan sunucu **yerel olarak göndermez**; sadece Redis'e publish eder.
  Redis mesajı tüm abonelere (kendisi dahil) dağıtır → her olay her istemciye
  tam bir kez ulaşır, çift gönderim olmaz.
- **Presence Redis'e taşınır:** çevrimiçi liste artık Redis hash'inde
  (`presence:board:{id}`), böylece her sunucu tam listeyi görür.
- **Kişiye özel mesajlar relay edilmez:** reddetme bildirimi (ADR 0003) sadece
  isteği gönderen oturuma gider; o oturum zaten bu sunucuda. Redis'e koymak
  gereksiz trafik olurdu.

## Sonuçlar

- ➕ Kaç sunucu olursa olsun herkes her olayı görür; yatay ölçekleme mümkün.
- ➕ Uygulama kodu neredeyse değişmedi (ADR 0002'nin ödülü).
- ➕ Presence çok sunucuda doğru çalışır.
- ➖ Redis kritik bağımlılık haline geldi (tek hata noktası). Üretimde Redis'in
  kendisi de yedekli kurulmalı.
- ➖ **Bilinen eksik:** Bir sunucu çökerse Redis'teki presence kayıtları artık
  kalabilir ("hayalet kullanıcı"). Gerçek sistemler bunu TTL + periyodik kalp
  atışı (heartbeat) ile çözer; kapsamı büyütmemek için not olarak bırakıyoruz.
- ➖ Mesaj sırası: Redis kanalı sırayı korur ama iki sunucudan gelen operasyonların
  mutlak sırası ağ gecikmesine bağlıdır. Kartların son hâli veritabanındaki
  sürüme göre belirlendiği için (ADR 0003) tutarlılık korunur.

## İlgili

- Protokol seçimi (bu geçişi kolaylaştıran karar): [ADR 0002](0002-gercek-zamanli-protokol-stomp.md)
- Çakışma çözümü ve resync: [ADR 0003](0003-cakisma-cozumu-versiyon-kontrolu.md)
