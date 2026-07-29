# ADR 0003 — Çakışma çözümü: optimistic versiyon kontrolü + snapshot resync

- **Durum:** Kabul edildi
- **Tarih:** 2026-07-29
- **Bağlam fazı:** Faz 2 (eşzamanlı düzenleme)

---

## Bağlam

Faz 1'de operasyonlar körü körüne uygulanıyordu. Sorun, "aynı milisaniye" değil,
**bayat karar** problemidir:

> İstemcinin kararı, ekranda gördüğü **eski** veriye dayanır. Sen kartı 10 saniye
> önceki hâline göre değiştirirken, başkası onu çoktan değiştirmiş olabilir.

Somut senaryo — Kart #7 "Süt al", `version=3`; Ayşe ve Bora ikisi de v3 görüyor:

```
Ayşe: title="2L Süt"     → uygulanır, v3 → v4
Bora: title="Badem sütü" → uygulanır, v4 → v5   ← Ayşe'nin yazdığı SESSİZCE kayboldu
```

Bu klasik **lost update** (kayıp güncelleme). Kimse uyarılmaz, kimse fark etmez.

## Karar vericiler

- **Sessiz veri kaybı kabul edilemez.** Kullanıcı en azından haberdar olmalı.
- **Kimseyi bloklamamalıyız.** İşbirliği aracında "kart kilitli" berbat bir deneyim.
- **Basitlik.** ADR 0001'de OT/CRDT'yi reddettik; burada da en basit yeterli çözüm.
- **Faz 3'e uyum.** Çözüm, çok sunuculu dünyada da çalışmalı.

## Seçenekler

### 1) Hiçbir şey yapma (mevcut durum)
- ➕ Basit, hiç reddetme yok.
- ➖ Sessiz veri kaybı. **Elendi.**

### 2) Pessimistic locking (kaydı kilitle)
Kart düzenlenirken kilitlenir, başkası dokunamaz.
- ➕ Çakışma imkânsız.
- ➖ Kullanıcıları **bloklar**; kilidi kim/ne zaman bırakacak (sekmeyi kapatırsa?);
  gerçek zamanlı işbirliğinin ruhuna aykırı. **Elendi.**

### 3) Optimistic versiyon kontrolü + resync  ← SEÇİLEN
Her operasyon, istemcinin gördüğü sürümü (`baseVersion`) taşır. Sunucu karşılaştırır:
```
gelen baseVersion == kartın güncel version ?  → uygula, version +1, herkese yayınla
                                        değil ?  → REDDET (hiçbir şey uygulanmaz)
                                                   + SADECE gönderene "OP_REJECTED" bildir
                                                   + istemci güncel state'i çekip ekranı düzeltir
```
- ➕ Sessiz kayıp yok; kimse bloklanmaz; `@Version` altyapısı zaten var (Faz 1).
- ➕ Faz 3'te çalışmaya devam eder (kontrol veritabanı sürümüne dayanır).
- ➖ "False conflict" mümkün (aşağıda).

## Karar

**Kart operasyonlarında optimistic versiyon kontrolü** uygulanacak:

| Operasyon | Strateji | Neden |
|-----------|----------|-------|
| `MOVE_CARD` | **Versiyon kontrolü** (bayatsa reddet) | Yapısal; kart iki yerde olamaz |
| `EDIT_CARD` | **Versiyon kontrolü** (bayatsa reddet) | Başlığın sessizce ezilmesi veri kaybıdır |
| `DELETE_CARD` | Kontrol yok | Silme nihai; "sildim ama sen değiştirmiştin" anlamsız |
| `ADD_CARD` | Kontrol yok | Yeni kayıt; çakışacak bir önceki sürüm yok |
| `MOVE_COLUMN` | **LWW** (son yazan kazanır) | Kolonda `version` alanı yok; sıra değişimi düşük riskli |

**Reddedilen operasyonda ne olur?**
1. Sunucu operasyonu **uygulamaz** (işlem geri alınır, DB'ye hiçbir şey yazılmaz).
2. **Sadece gönderene** `OP_REJECTED` mesajı gider (`/user/queue/errors`) —
   diğer kullanıcılar bu gürültüyü görmez.
3. İstemci **snapshot resync** yapar: `GET /api/boards/{id}` ile panonun tam hâlini
   yeniden çekip ekranı tazeler; kullanıcıya kısa bir bilgi mesajı gösterir.

**Neden resync için tam snapshot?** Elimizde zaten "fotoğraf" kanalı var (Faz 1) ve
her zaman doğru sonucu verir. Sadece çakışan kaydı yamamak daha "verimli" olurdu ama
daha kırılgan; aynı strateji Faz 3'te **yeniden bağlanma** için de kullanılacak.

Ayrıca **silinmiş kayda operasyon** gelirse (kart yokken EDIT/MOVE) aynı kanaldan
`NOT_FOUND` gerekçesiyle reddedilir; istemci resync ile kartı ekrandan düşürür.

## Sonuçlar

- ➕ Sessiz veri kaybı ortadan kalkar; kullanıcı "biri senden önce değiştirdi" bilgisini alır.
- ➕ Kimse bloklanmaz; optimistic model korunur.
- ➕ Reddetme gürültüsü yayına sızmaz (sadece gönderene gider).
- ➖ **False conflict:** Ayşe kartı taşır, Bora aynı anda *başlığını* düzenlerse —
  farklı alanlara dokundukları hâlde Bora'nın sürümü bayat sayılıp reddedilir.
  Teknik olarak kayıp olmayacaktı; kullanıcı gereksiz bir kez daha dener.
  **Bilerek kabul ediyoruz:** alan-bazlı sürümleme (her alana ayrı version) karmaşıklığı
  bu projede kazandıracağından fazlasını götürür. Otomatik resync sayesinde maliyet düşük.
- ➖ Tam snapshot resync, cerrahi bir yamaya göre biraz daha fazla veri taşır (kabul).

## İlgili

- Veri modeli & operasyonlar: [ADR 0001](0001-operasyon-tabanli-model-ve-cakisma-cozumu.md)
- Protokol: [ADR 0002](0002-gercek-zamanli-protokol-stomp.md)
- Ölçekleme (Redis pub/sub): ADR 0004 (Faz 3'te yazılacak)
