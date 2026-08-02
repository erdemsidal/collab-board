# ADR 0006 — Geçmiş ve akış ölçümü: olay kaydından yeniden hesaplama

- **Durum:** Kabul edildi
- **Tarih:** 2026-08-01

---

## Bağlam

İki yeni yetenek istendi:

1. **Zaman yolculuğu** — panoyu geçmişteki herhangi bir ana geri sarmak. Retrospektifte "geçen hafta neredeydik", anlaşmazlıkta "bu kartı kim taşımıştı", denetimde "o gün durum neydi" sorularının cevabı.
2. **Akış sağlığı** — kartların kolonlarda ne kadar beklediği, çevrim süresi, darboğazın nerede olduğu. Kanban'ın asıl amacı iş listesi tutmak değil, akışı yönetmektir.

Elimizde zaten bir pano geçmişi vardı ama **yalnızca insan için** yazılmıştı:

```
"'Süt al' kartını Done kolonuna taşıdı"
```

Bu cümle okunur, ama ondan panoyu yeniden kuramazsın: hangi kart, hangi kolon, hangi sıra — hiçbiri makine tarafından okunabilir değil.

## Karar vericiler

- **İki özellik de aynı soruyu soruyor:** "geçmişte ne oldu?" Ortak bir temel kurmak, iki ayrı mekanizmadan iyidir.
- **Mevcut veriyi bozmamak.** Kayıtlı geçmiş silinmemeli.
- **Doğruluk.** Yeniden kurulan durum, o anki gerçekle birebir aynı olmalı.
- **Kapsam.** Tam bir event-sourcing mimarisine geçmek (tüm okumaların olaylardan türetilmesi) bu projenin ihtiyacının çok üstünde.

## Seçenekler

### 1) Durum anlık görüntüleri (snapshot) saklamak
Her değişiklikte panonun tam hâlini kaydet.
- ➕ Geri sarmak çok kolay: ilgili satırı oku.
- ➖ Veri şişer (her kart hareketinde tüm pano kopyalanır) ve akış ölçümü için yine iki görüntüyü karşılaştırmak gerekir. **Elendi.**

### 2) Geri alma (undo) ile bugünden geriye gitmek
Bugünkü durumdan başlayıp olayların tersini uygulamak.
- ➕ Ek veri gerekmez.
- ➖ Her operasyonun **tersini** tanımlamak gerekir. Silme işleminin tersi, silinen kartın içeriğini geri getirmektir — yani o içeriği zaten bir yerde saklamış olmalısın. Sorunu çözmüyor, taşıyor. **Elendi.**

### 3) Olay kaydını yapılandırıp baştan ileri sarmak  ← SEÇİLEN
Mevcut geçmiş tablosuna, yayınlanan olayın JSON hâlini saklayan bir alan eklenir. Durum, kolonlar boşken başlanıp olaylar sırayla uygulanarak hesaplanır.

- ➕ **Tek temel, iki özellik.** Aynı kayıt hem geri sarmayı hem bekleme sürelerini besliyor.
- ➕ Tersine çevirme borcu yok; ileri sarmada silme işlemi sadece "listeden çıkar"dır.
- ➕ Mevcut tablo ve akış korunur; yalnızca bir sütun eklenir.
- ➖ Uzun geçmişte her sorgu tüm olayları okur (aşağıda).

## Karar

**Olay kaydı yapılandırılır ve durum ileri sarılarak hesaplanır.**

- `board_activities` tablosuna `payload` (JSON) alanı eklenir; yayınlanan olay olduğu gibi saklanır. Açıklama metni insan için, payload makine için.
- Kayıt, operasyonun **kendi işlemi içinde** yazılır: reddedilen bir operasyon (ADR 0003) geçmişe de girmez, dolayısıyla yeniden kurulan durum gerçeği yansıtır.
- **Sıralama `id`'ye göredir**, zaman damgasına göre değil: aynı milisaniyede iki olay olabilir, ama id sırası her zaman gerçek uygulanma sırasıdır.
- **Kolonlar güncel hâlleriyle başlar.** Kolonlar panoyla birlikte oluşur ve silinmez; yalnızca sıraları olaylardan hesaplanır. Kartlar ise sıfırdan kurulur.
- Geri sarılan an, **olay kimliğiyle** belirtilir (`?upTo=42`) — "42 numaralı olaydan sonraki durum". Zaman damgası yerine kimlik kullanmak belirsizliği ortadan kaldırır.
- **Geçmiş modunda arayüz salt okunurdur.** Geçmişe kart eklemek anlamsızdır ve iyimser arayüz o anı "şimdi" sanardı.
- Akış ölçümü aynı kayıttan türetilir: bir kart bir kolona girdiğinde ve çıktığında zaten olay var, bekleme süresi bu iki damganın farkıdır. Kart üzerinde "kaç gündür burada" diye bir alan tutmaya gerek yoktur.

## Sonuçlar

- ➕ Panonun herhangi bir anı yeniden kurulabilir; silinen kartlar geçmişte görünmeye devam eder.
- ➕ Darboğaz, ek veri modeli olmadan ölçülebilir hâle gelir.
- ➕ Geçmiş artık yalnızca bir günlük değil, **veri kaynağı**.
- ➖ **Bu özellikten önceki kayıtlar yeniden kurulamaz** (payload'ları yok). Geçmiş listesinde görünmeye devam ederler ama hesaba katılmazlar. Geriye dönük üretmek mümkün değil — o bilgi hiç kaydedilmemişti.
- ➖ **Her sorgu tüm olayları okur.** Yüzlerce olayda sorun değil; on binlerde periyodik anlık görüntü (snapshot) alıp "son snapshot + sonraki olaylar" şeklinde hesaplamak gerekir. Bilinen ve ölçülebilir bir sınır.
- ➖ Olay biçimi artık bir **sözleşme**: yayınlanan olayların alan adları değişirse eski kayıtlar okunamaz hâle gelir. Değiştirmek gerekirse sürümleme gerekir.
- ➖ Akış ölçümü yalnızca **bu özellikten sonra** eklenen kartlar için tam doğrudur; öncekilerin başlangıç anı bilinmediği için çevrim süresine katılmazlar.

## İlgili

- Operasyon tabanlı model: [ADR 0001](0001-operasyon-tabanli-model-ve-cakisma-cozumu.md)
- Reddedilen operasyonun geçmişe yazılmaması: [ADR 0003](0003-cakisma-cozumu-versiyon-kontrolu.md)
