# ADR 0021 — Kart anahtarı ve pano şablonları

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-26

---

## Bağlam

İki ayrı eksik vardı.

**Bir kartı panonun dışında anmanın yolu yoktu.** "Ödeme sayfası çöküyor kartı"
demek uzun ve belirsiz (aynı başlıkta iki kart olabilir). Kartı açan bağlantı
(`/?board=12&card=42`) yalnızca bahsetme postasında vardı. Yazılım ekipleri işi
kodla da bağlamak istiyor: dal adında, commit mesajında kartın kısa adı.

**Yeni pano her zaman üç İngilizce kolonla açılıyordu:** To Do, In Progress,
Done; Türkçe kullanıcıya da ([ADR 0020](0020-turkce-ve-ingilizce.md)'nin bilinen
sınırı). Herkesin süreci de bu üç adım değil: yazılım ekibi inceleme adımı ister,
içerik ekibi onay, kişisel liste gün ve hafta.

## Karar

### 1. Anahtar: ön ek ve kartın kimliği, STR-42

Anahtar kartın kimliğinden türer, ayrıca saklanmaz; migration gerekmedi. Kimlik
hiç değişmediği için anahtar da değişmez: kart kolondan kolona taşınsa, adı
değişse de STR-42 kalır (testle sabit). Kart başka panoya taşınamıyor
(`OperationScopeGuard`), bu yüzden anahtarın panoya bağlı olması gerekmiyor.

Ön ek tek yerde, iki tarafta: sunucuda `CardKey.PREFIX`, arayüzde
`CARD_KEY_PREFIX`. Tanınan yazımlar: `STR-42`, `str-42`, `STR 42`, `STR42`,
`#42` ve yalın `42`.

**Pano başına sayaç** (Jira'daki PROJ-12 gibi) değerlendirildi. Daha küçük sayılar
ve panoya özgü ön ek getirirdi; ama ayrı bir sütun, eşzamanlı artırma (kilit ya da
sayaç tablosu), pano adı değişince ön ekin ne olacağı soruları demekti. Ürün
adından gelen tek ön ek bugün yeterli.

Anahtarlar sıralı, yani tahmin edilebilir. Bu yüzden erişim her yerde sunucuda
denetlenir ve kartın panosuna erişimi olmayan, kart hiç yokmuş gibi aynı cevabı
alır: başlık, pano adı ve kartın varlığı sızmaz.

### 2. Nerede görünür

**Kartın yüzünde** göstergelerin başında: küçük, eş aralıklı, soluk yazı. Kartı
bir satır uzatıyor. Yalnızca fareyle üzerine gelince göstermek telefonda çalışmazdı;
sağ üst köşe silme düğmesinin yeri.

**Kart penceresinin başında** anahtar ve kolon, sağda "Kopyala" menüsü:

| Satır | Kopyalanan |
|---|---|
| Anahtarı kopyala | `STR-42` |
| Dal adını kopyala | `str-42-odeme-sayfasi-cokuyor` |
| Bağlantıyı kopyala | `<adres>/?card=STR-42` |

Her satır kopyalanacak metni de gösterir; kopyalama mevcut bildirimle onaylanır.
Menü klavyeyle kullanılır: Enter ya da ↓ açar, oklar gezer, Esc yalnızca menüyü
kapatır (pencereyi değil), odak düğmeye döner. Üç düğmeyi yan yana koymak
değerlendirildi: pencerenin başını kalabalıklaştırıyor, telefonda iki satıra
taşıyordu.

### 3. Dal adı: `str-42-<slug>`

1. Türkçe harfler Latin karşılığına: ç→c, ğ→g, ı→i, İ→i, ö→o, ş→s, ü→u.
   Küçültmeden önce yapılır: Java "İ"yi kök dilde "i̇" (i ve nokta işareti) diye küçültür.
2. Diğer aksanlar atılır (é→e); yoksa "Café" "caf" olurdu.
3. Küçük harf; harf ve rakam dışındaki her dizi tek tire; baştaki ve sondaki tire atılır.
4. 40 karakteri aşarsa son kelime sınırından kesilir; tek bir uzun kelimeyse 40'ta.
5. Başlıktan slug çıkmazsa (ör. yalnızca Japonca) dal adı yalnızca `str-42`.

Kural iki yerde yazılı: `CardKey.slug` (sunucu) ve `cardSlug` (index.html).
Kopyalama tarayıcının "kişi az önce tıkladı" iznine bağlı; sunucuya sorup cevabı
beklemek bu izni bazı tarayıcılarda (Safari) düşürür. Sunucudaki kopya çözme ucunun
cevabında (`branch`) ve ileride GitHub bağlantısında kullanılacak. İki taraf aynı
örneklerle sınanıyor: `CardKeyTest` ve tarayıcı testi.

### 4. Arama ve kısa bağlantı

**`GET /api/search`** ([ADR 0016](0016-genel-arama.md)): sorgu bir anahtarsa o
kart yalnızca erişilebilen panolarda aranır ve listenin başına konur; metin araması
da sürer, aynı kart iki kez gelmez. Anahtar tam eşleşme olduğu için 2 karakter
sınırı ona uygulanmaz ("7" de olur). Her kart sonucu `key` taşır.

**Ctrl K:** anahtarla bulunan kart en üstte, anahtarı vurgulu; Enter kartı
penceresiyle açar. Başlıkla bulunan kart eskisi gibi panoda gösterilir. Pano içi
süzgeç de anahtarı tanır.

**`GET /api/cards/by-key/{key}`** → `{ key, cardId, boardId, boardName, columnName, title, branch }`.
Kimin erişebildiğini `BoardAccessService.roleOf` söyler; erişim yoksa da `404`
("Kart bulunamadı.", olmayan kartla aynı). Anahtar biçimi bozuksa `400`.

**`/?card=STR-42`:** postadaki bağlantının ([ADR 0019](0019-kart-yorumlari-ve-bahsetme.md))
kardeşi. Adres çubuğundan hemen silinir, hedef sekmede saklanır; oturum açılınca
anahtar sunucuda çözülür ve aynı `openBoard(boardId, { openCard })` çağrılır.
Oturum yoksa önce giriş ekranı gelir. Erişimi olmayan "Kart bulunamadı" uyarısıyla
ana ekranda kalır.

### 5. Pano şablonları

| Kimlik | Türkçe | İngilizce |
|---|---|---|
| `kanban` (varsayılan) | Yapılacak · Devam ediyor · Bitti | To Do · In Progress · Done |
| `software` | Yapılacak · Yapılıyor (WIP 3) · İncelemede (WIP 2) · Bitti | To Do · In Progress · In Review · Done |
| `content` | Fikirler · Yazılıyor · Onayda · Yayında | Ideas · Writing · Awaiting Approval · Published |
| `personal` | Bugün · Bu hafta · Sonra · Bitti | Today · This Week · Later · Done |
| `blank` | kolon yok | no columns |

Şablonlar kodda (`BoardTemplate`), veritabanında değil: kişi kendi şablonunu
kaydetmiyor, liste sürümle değişiyor. Örnek kart eklenmez; boş panoyu kişinin kendi
işiyle doldurması, silinecek örneklerle uğraşmasından iyidir.

**Dil:** kolon adları `messages_*.properties` içinde (`template.{id}.col.{kolon}`)
ve pano **açan kişinin kayıtlı dilinde** (`users.locale`) kurulur. Liste ucu
(`GET /api/boards/templates`) da aynı dili kullanır; önizlemede "Yapılacak" görüp
panoda "To Do" bulmak olmasın. Bu, isteğin diline bakan diğer REST metinlerinden
bilinçli bir sapma: burada metin bir hata mesajı değil, kaydedilecek veri.

**`POST /api/boards`** isteğe bağlı `templateId` alır; boşsa `kanban`, tanınmıyorsa
`400` ve geçerli değerler. Şablon her şeyden önce çözülür: geçersizse kişisel alan
bile açılmaz. Şablonun kolonları panonun ilk kolonlarıdır, olay kaydına
`ADD_COLUMN` yazılmaz; zaman yolculuğunun tohumlaması
([ADR 0008](0008-kolon-yasam-dongusu-ve-gecmis-tohumlamasi.md)) onları baştan beri
var sayar. Var olan panolar değişmedi.

**Arayüz:** pano açma penceresinde radyo grubu; her satırda adı ve kolonların küçük
önizlemesi (WIP limiti "≤ 3"). Ok tuşları değiştirir, Enter panoyu açar. Liste
yüklenemezse pano yine Basit Kanban'la açılır.

## Sonuçlar

**Kazandıklarımız**
- Kart kısa ve kalıcı bir adla anılıyor: sohbette, commit mesajında, dal adında.
- Ctrl K'ya STR-42 yazmak kartı açıyor; kartın bağlantısı paylaşılabiliyor.
  Başkasının kartı aramada da bağlantıda da görünmüyor (testle sabit).
- Yeni pano kişinin dilinde ve işine uygun kolonlarla açılıyor; Türkçe kullanıcıya
  İngilizce kolon sorunu kapandı.

**Feda ettiklerimiz / bilinen sınırlar**
- Bütün kartlar tek sayaçtan: sayılar büyüyecek (STR-48213). Panoya özgü ön ek yok.
- Anahtar tahmin edilebilir; güvenlik erişim denetimine dayanıyor, anahtarın
  gizliliğine değil. Silinen kartın anahtarı boşta kalır, yeniden kullanılmaz.
- Dal adı kuralı iki yerde yazılı. Testler ortak örneklerde ayrılığı yakalar;
  kural değişirse ikisi birlikte değişmeli.
- "42" araması o kartı başa koyar, başlığında 42 geçen kart arkasından gelir.
- Kart yüzündeki anahtar her kartı bir satır uzatıyor.
- Şablonlar sabit; kişinin kendi şablonunu kaydetmesi ayrı bir iş. Hesabın dili
  sonradan değişirse var olan panonun kolon adları değişmez (kişinin verisi gibi).
