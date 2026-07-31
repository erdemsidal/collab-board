# Handoff: CollabBoard arayüzü

## Overview
CollabBoard, birden çok kişinin aynı anda düzenlediği gerçek zamanlı Kanban panosudur. Bu paket, mevcut Spring Boot backend'i (REST + WebSocket/STOMP) için yeniden tasarlanmış arayüzün eksiksiz referansıdır: giriş/kayıt, çalışma alanı + pano seçimi, pano ekranı (sürükle-bırak), üyeler paneli, canlı hareket akışı, durum/hata/çakışma geri bildirimleri.

## About the Design Files
Bu klasördeki HTML dosyaları **tasarım referansıdır** — hedeflenen görünüm ve davranışı gösteren prototiplerdir, doğrudan kopyalanacak üretim kodu değildir. `CollabBoard.dc.html` bir streaming component formatındadır (`support.js` runtime'ıyla açılır); amacı görsel ve davranışsal doğruluğu göstermektir.

**Görev:** bu tasarımları hedef projenin kendi ortamında yeniden kurmak. Bu projede hedef ortam brief'te sabitlenmiştir (bkz. `FRONTEND-DESIGN-BRIEF.md` §7):

- **Framework yok.** Arayüz tek bir `index.html` içinde: vanilya JavaScript + CSS.
- Dış kütüphane yalnızca CDN'den: **SortableJS** (sürükle-bırak), **@stomp/stompjs** (gerçek zamanlı).
- İkonlar **satır içi SVG** (ikon kütüphanesi yok).
- Arayüz dili **Türkçe**.
- Hedef konum: `src/main/resources/static/index.html`.

`collab-api.js` istisnadır: **doğrudan kullanılabilir üretim kodudur** (sunucu erişim katmanı). Tek dosya kısıtı nedeniyle içeriğini `index.html` içindeki bir `<script type="module">` bloğuna gömün ya da `static/collab-api.js` olarak bırakıp `index.html`'den import edin (tercih edilen).

## Fidelity
**High-fidelity.** Renkler, tipografi, boşluklar, yarıçaplar, gölgeler ve etkileşimler nihaidir; birebir uygulanmalıdır. Aşağıdaki tokenlar ve ölçüler tasarımdan çıkarılmıştır.

## Design Tokens

### Renkler
| Rol | Değer |
|---|---|
| Sayfa zemini | `#eef3f8` |
| Kart/panel | `#ffffff` |
| Kolon zemini | `#e7edf5` · kolon kenarlığı `#dae2ec` · sürükleme hedefi `#f5871f` |
| Yan panel zemini | `#f2f6fb` |
| Sidebar zemini (home) | `#f7fafd` · aktif satır `#e4ebf3` (+ `inset 0 0 0 1px #d3ddea`) · hover `#e9eff6` |
| Kenarlık | `#e1e7ef` · ikincil `#cfd8e3` · ayırıcı `#eef3f8` / `#f1f5fa` |
| Metin | `#1f2d3d` · ikincil `#6b7a90` · üçüncül `#8b9aad` · en soluk `#a9b6c6` |
| Vurgu (turuncu) | `#f5871f` · hover `#e07610` · koyu metin `#c96a10` · yumuşak zemin `#fff1e2` · yumuşak kenar `#f5cfa4` |
| Başarılı / bağlı | `#35b37e` · metin `#1f7a56` · zemin `#eaf7f1`/`#e4f5ec` · kenar `#c4e7d6` |
| Hata / kopuk | `#e05b49` · hover `#c94b3a` · metin `#a8392b` · zemin `#fdecea` · kenar `#f6cdc7` |
| Mavi (editör/üyeler) | `#3f7cc4` · `#6ba4e8` · metin `#2f5d99` · zemin `#e8f0fb` |
| Koyu panel (auth) | `#1f2d3d` · üzerindeki metin `#f6f8fb` / `#9fb0c4` / `#cbd6e2` |
| Rozet nötr | zemin `#eef1f5` · metin `#6b7a90` · sayaç çipi `#d9e2ec` / `#e4ebf3` |
| Avatar paleti | `#f5871f`, `#4a7fc1`, `#35b37e`, `#8b5cf6`, `#e05b49`, `#0e9aa7`, `#c96a10` (isim/e-posta hash'iyle seçilir) |
| Seçim | `::selection` `#ffe0bd` |
| Modal örtüsü | `rgba(31,45,61,.42)` |

### Tipografi
Sistem yığını: `-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif`. Gövde `14px/1.45`.

| Kullanım | Değer |
|---|---|
| Auth H1 | 34px / 1.16 / 640 / `-.02em` |
| Sayfa başlığı (home) | 22px / 640 / `-.015em` |
| Modal başlığı | 16.5–17px / 640 |
| Auth form başlığı | 21px / 640 |
| Pano adı (topbar) | 15.5px / 640 |
| Pano kartı adı | 15px / 620 |
| Kolon başlığı | 13px / 660 |
| Kart başlığı | 13.5px / 1.4 |
| Üye adı | 13.5px / 570 · e-posta 11.5px |
| Etiket/label | 12.5px / 550 |
| Rozet | 10.5px / 700 / `.04em` / uppercase |
| Sayaç çipi | 10.5–11px / 650 |
| Sistem göstergeleri | 11px, mono (`ui-monospace, SFMono-Regular, Menlo, monospace`) |
| Bölüm başlığı (sidebar) | 11px / 700 / `.07em` / uppercase |

### Boşluk / yarıçap / gölge
- Boşluk skalası: 2, 4, 5, 6, 7, 8, 9, 10, 11, 12, 14, 16, 18, 20, 22, 28, 32, 40, 48, 56 px.
- Yarıçap: kart 8–9, buton/input 7–8, kolon 12, panel kartı 13, modal 14, çip/rozet 5–7, pill 20px, avatar %50.
- Gölge: kart `0 1px 2px rgba(31,45,61,.06)` · pano kartı hover `0 4px 14px rgba(31,45,61,.08)` + `translateY(-1px)` · panel kartı `0 2px 6px rgba(31,45,61,.05)` · modal `0 24px 60px rgba(31,45,61,.28)` · toast `0 8px 26px rgba(31,45,61,.16)` · turuncu buton `0 2px 6px rgba(245,135,31,.35)` · avatar `0 1px 3px rgba(31,45,61,.15)`.
- Focus halkası: `border-color: #f5871f; box-shadow: 0 0 0 3px #fff1e2`.
- Uzun metinlerde `text-wrap: pretty`.

### Animasyonlar (keyframes)
| Ad | Tanım | Kullanım |
|---|---|---|
| `cbIn` | opacity 0→1, `translateY(-6px) scale(.98)`→none | 220ms ease — yeni kart girişi, modal 180ms |
| `cbFlash` | `#fff1e2` + `0 0 0 2px #f5871f` → `#fff` + normal gölge | 900ms ease — uzaktan değişen kartın vurgusu |
| `cbToast` | opacity 0→1, `translateY(10px)`→none | 200ms ease |
| `cbSpin` | rotate 360° | 700ms linear infinite — buton spinner'ı |
| `cbPulse` | opacity 1→.35→1 | 1.2s (iskelet) / 1.4s (bağlantı noktası) / 2.4s (CANLI rozeti) |
| `cbFloat` | `rotateX(54deg) rotateZ(-38deg)` sabit, `translateZ(0→16px→0)` | 7s ease-in-out infinite — auth 3D pano objesi |

## Screens / Views

### 1) Giriş / Kayıt
**Amaç:** kimlik doğrulama + marka anı.
**Layout:** iki kolon, `display:flex; flex-wrap:wrap`, her biri `flex: 1 1 420px`.
- **Sol (koyu) panel:** `#1f2d3d`, padding `34px 52px`, `flex-direction:column; justify-content:space-between; gap:18px; min-height:320px`. Üstte logo (24–26px, `border-radius:7px`, `#f5871f` zemin, içinde iki dikey çubuklu satır içi SVG) + "CollabBoard" 15px/650. Ortada: H1 "Ekibinizin panosu, herkeste aynı anda.", 15px `#9fb0c4` açıklama, ardından 3 maddeli liste (6px nokta: `#35b37e` "Canlı senkron — her değişiklik anında", `#f5871f` "Farkındalık — kim çevrimiçi, kim ne yaptı", `#6ba4e8` "Güven — çakışmada ne olduğu belli"). Altında **3D pano objesi**: `height:118px; perspective:1000px; transform:scale(.68)`; içinde `transform-style:preserve-3d`, `display:flex; gap:16px`, `animation: cbFloat 7s ease-in-out infinite`; üç "kolon" div'i (genişlik 82, padding 9, radius 12, `linear-gradient(160deg,#35455a,#26333f)`, `box-shadow:22px 22px 34px rgba(0,0,0,.34)`), her birinde 6px'lik gri başlık çubuğu + 26px yüksekliğinde kartlar; ilk kartlar sırasıyla turuncu (`linear-gradient(150deg,#f5871f,#d9700f)`), mavi (`#6ba4e8→#3f7cc4`), yeşil (`#35b37e→#229464`), diğerleri `#f6f8fb` (opacity .55–.8). En altta 12px `#6b7a90` backend etiketi ("Demo verisi · backend bağlı değil" veya "Backend: <adres>").
- **Sağ panel:** ortalanmış 360px form. Başlık 21px/640 ("Tekrar hoş geldin" / "Hesap oluştur"), 13.5px `#6b7a90` alt metin. Alanlar: kayıtta Ad + Soyad yan yana (`gap:10px`), sonra E-posta, Şifre (kayıtta "En az 8 karakter" ipucu 11.5px `#8b9aad`). Input: `padding:10px 12px`, radius 8, `1px solid #e1e7ef`, focus halkası. Hata bloğu: `#fdecea` zemin, `#f6cdc7` kenar, `#a8392b` metin 13px, sol tarafta 15px uyarı SVG. Buton: tam genişlik, `#f5871f`, radius 8, `padding:11px 14px`, 14px/600 beyaz; yükleniyorken 14px spinner (`cbSpin`). Altta mod değiştirme: "Hesabın yok mu? **Kayıt ol**" (link rengi `#c96a10`, hover `#f5871f`).
**Doğrulama:** boş alan → "E-posta ve şifre zorunludur"; hatalı → "E-posta veya şifre hatalı"; kayıtta şifre < 8 → "Şifre en az 8 karakter olmalı".

### 2) Çalışma alanı + pano seçimi
**Topbar (56px, `#fff`, alt kenar `#e1e7ef`, padding 0 20px):** solda 24px logo + "CollabBoard" + dikey ayırıcıdan sonra 11.5px backend etiketi; sağda 26px koyu avatar (baş harfler) + ad, ardından "Çıkış" butonu (çıkış SVG + 13px metin, `1px solid #e1e7ef`, radius 7).
**Sidebar (252px, `#f7fafd`, sağ kenar `#e1e7ef`, padding 22px 16px):** "ÇALIŞMA ALANLARI" başlığı; her satır `padding:8px 9px`, radius 9, `gap:9px`: 24px renkli çip (aktifse `#f5871f`, değilse isimden türeyen palet rengi; `box-shadow: 0 2px 5px <renk>55`), ad (aktif 620, diğer 500), pano sayısı pill'i (`#e4ebf3`), ardından **kalem** ve **çöp kutusu** ikon butonları (22px, faint `#a9b6c6`; hover: kalem `#fff1e2`/`#c96a10`, çöp `#fdecea`/`#e05b49`) — yalnızca rol `OWNER`/`ADMIN` iken görünür. Kalem satır içi yeniden adlandırma açar (input `1px solid #f5871f` + focus halkası; Enter kaydet, Escape iptal, blur kaydet). Çöp kutusu onay modalı açar. Altta kesikli çerçeveli "＋ Yeni çalışma alanı" butonu (`1px dashed #cfd8e3`, hover kenar `#f5871f`).
**Ana alan (padding 28px 32px 48px):** başlık = aktif alan adı (22px), altında "N pano · rolün: <rol>"; sağda "＋ Yeni pano" turuncu buton (GUEST rolünde gizli). Pano ızgarası: `grid-template-columns: repeat(auto-fill, minmax(252px, 1fr)); gap:14px`. Pano kartı: beyaz, radius 12, `1px solid #e1e7ef`, padding 15px 16px, üstte ad (15px/620) + rol rozeti, altta "N kart · M üye" (12.5px `#6b7a90`) ve sağda üst üste binen 22px avatarlar (`margin-left:-6px`, `2px solid #fff`). Hover: kenar `#f5cfa4`, gölge yükselir, `translateY(-1px)`.
**Yükleniyor:** 260×112 `#e6ecf4` iskelet kartlar, `cbPulse`.
**Boş durum:** kesikli çerçeveli beyaz blok (radius 14, padding 44px 28px), 46px `#fff1e2` ikon kutusu (üç dikey dikdörtgenli kanban SVG, `#f5871f` stroke), 16px/620 başlık, 13.5px açıklama, turuncu "İlk panoyu oluştur". GUEST rolünde başlık "Bu alana erişimin yok" ve buton gizli.

### 3) Pano ekranı
**Topbar (56px):** "‹ Panolar" butonu · pano adı (15.5px/640) + kendi rol rozeti + 12px `#8b9aad` çalışma alanı adı · sağda: 28px presence avatarları (`margin-left:-7px`, `2px solid #fff`), **bağlantı göstergesi** (7px nokta + metin; bağlı `#eaf7f1`/`#c4e7d6`/`#1f7a56` "Canlı", kopuk `#fdecea`/`#f6cdc7`/`#a8392b` "Bağlantı kesildi" + `cbPulse`, bağlanıyor turuncu), yan panel aç/kapa butonu, çıkış butonu.
**Kolon alanı:** `flex:1; overflow-x:auto; padding:18px; display:flex; align-items:flex-start; gap:14px`. Kolon: 276px, `#e7edf5`, radius 12, `1px solid #dae2ec` (sürükleme hedefiyken `#f5871f`); başlık satırı `padding:11px 12px 9px` — ad 13px/660 + sayaç pill (`#d9e2ec`); kart listesi `padding:0 8px 4px; gap:7px; overflow-y:auto`; footer `padding:8px`.
**Kart:** beyaz, radius 9, `padding:9px 11px`, `1px solid #e1e7ef`, gölge `0 1px 2px rgba(31,45,61,.06)`, hover kenar `#cfd8e3`. Sağ üstte silme ikonu (22px, opacity .55; hover `#fdecea`/`#e05b49`). Etiketler: 10.5px/600 pill, `#f1f5fa` zemin, `#e6ebf2` kenar. Sürüklenirken opacity .4; bırakma göstergesi hedef kartın üstünde `2px solid #f5871f` üst kenar, kolon sonunda 2px turuncu çizgi.
**Kart ekleme:** "＋ Kart ekle" (hover `#dde6f0`) → beyaz kompozitör (kenar `#f5cfa4`, radius 9): otomatik odaklı textarea + "Ekle" / "Vazgeç" + sağda 11px "Enter ile ekle".
**Boş kolon:** kesikli çerçeveli 12.5px `#8b9aad` metin ("Kart yok — aşağıdan ekle").
**VIEWER modu:** kart ekleme, silme ikonu, sürükleme kapalı; kolon şeridinin sonunda 232px'lik beyaz bilgi kartı ("İzleyici modundasın …").
**Yükleniyor:** 276px genişlikte `cbPulse` iskelet kolonlar.

### 4) Yan panel (Üyeler + Son hareketler)
352px, `#f2f6fb`, sol kenar `#e1e7ef`; içinde `overflow-y:auto; padding:14px; gap:12px` ile **iki ayrı kart** (`flex:none`, beyaz, radius 13, `1px solid #e1e7ef`, `0 2px 6px rgba(31,45,61,.05)`, `overflow:hidden`).
- **Üyeler kartı** — header `padding:11px 13px`, `linear-gradient(180deg,#f3f8ff,#ffffff)`, alt kenar `#eef3f8`: 24px `#e8f0fb` ikon kutusu (iki kişi SVG, `#3f7cc4`), "Üyeler" 13.5px/650, üye sayısı çipi, sağda kendi rol rozeti. Gövde `padding:11px 12px 12px`: OWNER ise tek satır davet — input `placeholder="e-posta ile davet et"`, rol `<select>` (Editör/İzleyici/Yönetici), turuncu **Ekle** butonu (`padding:8px 14px`, gölgeli). Üye satırı: 30px renkli avatar, ad (kendisi ise "(sen)" eklenir) + e-posta, rol rozeti, OWNER ise 24px çıkarma (×) butonu; satır hover `#f7fafd`.
- **Son hareketler kartı** — header `linear-gradient(180deg,#f1fbf6,#ffffff)`, 24px `#e4f5ec` saat ikonu (`#229464`), başlık, sağda **CANLI** pill rozeti (bağlıyken `#e4f5ec`/`#1f7a56` + `cbPulse 2.4s` yeşil nokta; kopukken "Kapalı" kırmızı, bağlanırken turuncu). Gövde `padding:4px 12px 10px`: satırlar `padding:10px 4px`, alt ayırıcı `#f1f5fa`, 26px avatar, "**Ad** açıklama" (13px) + göreli zaman (11.5px `#8b9aad`). Boş durum: 38px `#f1f5fa` saat ikonu + "Henüz hareket yok" + açıklama.
- **Sistem göstergeleri (opsiyonel):** panelin altında üst kenarlı satır, 11px mono: `ops ↑ N`, `ws bağlı/kapalı`, `gecikme 24 ms`, `sürüm vN`.
**Göreli zaman:** <1 dk "şimdi", <60 dk "N dk önce", <24 sa "N sa önce", sonrası "N gün önce".

### 5) Kart detayı (modal)
520px, beyaz, radius 14, `cbIn 180ms`. Başlıkta 11px uppercase kolon adı + düzenlenebilir başlık input'u (17px/620, odakta `#f5871f` kenar + `#fffaf4` zemin), sağda kapatma butonu. Gövdede üç meta alanı (Kolon / Konum "3 / 5" / Son değiştiren), ardından "Not" textarea'sı (min 92px). Aksiyonlar: turuncu **Kaydet**, çerçeveli **Kapat**, sağa yaslı `#e05b49` **Kartı sil**. VIEWER'da alanlar `disabled`, Kaydet ve Sil gizli.

### 6) Oluşturma modalı & silme onayı
- **Oluşturma (400px):** başlık + 13px açıklama + otomatik odaklı input (Enter gönderir, Escape kapatır) + turuncu "Oluştur" / "Vazgeç". İki varyant: "Yeni pano" (`ör. Ürün Yol Haritası`) ve "Yeni çalışma alanı" (`ör. Acme Yazılım`).
- **Silme onayı (400px):** 34px `#fdecea` uyarı ikonu, "“<ad>” silinsin mi?" başlığı, pano sayısını içeren uyarı metni ("İçindeki 3 pano, kartlar ve geçmiş kalıcı olarak silinir. Bu işlem geri alınamaz."), kırmızı **Sil** + **Vazgeç**.

### 7) Bildirimler (toast)
Sabit `right:18px; bottom:18px; gap:9px`, maks 360px. Beyaz, radius 10, sol tarafta 3px renkli şerit + 8px nokta, 13px/620 başlık + 12.5px gövde, kapatma butonu, `cbToast 200ms`, 6 sn sonra otomatik kapanır. Tonlar: hata `#e05b49`/`#f6cdc7`, başarı `#35b37e`/`#c4e7d6`, nötr `#f5871f`/`#f5cfa4`.
Metinler: "Bağlantı koptu · Yeniden bağlanmaya çalışılıyor…", "Yeniden bağlandı · Pano tazelendi.", "Çakışma · Bu kartı senden önce biri değiştirdi. Ekran güncellendi, tekrar dene.", "Davet gönderildi · <e-posta> · <rol>", "Çalışma alanı silindi · <ad> ve içindeki panolar kaldırıldı".

## Interactions & Behavior
- **Sürükle-bırak:** kart içi/kolonlar arası ve kolon yeniden sıralama. Hedef kartın üst/alt yarısına göre önce/sonra yerleştirme; boş alana bırakma kolon sonuna ekler. Uygulamada **SortableJS** kullanılacak (`group` ile kolonlar arası taşıma, `animation: 150`, `ghostClass`); tasarımdaki görsel karşılıklar: sürüklenen kart opacity .4, hedef çizgisi 2px `#f5871f`, hedef kolon kenarı turuncu.
- **Düzenleme:** karta çift tıkla satır içi düzenleme (Enter kaydet, Escape iptal, blur kaydet); tek tık kart detayını açar. `prompt()` kullanılmayacak.
- **İyimser arayüz:** her işlem anında ekrana uygulanır ve `/app/board/{id}/ops`'a gönderilir; `/user/queue/errors`'tan ret gelirse çakışma toast'ı gösterilir ve pano REST'ten tazelenir.
- **Canlı olaylar:** uzaktan gelen değişiklikte etkilenen kart `cbFlash` ile vurgulanır, yeni kartlar `cbIn` ile girer, presence avatarları anında güncellenir.
- **Bağlantı:** kopunca gösterge kırmızı + nabız, toast; geri gelince toast + tam tazeleme.
- **Rol kısıtları:** VIEWER'da ekleme/silme/sürükleme yok; OWNER dışında davet satırı ve üye çıkarma yok. Reddedilecek buton asla gösterilmez (ör. silme ikonu yalnızca OWNER/ADMIN).
- **Klavye:** Enter = gönder (kompozitör, davet, modal, yeniden adlandırma), Escape = iptal, Shift+Enter = satır sonu.
- **Dar ekran:** yan panel butonla kapatılabilir; home ızgarası `auto-fill` ile sarar; auth ekranı iki kolondan tek kolona düşer.

## State Management
- `view`: `auth | home | board`; `authMode`: `login | register`; `busy`, `authError`.
- `me`, tokenlar (`accessToken`, `refreshToken` — bellekte; yenileme sessiz).
- `workspaces`, `boards`, `activeWorkspaceId`, `homeLoading`; `editingWs` + `wsDraft` (satır içi yeniden adlandırma), `confirmWs` (silme onayı).
- `board` (kolonlar + kartlar), `boardLoading`, `members`, `activity`, `presence`, `conn` (`connecting|up|down`).
- `composerCol` + `composerText`, `editingCard` + `cardDraft`, `detail` (kart detay modalı), `createModal` + `createName`, `inviteEmail` + `inviteRole`, `toasts[]`, `flash{cardId}`.
- Sürükleme durumu render dışı tutulur: `drag = {kind:'card'|'column', ...}`, `dropTarget = {kind:'card'|'end'|'column', colId, cardId, before}`.
- **Veri akışı:** panoya girişte REST ile tam fotoğraf (`board`, `members`, `activity`), ardından WebSocket ile artımlı olaylar.

## Assets
Harici görsel/ikon yok. Tüm ikonlar satır içi SVG (16×16 viewBox, `stroke: currentColor`, `stroke-width: 1.3–1.6`, `stroke-linecap/linejoin: round`): logo, artı, çöp kutusu, kalem, ×, çıkış, göz, saat, kişiler, panel, uyarı üçgeni, geri oku. Auth ekranındaki 3D pano tamamen CSS'tir (div + gradient + `rotateX/rotateZ`), görsel dosya yok.

## Files
| Dosya | Ne |
|---|---|
| `CollabBoard.dc.html` | Tasarım referansı — tüm ekranlar, durumlar ve etkileşimler çalışır hâlde |
| `collab-api.js` | Sunucu erişim katmanı — REST + STOMP + mock; doğrudan kullanılabilir |
| `BACKEND-BAGLAMA.md` | Uç nokta sözleşmesi, token yenileme, STOMP adresleri, CORS örneği |
| `FRONTEND-DESIGN-BRIEF.md` | Özgün brief: veri modeli, roller, gerçek zamanlı davranışlar, teknik kısıtlar |
| `support.js` | Yalnızca `CollabBoard.dc.html`'i tarayıcıda açabilmek için gereken runtime — hedef projeye taşınmaz |

## Uygulama sırası (öneri)
1. `static/index.html` iskeleti + tokenlar (CSS değişkenleri) + satır içi SVG ikon seti.
2. `collab-api.js`'i `static/` altına koy, `configure({ baseUrl: "" })` ile aynı origin'den çalıştır.
3. Auth ekranı → workspace/board seçimi (çalışma alanı düzenle/sil dahil) → pano ekranı.
4. SortableJS ile sürükle-bırak + `sendOp` bağlantısı.
5. STOMP abonelikleri, presence, çakışma reddi, bağlantı göstergesi, toast'lar.
6. Üyeler + Son hareketler kartları, kart detay modalı, boş/yükleniyor/yetkisiz durumlar.
