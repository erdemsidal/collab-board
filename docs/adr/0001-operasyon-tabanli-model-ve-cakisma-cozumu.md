# ADR 0001 — Operasyon tabanlı model + versiyonlama/LWW (OT/CRDT değil)

- **Durum:** Kabul edildi
- **Tarih:** 2026-07-24
- **Bağlam fazı:** Faz 1 (temel) → Faz 2 (çakışma çözümü)

---

## Bağlam

CollabBoard, birden fazla kullanıcının **aynı anda** düzenlediği bir Kanban
panosudur. İki temel soruyu baştan cevaplamamız gerekiyor:

1. **Bir değişikliği ağ üzerinden nasıl temsil edeceğiz?** Her düzenlemede tüm
   panoyu (bütün kolonlar + kartlar) göndermek, pano büyüdükçe israfa dönüşür ve
   her mesaj bir öncekini eziyor.
2. **İki kullanıcı aynı anda aynı şeyi değiştirirse ne olacak?** Kimin
   değişikliği kazanır, veri kaybını nasıl sınırlarız?

Önemli bir gözlem: Panomuz **serbest metin değil, yapılandırılmış** bir yapıdır
(pano → kolonlar → kartlar; kartın başlığı, sırası, bulunduğu kolon gibi ayrık
alanları vardır). Bu, çözüm uzayını daraltır — Google Docs gibi karakter karakter
birleştirme problemimiz yok.

## Karar vericiler (neye göre seçtik)

- **Öğrenme/karmaşıklık dengesi:** Odak gerçek zamanlı senkron ve ölçekleme;
  amaç en karmaşık algoritmayı yazmak değil, doğru soruna doğru aracı seçmek.
- **Verimlilik:** Ağdan yalnızca değişen kısım gitmeli.
- **Çakışmada öngörülebilir sonuç:** Aynı kart iki kişide aynı anda değişince
  tutarlı, savunulabilir bir sonuç.
- **Faz 3'e ölçeklenebilirlik:** Seçtiğimiz model çok sunuculu yayına uygun olmalı.

## Seçenekler

### 1) Tüm panoyu her seferinde gönder (full-state)
- ➕ Basit.
- ➖ Büyük panoda israf; eşzamanlı düzenlemede son gönderen her şeyi ezer, araya
  giren değişiklikler kaybolur. **Elendi.**

### 2) Operasyon tabanlı + versiyonlama/LWW  ← SEÇİLEN
Her değişikliği küçük, ayrık bir **operasyon** olarak yolla:
```json
{ "type": "MOVE_CARD", "cardId": 7, "toColumn": "done", "position": 2, "baseVersion": 5 }
{ "type": "EDIT_CARD", "cardId": 7, "title": "Yeni başlık", "baseVersion": 5 }
{ "type": "ADD_CARD",  "columnId": 1, "title": "..." }
```
Çakışma çözümü iki katmanlı:
- **Versiyonlama (optimistic):** Her kartta bir `version` sayısı var. Operasyon
  hangi versiyona dayandığını (`baseVersion`) söyler. Sunucudaki güncel versiyon
  farklıysa (birisi arada değiştirmiş) operasyon **reddedilir**; istemci güncel
  state'i alıp yeniden dener. Kritik/yapısal değişiklikler için (taşıma vb.).
- **Last-Write-Wins (LWW):** Serbest alan düzenlemesinde (ör. başlık metni) son
  yazan kazanır — basit, kabul edilebilir veri kaybı riski.

- ➕ Verimli (sadece delta gider), çakışmada öngörülebilir, Faz 3'te Redis ile
  yayınlanmaya birebir uygun, uygulaması makul.
- ➖ İstemcinin reddedilen operasyonu telafi etmesi gerekir (Faz 2'de ele alınacak).

### 3) OT (Operational Transformation) — Google Docs
- ➕ Eşzamanlı serbest metin düzenlemede en güçlüsü.
- ➖ **Çok karmaşık.** Operasyonları birbirine göre dönüştürme mantığı, doğru
  uygulanması zor bir alandır. Bizim **yapılandırılmış** panomuz bunu gerektirmez.
  **Elendi** (kapsam/karmaşıklık tavşan deliği).

### 4) CRDT (Conflict-free Replicated Data Types)
- ➕ Çakışmasız birleşme, offline-first senaryolar.
- ➖ Kavramsal yük ve veri yapısı karmaşıklığı yüksek; problemimiz için fazla
  ağır. **Elendi.**

## Karar

**Operasyon tabanlı model** kullanacağız. Çakışmayı **versiyonlama (optimistic
locking) + LWW** ile çözeceğiz: yapısal değişikliklerde versiyon kontrolü, serbest
alanlarda LWW. **OT ve CRDT kullanmayacağız.**

Gerekçenin özü: Problemi, elimizdeki araçlarla çözülebilir bir şekle sokmak.
Yapılandırılmış Kanban, OT/CRDT'nin çözdüğü zor birleştirme problemini zaten
ortadan kaldırıyor; o karmaşıklığı satın almanın karşılığı yok.

## Sonuçlar (neyi kazandık, neyi feda ettik)

- ➕ Ağ trafiği minimal; her operasyon Faz 3'te Redis üzerinden aynen yayınlanabilir.
- ➕ Çakışma sonucu öngörülebilir ve mülakatta savunulabilir.
- ➕ Uygulama karmaşıklığı öğrenme hedefiyle uyumlu.
- ➖ Aynı serbest metni eşzamanlı düzenlemede LWW veri kaybına yol açabilir — bunu
  **bilerek** kabul ediyoruz (kritik alanlar zaten versiyonla korunuyor).
- ➖ İstemcinin "reddedilen operasyon" senaryosunu ele alması gerekir → Faz 2 işi
  (optimistic UI + resync).

## İlgili

- Protokol kararı: [ADR 0002](0002-gercek-zamanli-protokol-stomp.md)
