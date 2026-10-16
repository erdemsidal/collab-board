# Claude Design komutu — Profil, davetler ve bildirimler

> Bu dosya Claude Design'a verilecek komuttur. Mevcut tasarım teslimiyle
> (`docs/design_handoff_collabboard/`) **aynı projede** devam edilmesi önerilir;
> yeni bir projede başlanacaksa `README.md` ve birkaç ekran görüntüsü eklenmeli.

---

CollabBoard için daha önce hazırladığın arayüze yeni ekranlar ve bileşenler ekleyeceğiz. Mevcut tasarım dili **aynen korunacak**: aynı renk tokenları, tipografi, yarıçaplar, gölgeler, animasyonlar ve bileşen kalıpları (pcard panel kartları, rozetler, toast'lar, modallar, butonlar). Yeni hiçbir şey mevcut ekranlardan "ayrı bir uygulama" gibi görünmemeli.

## Teknik kısıtlar (değişmedi)

- Framework yok: tek `index.html`, vanilya JavaScript + CSS. İkonlar satır içi SVG.
- Dış kütüphane yalnızca mevcut olanlar (SortableJS, @stomp/stompjs). Görsel kırpma için de kütüphane yok — `<canvas>` ile.
- Arayüz dili Türkçe. Teslim formatı önceki gibi: yüksek doğruluklu referans + README (tokenlar, ölçüler, durumlar, etkileşimler).

## Hatırlatma: tasarım tokenları

```
--bg #eef3f8 · --card #fff · --panel #f2f6fb · --sidebar #f7fafd
--bd #e1e7ef · --bd-2 #cfd8e3 · --sep #f1f5fa
--ink #1f2d3d · --ink-2 #6b7a90 · --ink-3 #8b9aad · --ink-4 #a9b6c6
--acc #f5871f (turuncu, birincil) · --acc-soft #fff1e2 · --acc-ink #c96a10
--ok #35b37e · --ok-soft #e4f5ec · --err #e05b49 · --err-soft #fdecea
--blue #3f7cc4 · --blue-soft #e8f0fb
Yazı: sistem yığını, gövde 14px/1.45. Rozet 10.5px/700 uppercase.
Yarıçap: kart 8–9, input/buton 7–8, panel kartı 13, modal 14, avatar %50.
```

---

## 1. Kullanıcı menüsü (üst çubuk, sağ)

Şu an sağ üstteki avatar + ad tıklanmıyor. Tıklanınca açılır bir menü:

- Üstte küçük kimlik bloğu: avatar (36px), ad soyad, e-posta.
- Satırlar: **Profilim**, **Bildirimler** (okunmamış sayısıyla), ayraç, **Çıkış yap** (nötr, kırmızı değil).
- Menü hem ana ekranda hem pano ekranında aynı.

## 2. Bildirim zili ve bildirim paneli

Üst çubukta kullanıcı menüsünün solunda bir zil ikonu.

- Okunmamış varsa sağ üstünde turuncu sayı rozeti (9'dan fazlası "9+").
- Tıklanınca açılan panel (~380px genişlik, maks. yükseklik kaydırmalı):
  - Başlık "Bildirimler" + sağda "Tümünü okundu say" bağlantısı.
  - Bildirim satırı: solda gönderenin avatarı, metin ("**Erdem Sıdal** seni **Pazarlama** panosuna **İzleyici** olarak davet etti"), göreli zaman ("3 dk önce"), okunmamışsa solda ince turuncu şerit / nokta.
  - **Davet bildirimi** satırının altında iki buton: **Kabul et** (turuncu) ve **Reddet** (çerçeveli). Kabul/ret sonrası satır yerinde "Kabul ettin · Panoya git →" ya da "Reddettin" durumuna döner (satır kaybolmaz).
  - Diğer bildirim türleri (buton yok, yalnızca bilgi):
    - "**Mustafa Sıdal** davetini kabul etti · Pazarlama"
    - "**Mustafa Sıdal** davetini reddetti · Pazarlama"
    - "**Erdem Sıdal** davetini geri çekti · Pazarlama"
  - Boş durum: soluk zil ikonu + "Bildirimin yok" + kısa açıklama.

## 3. Canlı davet bildirimi (sağ alt)

Projenin amacı gerçek zamanlılık: davet edilen kişi **hangi ekranda olursa olsun** (ana ekran, başka bir pano) daveti anında görmeli.

- Mevcut toast yerleşiminde (sağ alt) ama **daha zengin bir varyant**: gönderenin avatarı, iki satır metin, altta **✓ Kabul et** ve **✗ Reddet** butonları.
- Bilgi toast'larından farklı olarak **6 saniyede kendiliğinden kapanmaz**; kullanıcı karar verene ya da kapatana kadar durur. Kapatırsa davet zilde bekler.
- Kabul edince toast başarı tonuna döner ("Panoya katıldın · Pazarlama — Aç →") ve birkaç saniye sonra kapanır.
- Aynı anda birden fazla davet gelirse üst üste yığılır (en fazla 3, fazlası zile düşer).

## 4. Üyeler paneli — bekleyen davetler

Mevcut "Üyeler" panel kartında:

- Buton yazısı **Ekle** → **Davet et**. Davet gönderilince mevcut "Davet gönderildi" toast'ı.
- Üye listesinin altında (yalnızca davet varsa) **"Bekleyen davetler"** alt başlığı. Satır: avatar (hesabı yoksa e-postanın baş harfi, gri), e-posta, rol rozeti, **BEKLİYOR** rozeti (soluk turuncu), YÖNETİCİ için davet iptal (×) butonu.
- Hesabı olmayan birine davet gönderilebilir (e-postasına kayıt bağlantısı gider); satırında küçük not: "Hesabı yok · kayıt olunca görecek".
- Davet kabul edilince satır **canlı olarak** bekleyenlerden üyelere geçer (`cbIn` animasyonu); reddedilince kaybolur.

## 5. Ana ekran — "Benimle paylaşılanlar"

Sorun: bir kullanıcı hiçbir çalışma alanına üye değilse ama bir panoya davet edilmişse, bugün ana ekranda "Çalışma alanı yok · 0 pano" görüyor ve panoyu bulamıyor.

- Sol kenar çubuğunda, çalışma alanlarının altında ayrı bir bölüm: **"BENİMLE PAYLAŞILANLAR"** başlığı + sayı. Tıklanınca o panolar normal pano ızgarasında, kart üzerinde rol rozetiyle ve hangi çalışma alanından geldiği küçük yazıyla listelenir.
- Hiç çalışma alanı olmayan ama paylaşılan panosu olan kullanıcı doğrudan bu görünümle açılmalı.
- Hiçbir şeyi olmayan yeni kullanıcı için boş durum metni yeniden yazılsın: "Henüz bir panon yok. Kendi panonu oluşturabilir ya da bir davet bekleyebilirsin."

## 6. Profil sayfası

Ana ekran ve pano gibi tam bir görünüm (`view: profile`), aynı üst çubukla. Ortalanmış ~720px sütun, panel kartlarıyla bölümlenmiş:

**a) Profil başlığı kartı**
- Büyük avatar (96px): fotoğraf varsa fotoğraf, yoksa baş harfler (mevcut renk üretimiyle).
- Avatarın altında/yanında **Fotoğrafı değiştir** ve (fotoğraf varsa) **Kaldır**.
- Ad soyad (büyük), e-posta, "Üyelik: Temmuz 2026".
- Küçük istatistik satırı: çalışma alanı sayısı · pano sayısı.

**b) Kişisel bilgiler kartı**
- Ad, Soyad (düzenlenebilir), E-posta (**düzenlenemez**: kilit ikonu, soluk zemin, altında "E-posta adresi değiştirilemez" açıklaması).
- Kaydet butonu yalnızca bir şey değiştiğinde etkin. Kaydedince başarı toast'ı.

**c) Güvenlik kartı — şifre değiştir**
- Mevcut şifre, yeni şifre, yeni şifre (tekrar). "En az 8 karakter" ipucu.
- Not: "Şifren değişince diğer cihazlardaki oturumların kapatılır; bu cihazda oturumun açık kalır."
- Hata durumları: mevcut şifre yanlış (alanın altında), iki yeni şifre uyuşmuyor, yeni şifre eskisiyle aynı.

**d) Fotoğraf yükleme / kırpma modalı**
- "Fotoğrafı değiştir" → dosya seçici (PNG, JPEG, WebP; en fazla 5 MB) → modal:
  - Kare kırpma alanı, içinde yuvarlak önizleme maskesi; fotoğraf sürüklenerek konumlanır.
  - Yakınlaştırma kaydırıcısı.
  - Yanında küçük boyutlarda canlı önizleme (96px ve 30px — üye listesinde nasıl görüneceği).
  - **Kaydet** / **Vazgeç**. Kaydedince 256×256 olarak yüklenir.
- Hata durumları: desteklenmeyen dosya türü, dosya çok büyük, yükleme başarısız.

## 7. Avatar her yerde

Fotoğrafı olan kullanıcının fotoğrafı şu yerlerde baş harfli dairenin yerine geçmeli: üst çubuk, kullanıcı menüsü, çevrimiçi kişiler yığını (presence), üye listesi, son hareketler, bildirimler, davet toast'ı. Fotoğraf yüklenemezse baş harflere düşmeli. Aynı boyut ve gölge kuralları.

## Durumlar (hepsi çizilsin)

- Yükleniyor (iskelet), boş, hata, başarı.
- Rol farkları: YÖNETİCİ davet edebilir ve iptal edebilir; EDİTÖR/İZLEYİCİ bekleyen davetleri göremez.
- Dar ekran: profil tek sütun, bildirim paneli tam genişlik alt sayfa.

## Teslim

Önceki teslimle aynı yapıda: güncellenmiş `README.md` (yalnızca yeni bölümler + değişen bileşenler), referans HTML, yeni bileşenlerin ölçüleri ve durumları. Mevcut ekranları yeniden tasarlama; yalnızca yukarıdaki eklemeler ve değişen yerler.
