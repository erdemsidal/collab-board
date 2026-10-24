# ADR 0016 — Genel arama

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-23

---

## Bağlam

Yeni arayüzün üst çubuğunda bir arama kutusu var: "şu kart hangi panodaydı?"
sorusunun cevabı için panoları tek tek açmak gerekiyordu. Arama bütün panoları
tek sorguda taradığı için, yetki hatası tek bir panonun değil erişemediğim her
panonun kart başlıklarını açardı.

## Karar

**Tek uç: `GET /api/search?q=…`.** Yanıt `{ boards: [...], cards: [...] }`, en
fazla 8'er sonuç. Panolar ada, kartlar başlığa ya da açıklamaya göre eşleşir.

**Yetki yeniden yazılmadı** (ADR 0015 ile aynı ilke): aranan küme
`BoardService.accessibleBoards`'un döndürdüğü panolardır, iki sorgu da yalnızca bu
pano kimlikleriyle sınırlanır. Pano rolü de oradan gelir; "panolarım" ile aynı.

**Eşleştirme: `lower(alan) LIKE lower(:kalıp)`.** İki taraf da veritabanında
küçültülür; Java ile Postgres "İ"yi farklı küçültüyor, biri Java'da biri SQL'de
küçültülseydi "İndirim" araması bulamayabilirdi. Kullanıcının yazdığı `%` ve `_`
joker değil düz karakterdir: `!` ile kaçışlanır (`ESCAPE '!'`; ters bölü Java, JPQL
ve SQL katmanlarının her birinde ayrı kaçış isterdi). "Türkçe için yeterince
güvenli": Ş, Ç, Ö, Ü, Ğ ve İ doğru küçülür ("ŞUBAT" → "Şubat", "indirim" →
"İndirim"; testle sabit); büyük `I`'nın `ı` olduğunu ise veritabanı bilmez
("ISIK" araması "ışık"ı bulmaz).

**Sıra:** başlığı aramayla başlayan kartlar önce, sonra başlığında geçenler, sonra
yalnızca açıklamasında geçenler; her grupta en yeni önce. Panolarda adı aramayla
başlayanlar önce, sonra alfabetik.

**Sınırlar:** baştaki/sondaki boşluk atılır. 2 karakterden kısa arama `200` ve boş
listeler döner (sorgu atılmaz: kullanıcı yazmaya devam ediyor, tek harf her şeyle
eşleşir). 100 karakterden uzunsa `400`. Uç kimlik ister; hız sınırı yoktur, çünkü
sınırlar kimliksiz uçlar için (ADR 0010).

Tam metin arama (Postgres `tsvector`) ya da ayrı bir arama motoru değerlendirildi:
kök bulma ve sıralama kalitesi getirirdi ama dil yapılandırması, ek sütun/indeks
ve senkron tutulacak ikinci bir kopya demekti. Bugünkü veri boyunda `LIKE` yeter.

## Sonuçlar

**Kazandıklarımız**
- Kart ya da pano adı yazarak doğrudan ilgili panoya gidiliyor.
- Arama, pano listesiyle aynı yetki kuralına bağlı; erişemediğim panodaki aynı
  kelimeyi taşıyan kart sonuçlara giremez (testle sabit).

**Feda ettiklerimiz / bilinen sınırlar**
- `%kelime%` indeks kullanamaz: erişilebilen panoların kartları taranır. Kart
  sayısı büyürse `pg_trgm` GIN indeksi (aynı sorgu, değişiklik gerektirmez) ya da
  tam metin aramaya geçilmeli.
- Kök bulma yok: "plan" araması "planlar"ı bulur, "planlar" araması "plan"ı bulmaz.
- Dotless/dotted I ayrımı yok (yukarıda).
