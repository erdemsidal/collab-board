# ADR 0015 — Tanıtım sayfası ve ana ekran özeti

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-23

---

## Bağlam

Oturumu olmayan ziyaretçi doğrudan giriş formuna düşüyordu: ürünün ne yaptığını
görmeden hesap açması bekleniyordu. Giriş yapan kişinin ana ekranı ise yalnızca
pano kartlarından oluşuyordu; "bana atanan ne var, ne gecikti, panolarımda ne
oluyor?" sorularının cevabı için panoları tek tek açmak gerekiyordu.

## Karar

### 1. Tanıtım sayfası ayrı dosyalarda, ihtiyaç anında yüklenir

`static/landing.js` ve `static/landing.css`. `index.html` yalnızca stil bağlantısını,
`start()` içindeki kancayı ve giriş ekranındaki "Ana sayfa" bağlantısını taşır.
Modül `import()` ile ancak oturum yoksa yüklenir: oturumu açık kullanıcı onu hiç
indirmez; yüklenemezse ziyaretçi boş sayfada değil giriş formunda kalır.
`?verify=`, `?reset=`, `?signup=` bağlantıları ve kayıtlı oturum eskisi gibi önce
ele alınır. İki dosya `SecurityConfig` izin listesine eklendi; bir test, oturumsuz
sunulması gereken dosyaların listesini sabitler.

Sayfada sosyal kanıt yoktur (müşteri logosu, yorum, kullanıcı sayısı); ürün
yalnızca gerçekten yaptığı şeylerle anlatılır. Hero'daki pano bir canlandırmadır
ama gösterdiği her davranış uygulamada vardır. Hareketler yalnızca
`transform`/`opacity` ile yapılır, ekran dışındayken ve sekme gizliyken durur,
`prefers-reduced-motion` açıksa hiç oynamaz ve anlamlı tek bir kare gösterir.

### 2. Ana ekran özeti tek uçtan: `GET /api/me/overview`

Yetki yeniden yazılmadı. "Hangi panolar bu kişiye açık" sorusunu "panolarım"
listesini üreten kod cevaplıyor: `BoardService.accessibleBoards` (çalışma alanı
rolü + pano bazlı istisna) iki yere de hizmet ediyor ve özetin her sorgusu bu
pano kümesiyle sınırlanıyor. Kural tek yerde kaldığı için, ileride değiştiğinde
özet pano listesinde olmayan bir panonun kartını gösteremez.

**"Açık kart" tanımı:** kart panonun son kolonunda değilse açıktır. Akış ölçümü
(ADR 0006, `FlowMetricsService`) de işi, kart son kolona ulaştığında bitmiş
sayar; özet başka bir tanım kullansaydı pano "bitti" derken ana ekran "sende
bekliyor" derdi. Tek kolonlu panoda akış olmadığından o kolon "bitti" anlamına
gelmez, kartları açık sayılır.

**Gecikme günü istemciden:** son tarih saatsiz bir gündür. Sunucu UTC'de çalışırken
Türkiye'de ertesi gün başlamış olabilir; istemci `today` parametresiyle kendi
gününü gönderir ki sayaç kartın üstündeki "gecikti" rozetiyle aynı şeyi söylesin.
Gelmezse sunucunun günü kullanılır; bozuk tarih `400` alır.

Kartlar (kolonuyla birlikte), hareketler, hareket sayısı ve hareketi yapanların
fotoğrafları birer toplu sorguyla gelir: sorgu sayısı pano sayısından bağımsızdır.
Bu sırada "panolarım" listesinin pano sorguları da alan/üyelik başına ayrı sorgu
yerine toplu hâle getirildi.

### 3. Özetin ikinci katmanı: eğilim, akış ve ekip

Yeni ana ekran için aynı uca alanlar eklendi; eskilerin hiçbiri değişmedi. Hepsi
yine `accessibleBoards` kümesiyle sınırlı.

**`activityByDay` — gün sınırı UTC gece yarısı.** Veritabanı zamanı UTC tutar
(`hibernate.jdbc.time_zone: UTC`; sunucu Türkiye saatinde çalışsa bile 01:30'daki
hareket `22:30` olarak yazılır, denendi). Günü veritabanında ayırmak 7 sorgu
yerine tek gruplu sorgu demek; gün metin olarak okunur, tarih türünü JDBC sürücüsü
yeniden bir saat dilimine çevirip kaydırabilirdi. Pencerenin hangi günle bittiğini
istemcinin `today`'i söyler, her zaman 7 satır döner (hareketsiz gün 0). İstemci
saat dilimini göndermediği için Türkiye'de 00:00–03:00 arasındaki hareket bir
önceki günün çubuğuna yazılır; bu saatlerde en sağdaki çubuk boş görünebilir.
Eski `activityLast7Days` kayan 7×24 saattir; iki sayı birebir tutmayabilir.

**`doneLast7Days` / `donePrev7Days` — son kolona BAŞKA bir kolondan giren farklı
kart.** Pencereler grafikle aynı UTC günleri: `today` ile biten 7 gün ve ondan
önceki 7 gün. Done içinde sıra değiştirmek bitirmek sayılmaz; aynı hafta iki kez
Done'a giren kart bir kez sayılır. Eklenme kaydı olmayan (V9 öncesi) kartın ilk
görülen taşıması, nereden geldiği bilinmediği için giriş sayılır.

**`aging` ve `boardsHealth` — akış ölçümüyle aynı kaynak.** "Kart bu kolona ne
zaman girdi" sorusu `FlowMetricsService` içinden `CardFlowReplay`'e taşındı;
akış paneli ile ana ekran aynı oynatıcıyı kullanır, kaydı olmayan kart için
oluşturulma anı (`estimated`). Kart olayları erişilebilen tüm panolar için tek
sorguda okunup bir kez oynatılır. Sağlık kartı en son hareket gören 6 panoyu
gösterir; darboğaz yeniden hesaplanmaz, panonun akış ekranı ne diyorsa o
(`FlowMetricsService.analyze`, pano başına — tek bilinçli istisna, en fazla 6).
`wipFull`, kart sayısı limitine ulaşmış ilk kolondur.

**`liveBoards` / `team` — presence'tan, yalnızca erişilebilen panolar.** Redis'teki
`presence:board:{id}` kayıtları tek turda (pipeline) okunur. Yalnızca kişinin
erişebildiği panolar okunduğu için, ekip arkadaşı göremediği bir panodaysa
"çevrimdışı" görünür; `where` başka bir panonun adını sızdıramaz. Ekip: panoların
doğrudan üyeleri ve o panoların alanlarının üyeleri; misafir hariç (alan rolü ona
pano açmaz). Ad, baş harf ve renk tek yerden (`PresenceUser.of`): panodaki
çevrimiçi listesiyle aynı renk. Kişi satırları rol tablosu yüklenmeden tek
sorguyla gelir.

## Sonuçlar

**Kazandıklarımız**
- Ziyaretçi ürünü kayıt olmadan görüyor; her düğme doğrudan ilgili forma götürüyor.
- Ana ekran ilk bakışta "bende ne var, ne gecikti, ne oldu" sorularını cevaplıyor;
  satıra tıklamak ilgili panoyu açıyor.
- Özet, pano listesiyle aynı yetki kuralına bağlı; ayrı bir kural yok.

**Feda ettiklerimiz / bilinen sınırlar**
- Özet canlı değil: ana ekrana her dönüşte yeniden istenir, açıkken kendiliğinden
  tazelenmez.
- Atanmış kart listesi en yakın tarihli 20 kartla sınırlı (sayaç gerçek toplamı
  gösterir); hareket listesi son 15 kayıt.
- "Son kolon = bitti" bir varsayımdır; ekip son kolonu başka amaçla kullanıyorsa
  (ör. "Arşiv") özet onu da bitmiş sayar. Kolona açık bir "bitti" işareti
  eklenirse hem akış ölçümü hem özet ona geçmeli.
- Akış ölçümünün "yaşlanan iş" listesi şu an son kolondaki kartları da içeriyor;
  özet onları açık saymıyor. Bu fark ayrıca ele alınmalı.
- Biten iş ve bekleyen iş, erişilebilen panoların kart olaylarını baştan oynatır:
  maliyet olay sayısıyla büyür. Büyüdüğünde karta "kolona giriş anı" alanı
  eklenmeli ya da özet önceden hesaplanmalı.
- Sunucu çökerse presence kaydı kalır (ADR 0004); o kişi ana ekranda da bir süre
  "çevrimiçi" görünür.
