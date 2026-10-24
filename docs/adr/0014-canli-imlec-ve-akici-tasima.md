# ADR 0014 — Canlı imleçler ve akıcı taşıma

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-23

---

## Bağlam

Canlı senkron doğru çalışıyordu ama **göze kötü** görünüyordu:

1. Karşı taraf bir kartı taşıdığında pano baştan çiziliyordu (`renderColumns`).
   Her kartın CSS'inde bir belirme animasyonu olduğu için **bütün kartlar aynı
   anda yeniden beliriyor**, taşınan kart da yeni yerine **ışınlanıyordu**.
2. Biri bir kartı sürüklerken karşıdan bir olay gelirse pano yeniden çiziliyor,
   sürüklenen kart elde kalıyordu.
3. Panoda kimin nerede olduğunu yalnızca üstteki avatarlar söylüyordu. Biri bir
   kartı taşımaya başladığında diğerleri bunu ancak bırakıldıktan sonra görüyordu.

## Karar

### 1. Akıcı yeniden çizim: FLIP

Çizmeye dokunmadan, çizimin **öncesini ve sonrasını** ölçüyoruz:

- **First:** çizmeden önce her kartın ve kolonun ekrandaki yeri ölçülür.
- **Last:** pano yeniden çizilir, yeni yerler ölçülür.
- **Invert + Play:** her öğe eski yerine geri itilir ve oradan yenisine
  300 ms'de kaydırılır (Web Animations API).

Yalnızca **gerçekten yeni** kart belirme animasyonu oynar. Kolon kayarsa içindeki
kartların kendi hareketinden kolonunki düşülür; yoksa kartlar iki kat yol alırdı.
`prefers-reduced-motion` açık olan kullanıcıda animasyon yapılmaz.

Neden çizim yöntemini değiştirmedik (tek tek DOM güncellemesi, sanal DOM)?
Çizim kodu sade ve doğru; sorun yalnızca görünüştü. FLIP onu bozmadan üstüne
eklenen 40 satır.

### 2. Sürüklerken gelen olaylar bekletilir

Sürükleme başlayınca gelen canlı olaylar sıraya alınır, bırakınca sırayla
uygulanır. Kendi taşıman önce sunucuya gider, sonra bekleyenler gelir.

Sürükleme de güzelleşti: tarayıcının yarı saydam sürükleme resmi yerine kartın
gerçek kopyası eğik ve gölgeli taşınıyor (`forceFallback`), bırakılacak yer net
bir boşluk olarak görünüyor. Telefonda karta 150 ms basılı tutunca sürükleme
başlıyor; yoksa panoyu kaydırmaya çalışan her dokunuş kart taşırdı.

### 3. Canlı imleçler

| | |
|---|---|
| Gönderilen | `/app/board/{id}/cursor` → `{x, y, cardId, gone}` |
| Yayınlanan | `/topic/board.{id}/cursors` → `{userId, name, initials, color, x, y, cardId, gone}` |
| Sıklık | Tarayıcı en fazla 50 ms'de bir gönderir; sunucu oturum başına ~30/sn üstünü düşürür |
| Konum | Kolonların kaydırılabilir alanına göre piksel — herkeste kolonlar aynı sırada ve genişlikte |

**Yetki:** İmleç yalnızca **panoya katılmış** oturumdan kabul edilir. Katılma
(`presence/join`) üyeliği zaten doğruluyor; saniyede onlarca gelen mesaj için her
seferinde veritabanına gitmiyoruz. Abonelik tarafı mevcut kural ile korunuyor:
`/topic/board.{id}/...` altındaki her kanal yalnızca üyelere açık.

**Kimlik istemciden alınmaz:** ad ve renk, katılırken sunucunun kaydettiği
kişiden gelir. Kimse başkasının adıyla imleç gezdiremez.

**Kaydedilmez:** İmleç geçmişe yazılmaz, sürüm kontrolü yoktur. Kaybolan bir
mesajın bedeli, bir sonrakinde düzelen birkaç piksellik atlamadır.

**Sürükleme göstergesi:** Sürüklenen kartın kimliği imleçle birlikte gider.
Karşı tarafta kart soluklaşır ve sürükleyenin renginde kesik çerçeve alır,
imlecin altında kartın başlığı taşınır.

Çok sunuculu kurulumda imleçler de Redis köprüsünden geçer (ADR 0004).

## Sonuçlar

**Kazandıklarımız**
- Uzaktan gelen taşıma kayarak görünüyor, pano titremiyor.
- Sürükleme karşıdan gelen olaylarla bozulmuyor.
- Kimin nerede olduğu ve neyi taşıdığı anında görülüyor; iki kişinin aynı
  kartı aynı anda taşıması baştan önleniyor.

**Feda ettiklerimiz / bilinen sınırlar**
- İmleç başına Redis'ten saniyede ~20 mesaj geçer. Birkaç kişilik panolarda
  sorun değil; yüzlerce kişilik bir panoda imleçleri ayrı bir kanala ya da
  sunucu içi birleştirmeye almak gerekir.
- Konum piksel bazlı: dar ekranda kolonlar farklı dizilirse (telefon) imleç
  karta tam denk gelmeyebilir.
- Kolon içi dikey kaydırma hesaba katılmıyor; uzun bir kolonda imleç birkaç
  kart kayabilir.
