# ADR 0012 — Onaylı davet ve kişiye özel canlı bildirim

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-14

---

## Bağlam

Panoya kişi eklemek tek adımdı: yönetici e-postayı yazıyor, kişi anında üye
oluyordu. Denemede iki sorun çıktı:

1. **Rıza yok.** Kişi, haberi olmadan başkasının panosuna giriyor. Bir panoya
   üye olmak o panonun içeriğini görmek, adının üye listesinde durması demek;
   bunu kişinin kendisi kabul etmeli.
2. **Haber yok.** Eklenen kişinin ekranında hiçbir şey değişmiyordu. Canlı
   kanal yalnızca **panoya** bağlıydı (`/topic/board.{id}`); henüz üyesi
   olmadığı panonun kanalını dinlemesi zaten mümkün değil. Sayfayı yenilese
   bile listesinde göremiyordu, çünkü hiçbir çalışma alanına ait olmayan
   panolar arayüzde gizleniyordu (ayrıca düzeltildi: "Benimle paylaşılanlar").

Projenin amacı değişikliği karşı tarafa anında göstermek. İkinci sorun bu
amacın tam ortasında.

## Seçenekler

**Kişiye haber vermek için**

| Seçenek | Artı | Eksi |
|---|---|---|
| Ana ekranda periyodik sorgu (polling) | Basit | Gecikme ya da boşa istek; projenin karşı çıktığı yöntem |
| Kullanıcıyı ilgilendiren panoların kanallarına abone olmak | Mevcut kanal | Üye olmadığı panoya abone olamaz — davet tam o durumda |
| **Kişiye özel kanal (`/user/queue/notifications`)** | Kişi hangi ekranda olursa olsun ulaşır | Redis köprüsünün kişiye özel mesajı da taşıması gerekir |

**Davetin neye bağlanacağı**

| Seçenek | Eksi |
|---|---|
| Kullanıcıya (user id) | Hesabı olmayan birini davet etmek imkânsız |
| **E-posta adresine** | Kayıttan sonra davetin kişiyle eşleşmesi gerekir |

## Karar

### 1. Üyelik, davetin kabulüyle başlar

Yönetici artık kişi **ekleyemez**, yalnızca **davet eder**. Davet
`PENDING → ACCEPTED | DECLINED | CANCELLED` yolunu izler; üyelik kaydı yalnızca
`ACCEPTED` anında açılır. Eski "üye ekle" ucu kaldırıldı; yerine yalnızca
**mevcut** üyenin rolünü değiştiren `PATCH /members/{userId}` geldi. Yeni kişi
rol değiştirme kılığında içeri sokulamaz (testle sabit).

Kurallar:

- Aynı adrese aynı panoda **tek bekleyen davet**. Kod kontrol ediyor, ama iki
  eşzamanlı istek kontrolü birlikte geçebilir; asıl garanti veritabanında:
  `(board_id, email) WHERE status = 'PENDING'` üzerinde kısmi tekil indeks.
- Zaten üye olana davet `409`, kendini davet etmek `400`.
- Daveti **yalnızca davet edilen** yanıtlayabilir. Başkası denerse `404` —
  `403` olsaydı davet numarası denenerek hangi davetlerin var olduğu öğrenilirdi.

### 2. Davet e-postaya bağlanır

Hesabı olan kişiye bildirim gider. Hesabı olmayan adrese ise kayıt
bağlantısıyla bir posta gider (`/?signup=adres`). Kişi kaydolup giriş yaptığında
bildirim listesi istenirken o adrese bekleyen davetler bildirime dönüştürülür
(geriye dönük doldurma). Aynı davetten iki bildirim çıkmaması için
`(user_id, invitation_id)` üzerinde tekil indeks var.

Bu, README'deki eski "davet kayıtlı kullanıcı gerektirir" sınırını da kaldırıyor.

### 3. Kişiye özel canlı kanal

Her oturum, panodan **bağımsız** ikinci bir STOMP aboneliği açar:
`/user/queue/notifications`. Sunucu `convertAndSendToUser(email, …)` ile
yalnızca o kişinin oturumlarına yazar. Davet, kişi ana ekranda, başka bir
panoda ya da profil sayfasındayken aynı şekilde önüne düşer.

Çok sunuculu kurulumda kişinin bağlantısı başka bir sunucuda olabilir. Redis
köprüsündeki zarfa (`RelayMessage`) bir `user` alanı eklendi: doluysa her sunucu
mesajı kendi üzerindeki o kişinin oturumlarına iletir, boşsa eskisi gibi
kanaldaki herkese yayınlar ([ADR 0004](0004-cok-sunucuya-olcekleme-redis-pubsub.md)).

Bağlantı koparken gelen bildirim kaçmasın diye istemci her (yeniden)
bağlanmada listeyi REST'ten bir kez çeker. Canlı kanal hız içindir; doğruluğun
kaynağı veritabanıdır.

### 4. Canlı mesaj işlem tamamlandıktan sonra gider

Bildirim, veritabanı işlemi **commit edildikten sonra** gönderilir
(`AfterCommit`). Aksi hâlde kişi "davet geldi"yi görüp kabul düğmesine
bastığında daveti henüz kaydedilmemiş bulabilir, ya da işlem geri alınır ve
var olmayan bir davet ekranda kalırdı.

### 5. Pano kanalına içeriksiz sinyal

Davet gönderildiğinde, kabul edildiğinde ya da üye listesi değiştiğinde pano
kanalına yalnızca `MEMBERS_CHANGED` / `INVITATIONS_CHANGED` sinyali gider; içinde
kimin davet edildiği **yazmaz**. Pano kanalını izleyiciler de dinliyor; bekleyen
davetlerin e-posta adresleri yalnızca yöneticinin görmesi gereken bilgi.
Yönetici sinyali alınca listeyi yetkisi denetlenen REST ucundan çeker.

### Diğer kararlar

- **Hız sınırı:** davet gönderme saatte 20 — davet, hesabı olmayan adreslere
  posta gönderiyor; uç bir spam aracına dönüşmemeli.
- **Kabul, geçmişe yazılır** ("panoya İzleyici olarak katıldı").
- **Geri çekilen davet** davet edilenin ekranından da canlı olarak kalkar.

## Sonuçlar

**Kazandıklarımız**
- Kimse rızası olmadan bir panoya üye olmuyor.
- Davet ve yanıtı her iki tarafa sayfa yenilemeden ulaşıyor.
- Hesabı olmayan biri de davet edilebiliyor.
- Kişiye özel kanal başka bildirimler için (atama, yorum, son tarih) hazır.

**Feda ettiklerimiz / bilinen sınırlar**
- Her oturum iki WebSocket bağlantısı tutuyor (pano + kişisel). Tek bağlantıda
  birleştirmek mümkün ama pano değiştikçe aboneliği yönetmek karmaşıklaşırdı.
- Bekleyen davetin süresi dolmuyor; yönetici geri çekene kadar duruyor.
- Bildirimler silinmiyor, yalnızca okundu işaretleniyor; temizleyen iş yok.
- Çalışma alanına üye eklemenin ucu henüz yok; eklendiğinde aynı onaylı
  davet akışını kullanmalı.
