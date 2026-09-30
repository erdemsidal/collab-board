# ADR 0007 — WIP limiti ve kart detayları

- **Durum:** Kabul edildi
- **Tarih:** 2026-08-20

---

## Bağlam

ADR 0006 ile akış sağlığı geldi: hangi kolonda kartların ne kadar beklediğini,
çevrim süresini ve darboğazın nerede olduğunu ölçebiliyoruz.

Ama ölçüm tek başına **teşhis**tir. Panel "In Progress'te darboğaz var" diyor;
kullanıcının elinde bunu düzeltecek bir araç yok. Teşhis edip tedavi etmemek,
özelliği bir gösterge panosuna indirger.

Kanban'ın bu soruna verdiği cevap **WIP limiti**dir (Work In Progress): bir kolonda
aynı anda bulunabilecek kart sayısını sınırlamak. Limit dolduğunda yeni iş
başlatamazsın, mecburen mevcut işi bitirirsin. Kuyruk teorisinin bir sonucudur:
devam eden iş miktarı arttıkça her bir işin tamamlanma süresi uzar.

Aynı zamanda kartın kendisi fazla çıplaktı — yalnızca `title` ve `position`
tutuyordu. "Login sayfası yap" yazabiliyordun ama kimin yaptığı, ne zamana kadar,
detayının ne olduğu kayıtsızdı. Bu, aracı gerçek işte kullanılamaz kılıyordu.

## Karar vericiler

- **Teşhis ile tedavi aynı hikâyenin parçası olmalı.** Akış sağlığı darboğazı
  bulur, WIP limiti oluşmasını engeller.
- **Mevcut panolar davranış değiştirmemeli.** Limit isteğe bağlı olmalı.
- **Yeni teknoloji getirmemek.** Mevcut operasyon modeli ve reddetme yolu bu işi
  zaten karşılayabiliyor.
- **Geçmiş bozulmamalı.** Zaman yolculuğu yeni alanlarla da doğru çalışmalı.

## Seçenekler

### WIP limiti nasıl taşınsın?

#### 1) REST ucu (`PATCH /api/columns/{id}`)
- ➕ Basit, tanıdık.
- ➖ Limit değiştiğinde panodaki diğer kullanıcıların rozeti eski kalır. Biri
  hâlâ "2/5" görürken kartı reddedilir — sebebi ekranda görünmez. **Elendi.**

#### 2) Operasyon (`SET_WIP_LIMIT`) — **seçilen**
Diğer değişiklikler gibi WebSocket üzerinden gelir, `/topic/board.{id}`'e yayınlanır.
- ➕ Herkesin rozeti anında güncellenir.
- ➕ Geçmiş kaydına düşer → zaman yolculuğunda limit de yeniden kurulur.
- ➕ `sealed` arayüz sayesinde switch'i güncellemeyi unutmak derleme hatası verir.
- ➖ Kolon ayarı olmasına rağmen "operasyon" adı altında duruyor; kavramsal olarak
  kart operasyonlarıyla aynı kutuda değil.

### Limit dolu kolona kart gelirse ne olsun?

#### 1) Sessizce kabul et, sadece rozeti kırmızı göster
- ➕ Kimse engellenmez.
- ➖ Limit tavsiyeye dönüşür ve **hiçbir şey değişmez.** WIP limitinin tüm değeri
  bağlayıcı olmasından gelir. **Elendi.**

#### 2) Operasyonu reddet — **seçilen**
`WipLimitExceededException` → mevcut reddetme yolu (`/user/queue/errors`).
- ➕ Kural gerçekten çalışır.
- ➕ Altyapı hazır: `OperationRejectedEvent` ve `@MessageExceptionHandler` zaten var.
- ➖ Kullanıcı engellenir. Bunu bir hata gibi göstermemek için `WIP_LIMIT` ayrı bir
  `reason` olarak taşınıyor ve arayüzde kırmızı hata değil, açıklayıcı bir uyarı olarak
  gösteriliyor.

### Kart düzenlemesi kısmi mi, tam mı?

#### 1) Kısmi güncelleme (`null` = "bu alana dokunma")
- ➕ Küçük mesajlar.
- ➖ Bir alanı **boşaltmak imkânsız** hâle gelir: "açıklamayı sil" ile "açıklamaya
  dokunma" aynı mesaja dönüşür. **Elendi.**

#### 2) Alanların tamamı taşınır — **seçilen**
`EDIT_CARD` kartın düzenlenebilir alanlarının yeni hâlini bir bütün olarak taşır;
gönderilmeyen alan temizlenir.
- ➕ Boşaltma doğal olarak çalışır.
- ➕ Güvenli, çünkü `baseVersion` araya başkasının girmediğini garanti eder (ADR 0003).
  İstemci gördüğü hâlin üzerine yazar, kör bir ezme yapmaz.
- ➖ Eski bir istemci yalnızca başlık gönderirse detayları siler. Tek istemcimizi
  kendimiz yazdığımız için kabul edilebilir; harici istemci geldiğinde sürümlenmiş
  bir operasyon tipi gerekir.

## Karar

1. **WIP limiti**, `board_columns.wip_limit` (NULL = sınırsız) olarak saklanır ve
   `SET_WIP_LIMIT` operasyonuyla değiştirilir.
2. Limit dolu bir kolona kart eklenmesi veya taşınması **reddedilir**;
   gerekçe `WIP_LIMIT` olarak yalnızca gönderene iletilir.
3. Kolon **içinde** sıralama limitte bile çalışır — taşınan kart kendi yerini işgal
   ediyor sayılmaz. Aksi hâlde dolu bir kolonda kartları yeniden sıralamak
   imkânsız olurdu.
4. Limit, mevcut kart sayısının altına da düşürülebilir. Fazlalık silinmez; kolon
   "aşırı dolu" görünür ve boşalana kadar yeni kart kabul etmez.
5. **Kart detayları** olarak `description`, `assignee_id`, `due_date` eklenir.
   Atanan kişi ilişki değil düz `Long` olarak tutulur.
6. `EDIT_CARD` alanların tamamını taşır.

### Neden atanan kişi `@ManyToOne` değil?

Pano açılışında yüzlerce kart tek seferde DTO'ya çevriliyor. JPA ilişkisi kursaydık
her kart için ayrı bir kullanıcı sorgusu doğardı (N+1 problemi). İstemci üye
listesini `/api/boards/{id}/members` üzerinden zaten ayrıca çekiyor ve id'yi isme
orada eşliyor — tek sorgu, sıfır ek yük. Bedeli: veritabanı düzeyinde bütünlük
kısıtı elle kuruldu (`REFERENCES users(id) ON DELETE SET NULL`).

## Sonuçlar

**Kazandıklarımız**

- Akış sağlığı artık kapalı bir döngü: panel darboğazı gösterir, limit oluşmasını
  engeller.
- Kart gerçek işte kullanılabilir hâle geldi.
- Limit değişiklikleri geçmişe düşüyor → zaman yolculuğunda o günkü limit de görünür.
- Yeni bağımlılık yok; mevcut operasyon ve reddetme altyapısı yeniden kullanıldı.

**Feda ettiklerimiz**

- Kullanıcı bazen engellenir. Bu kasıtlıdır, ama alışık olmayan birine ilk seferde
  hata gibi gelebilir — arayüzdeki açıklama metni bu yüzden var.
- `EDIT_CARD` artık daha büyük bir mesaj ve tam-durum semantiği taşıyor; harici bir
  istemci yazılırsa bu sözleşmenin sürümlenmesi gerekir.
- Atanan kişi ilişkisiz tutulduğu için "bu kullanıcının tüm kartları" gibi sorgular
  JPA üzerinden doğal gelmiyor; gerekirse elle sorgu yazılacak.

**Bilinen sınırlar**

- Limit yalnızca kart **sayısını** sınırlar. Gerçek Kanban'da bazı takımlar iş
  büyüklüğüne (story point) göre sınırlar; bu ölçü henüz kartta yok.
- Son tarih için hatırlatma/bildirim yok — tarih yalnızca görsel bir uyarı üretir.
