# CollabBoard — Gerçek Zamanlı İşbirlikçi Kanban Panosu · Yol Haritası

> **Özet:** Birden çok kullanıcı **aynı anda** aynı Kanban panosunu düzenler (kart ekler, taşır, yazar) ve herkes değişikliği **canlı** görür. Bir kişi kartı taşıyınca, diğerinin ekranında da anında kayar.
>
> **Amaç:** Vitrin projesi. Kanıtlamak istediğimiz cümle: *"Gerçek zamanlı, çok kullanıcılı, birden çok sunucuya ölçeklenebilen bir sistem kurabiliyorum ve eşzamanlı düzenleme / çakışma / ölçekleme kararlarımı savunabiliyorum."*
>
> **Neden bu proje?** Önceki projemiz (Orchestra) istek/cevap + kuyruk dünyasıydı. Bu proje **tamamen farklı** bir dünya: sürekli açık bağlantı (WebSocket), sunucunun istemciye **push** etmesi, ve çok kullanıcılı gerçek zamanlı **state senkronizasyonu**.
>
> **Süre:** ~3-4 hafta, faz başına ~1 hafta. Her faz sonunda: DUR, README güncelle, commit at, "burası bitti" de.

---

## Nasıl çalışacağız

Aynı disiplin: **"ne kodladığın" değil, "neden o kararı verdiğin".** Her önemli kararda bir **ADR** (`/docs/adr/000X-baslik.md`) yazacağız: Bağlam → Seçenekler → Karar → Sonuç/Feda ettiğim.

Çalışma modu: Ben iskeleti ve zor kısımları yazıp **her satırı açıklarım**, sen `TODO(sen)` kısımlarını doldurursun, kararları birlikte tartışırız. Öğrenmek esas; hız ikincil.

---

## Kavramsal temel — başlamadan bilmen gerekenler

Bu proje yeni topraklar. Üç kavramı baştan oturtalım.

### 1. WebSocket nedir? (REST'ten farkı)

**REST (Orchestra'da yaptığımız):** İstemci sorar, sunucu cevaplar, bağlantı kapanır. Tek yönlü, "soru-cevap". Sunucu kendiliğinden istemciye bir şey **söyleyemez** — istemci tekrar sormak zorunda (polling).

**WebSocket:** İstemci ile sunucu arasında **sürekli açık** bir hat kurulur (tek bir "handshake" sonrası). İki yönlü: sunucu istediği an istemciye **push** edebilir, istemci de sunucuya. Gerçek zamanlı her şeyin temeli bu.

```
REST:                          WebSocket:
İstemci ──soru──► Sunucu        İstemci ◄════════► Sunucu
İstemci ◄─cevap── Sunucu              (sürekli açık, çift yönlü,
   (her seferinde yeni bağlantı)       sunucu istediği an push eder)
```

Bizim panoda: bir kullanıcı kartı taşıyınca, sunucu bunu **açık WebSocket hatlarından** diğer tüm kullanıcılara anında push eder. Polling yok, gecikme yok.

### 2. Değişiklikleri "operasyon" olarak modellemek

Panoyu her seferinde baştan sona göndermek israf olur (büyük pano = büyük veri). Onun yerine her değişikliği küçük bir **operasyon** olarak yolluyoruz:

```
{ "type": "MOVE_CARD", "cardId": "42", "toColumn": "done", "position": 2 }
{ "type": "EDIT_CARD", "cardId": "42", "title": "Yeni başlık" }
{ "type": "ADD_CARD", "columnId": "todo", "title": "..." }
```

Sunucu bu operasyonu alır, uygular, ve diğer herkese yayınlar. Bu "operasyon tabanlı" model, hem verimli hem de çakışma çözümünün temeli olacak.

### 3. Çakışma (conflict) — bu projenin kalbi

İki kişi **aynı anda** aynı kartı düzenlerse kimin değişikliği kazanır? Bir yelpaze var:

| Yaklaşım | Ne yapar | Zorluk | Bizim kullanımımız |
|----------|----------|--------|--------------------|
| **Last-Write-Wins (LWW)** | Son gelen kazanır | Basit; veri kaybı olabilir | Alan düzenlemede (başlık vs.) |
| **Versiyonlama** | Her kartın versiyonu var; eski versiyonla gelen reddedilir | Orta | Kritik değişikliklerde |
| **OT (Operational Transformation)** | Google Docs; operasyonları birbirine göre dönüştürür | Çok zor | Kullanmayacağız |
| **CRDT** | Çakışmasız veri tipleri | Çok zor | Kullanmayacağız |

**İlk büyük kararımız (ADR 0001):** Serbest metin değil, **yapılandırılmış Kanban** yaptığımız için OT/CRDT'ye gerek yok. Değişiklikler ayrık operasyonlar; çakışmayı **versiyonlama + LWW** ile çözeceğiz. Doğru senior kararı: problemi çözülebilir bir şekle sokmak, en karmaşık aracı seçmek değil.

---

## FAZ 1 — Temel + gerçek zamanlı senkron (1. hafta)

**Tek sunucu. Çakışma yok, presence yok. Amaç: bir kart hareketi iki tarayıcı sekmesinde senkron çalışsın.**

### Ne kuracaksın
- Domain: `Board`, `Column`, `Card` (temiz modeller)
- REST: `POST /boards` (pano oluştur), `GET /boards/{id}` (panonun tam halini al)
- **WebSocket bağlantısı** (STOMP protokolü) — bir panoya "abone ol"
- Operasyon yayını: bir kullanıcı `MOVE_CARD` yollar → sunucu uygular → panodaki herkese push eder
- **Minimal frontend:** panoyu gösteren, kart sürüklenen, WebSocket'e bağlı basit bir HTML/JS sayfası
- Postgres + Docker Compose

### Ne öğreneceksin — asıl mesele
**WebSocket/STOMP mantığı** ve **operasyon tabanlı senkronizasyon.** İki sekme açıp birinde kartı taşıyınca diğerinde kaydığını görmek — "gerçek zamanlı" kavramının somut hali.

**Kontrol sorusu:** "Sunucu, bir kullanıcının değişikliğini diğerlerine nasıl ulaştırıyor? Diğerleri tekrar sormadan (polling'siz) nasıl haberdar oluyor?"

### Karar noktaları (ADR)
1. **Protokol: Ham WebSocket mi, STOMP mu, SSE mi?** (STOMP öneri — Spring'le uyumlu, "topic/abone ol" modeli hazır. Neden?)
2. **Operasyon modeli nasıl?** Her değişiklik bir operasyon; tipleri ne olacak?
3. **Yeni katılan ne görür?** Panoya bağlanınca önce tam state (REST), sonra canlı operasyonlar (WS). Neden ikisi birden?

### Faz 1 bittiğinde
İki sekmede canlı senkron çalışan bir Kanban. İlk WebSocket deneyimin.

---

## FAZ 2 — Çakışma çözümü + presence (2. hafta)

**Artık "iki kişi aynı anda ne yaparsa?" sorusuyla ilgileniyoruz. Bu fazın en güçlü kozu: eşzamanlılık.**

### Ne ekleyeceksin
- **Çakışma çözümü:** Her kartta bir versiyon; eski versiyonla gelen operasyon reddedilir (optimistic). Alan düzenlemede LWW. Aynı anda taşınan kart için tutarlı sonuç.
- **Presence:** Kim çevrimiçi (panoda kimler var), kimin imleci nerede — canlı avatarlar/imleçler.
- İstemci reddedilen operasyonu nasıl telafi eder (state'i sunucuya göre düzeltir)?

### Ne öğreneceksin
- **Eşzamanlı düzenleme problemleri** — yaşayarak. Orchestra'daki "optimistic locking / yarış durumu" tartışması burada gerçek oluyor.
- **Presence takibi** — bir kullanıcı bağlanınca/kopunca nasıl anlarsın, herkese nasıl bildirirsin.
- **Optimistic UI** — istemci değişikliği hemen gösterir, sunucu reddederse geri alır.

### Karar noktaları (ADR)
1. **Çakışma stratejisi:** versiyonlama mı, LWW mi, hangisi nerede? Trade-off?
2. **Presence'i nerede tutuyorsun?** Bellekte mi (tek sunucu), yoksa dış bir yerde mi (çok sunucu için)? (Faz 3'e köprü.)
3. **Reddedilen operasyonda istemci ne yapar?**

---

## FAZ 3 — Çok sunucuya ölçekleme (3. hafta) — SENIOR KISMI

**Şimdi asıl zor soru: aynı uygulamadan 2 kopya çalıştırırsan ne olur?**

### Problem
WebSocket bağlantısı **belirli bir sunucuya** bağlıdır. Sunucu A'ya bağlı kullanıcı kartı taşırsa, A sadece **kendine bağlı** istemcilere push eder. Sunucu B'ye bağlı kullanıcılar **hiçbir şey görmez!** Çünkü B, A'daki değişiklikten habersiz.

```
Kullanıcı1 ──► Sunucu A ──► Kullanıcı2   (A'ya bağlı olanlar görür)
                  ✗
Kullanıcı3 ──► Sunucu B ──► Kullanıcı4   (B'ye bağlı olanlar A'yı GÖREMEZ)
```

### Çözüm: Redis Pub/Sub ile yayın
Sunucu A bir operasyon alınca onu **Redis'e yayınlar (publish)**. Tüm sunucular (A ve B) Redis'i **dinler (subscribe)**. B mesajı Redis'ten alıp kendi istemcilerine push eder. Böylece hangi sunucuya bağlı olursan ol, herkes her şeyi görür.

```
Sunucu A ──publish──► Redis Pub/Sub ──►  A dinler → A'nın istemcileri
                          │              B dinler → B'nin istemcileri
Sunucu B ──publish───────┘
```

### Ne öğreneceksin
- **WebSocket'in ölçeklenmesi** — dağıtık sistemin en sık sorulan gerçek zamanlı problemi.
- **Pub/Sub yayın (fan-out)** deseni.
- **Yeniden bağlanma (reconnection):** kullanıcının hattı kopunca, tekrar bağlanınca kaçırdığı değişiklikleri nasıl toparlar (state resync).
- **Operasyon sıralaması:** farklı sunuculardan gelen operasyonların tutarlı sırası.

### Karar noktaları (ADR)
1. **Ölçekleme yaklaşımı:** Redis Pub/Sub mı, sticky session mı, özel bir broker mı? Neden Redis?
2. **Reconnection'da kaçan operasyonlar:** tam state mi çekilir, yoksa eksik operasyonlar mı? Trade-off?
3. **Presence çok sunucuda nasıl?** (Faz 2'deki bellek çözümü burada çöker — Redis'e taşınır.)

---

## FAZ 4 — Cila / operasyonel olgunluk

- **Auth** (boilerplate'ten): kim düzenliyor, gerçek kullanıcı adları presence'ta
- Pano geçmişi / audit (kim ne zaman ne yaptı)
- Observability (Actuator + metrikler: aktif bağlantı sayısı, operasyon hızı)
- README + mimari diyagram (mermaid) + ADR klasörü

---

## Frontend gerçekliği

Bu proje **görülmeden gösterilemez** — collaborative panoyu ancak iki tarayıcı sekmesi açıp senkronu izleyerek anlarsın. O yüzden **minimal bir HTML/JS/CSS** frontend yazacağız. Odak backend, ama bu frontend demo için şart — ve projeyi **görsel/etkileyici** yapacak. Karmaşık bir framework yok; vanilya JS + WebSocket yeterli.

---

## Bitiş — vitrini yapan dokunuşlar

1. **README** — ne yaptığın, mimari diyagram (mermaid), "nasıl çalıştırılır", canlı senkronun ekran görüntüsü/GIF'i.
2. **ADR klasörü** — protokol, çakışma stratejisi, ölçekleme kararları. Gizli silahın.
3. **Çalışan demo** — iki sekme, canlı senkron.

---

## Teknoloji yığını

| Katman | Seçim |
|--------|-------|
| Dil / Framework | Java 21, Spring Boot 3 |
| Gerçek zamanlı | Spring WebSocket + STOMP |
| Veritabanı | PostgreSQL (pano kalıcılığı) |
| Ölçekleme / Presence | Redis (Pub/Sub + presence) |
| Frontend | Minimal HTML/JS/CSS (vanilya) |
| Altyapı | Docker Compose |
| Kararlar | ADR |

---

## Uyarılar

- **Kapsam patlaması baş düşman.** "Bir de yorum, bir de etiket, bir de dosya ekleme..." → proje bitmez. **Çekirdek: pano + kart + gerçek zamanlı senkron + ölçekleme.** Gerisi süs.
- **CRDT/OT tavşan deliğine girme.** Yapılandırılmış Kanban + versiyonlama yeterli; Google Docs yazmıyoruz.
- **Her faz kendi başına gösterilebilir olmalı.** Faz 1 bitince "işte canlı senkron" diyebilmelisin.
- **AI kullan ama her satırı anla.** "Burada neden bu?" sorusuna cevabın yoksa, o parçayı henüz öğrenmemişsin demektir.
- **Commit mesajları da vitrin.** "fix" değil; "Add operation-based card move broadcast over WebSocket" gibi.
