# ADR 0008 — Kolon yaşam döngüsü ve geçmişin tohumlanması

- **Durum:** Kabul edildi
- **Tarih:** 2026-08-21

---

## Bağlam

Pano üç sabit kolonla doğuyordu: `To Do`, `In Progress`, `Done`. Kullanıcı kolon
ekleyemiyor, silemiyor, adını değiştiremiyordu — yalnızca sıralarını değiştirebiliyordu.

Kimsenin süreci tam olarak bu üç adım değil. Biri "Kod İncelemesi" ister, biri
"Beklemede". Sabit kolonlar aracı gerçek işte kullanılamaz kılıyor ve ilk otuz
saniyede fark ediliyordu.

Operasyonları eklemek kolay kısımdı — `MOVE_COLUMN` zaten vardı, yanına üç tane
daha gelecekti. Asıl mesele **geçmişi bozmamaktı.**

## Sorun: "bugünkü kolonlar" artık başlangıç hâli değil

ADR 0006'daki yeniden kurulum şöyle çalışıyordu:

> Kolonların kendisi panoyla birlikte oluşur ve silinmez; bu yüzden **güncel
> kolonlardan başlanır**, yalnızca sıraları olaylardan hesaplanır.

Bu varsayım artık geçersiz. İki yeni durum doğuyor:

| Durum | Naif davranış | Olması gereken |
|---|---|---|
| Kolon dün eklendi | Bugünkü kolonlardan başlandığı için **geçen haftaya da** görünür | Eklenmeden önceki anlarda görünmemeli |
| Kolon dün silindi | Bugün panoda olmadığı için **hiç var olmamış** gibi görünür | Silinmeden önceki anlarda görünmeli |

İkisi de sessiz hatalar: bir şey patlamaz, geçmiş sadece yanlış olur. Zaman
yolculuğunun tüm değeri doğruluğundan geldiği için bu kabul edilemez.

## Seçenekler

### 1) Kolonları tamamen olaylardan kurmak
Pano oluşturulurken ilk üç kolon için de olay yaz, sonra her şeyi kayıttan türet.
- ➕ Kavramsal olarak en temiz: tek doğruluk kaynağı.
- ➖ **Mevcut panoların ilk kolonları için böyle bir olay yok.** Geriye dönük veri
  üretmek (backfill migration) gerekir; üretilen olayın zaman damgası da uydurma
  olur. Kayıtlı geçmişi bozmama ilkesine aykırı. **Elendi.**

### 2) Silme işlemini "arşivleme"ye çevirmek
Kolonu gerçekten silmemek, gizlemek.
- ➕ Geçmiş sorunu kendiliğinden çözülür: kolon hep orada.
- ➖ Kullanıcı sildiğini silinmiş sanar. Ayrıca "silinen veriler gerçekten silinsin"
  ilkesine aykırı. **Elendi.**

### 3) Başlangıç hâlini düzelterek tohumlamak — **seçilen**
Yeniden kuruluma "bugünkü kolonlar" ile değil, **hesaplanmış bir başlangıç** ile
başlamak.

## Karar

Üç operasyon eklendi: `ADD_COLUMN`, `RENAME_COLUMN`, `DELETE_COLUMN`.

`DELETE_COLUMN` olayı, silinen kolonun **adını ve konumunu da taşır.** İstemcinin
bu bilgilere ihtiyacı yok — yalnızca geçmiş yeniden kurulurken gerekiyorlar. Silinmiş
bir kolon artık panoda bulunmadığı için, geçmişteki hâlini ancak bu kayıttan geri
getirebiliriz.

Yeniden kurulumun başlangıç hâli şöyle hesaplanır:

```
tohum = (bugünkü kolonlar − sonradan EKLENENLER)
      + (DELETE_COLUMN kayıtlarından SİLİNENLER − sonradan EKLENENLER)
```

Dört durumun hepsi doğru çıkar:

| Kolon | ADD kaydı | Bugün var mı | Tohumda | Nasıl doğru olur |
|---|---|---|---|---|
| Baştan beri var | yok | evet | **evet** | Hep görünür |
| Sonradan eklendi | var | evet | hayır | ADD olayı sırası gelince ekler |
| Baştan vardı, silindi | yok | hayır | **evet** | DELETE olayı sırası gelince kaldırır |
| Eklendi ve silindi | var | hayır | hayır | ADD ekler, DELETE kaldırır |

## Sonuçlar

**Kazandıklarımız**

- Pano artık gerçek bir süreci modelleyebiliyor.
- Zaman yolculuğu kolon değişikliklerine rağmen doğru kalıyor; "geçen hafta neredeydik"
  sorusu o haftanın kolonlarıyla cevaplanıyor.
- Geriye dönük veri üretimi yapılmadı; kayıtlı geçmiş olduğu gibi duruyor.

**Feda ettiklerimiz**

- Tohumlama, panonun **tüm** olay kaydını bir kez tarıyor — yalnızca istenen ana
  kadarkileri değil. Sonradan eklenmiş bir kolonu tanıyabilmek için kesme
  noktasından sonraki kayıtlara da bakmak gerekiyor. Uzun geçmişli panolarda bu
  fazladan bir tarama demek. Kabul edildi: ölçüm zaten tüm kaydı okuyor ve bu uç
  bir kullanım değil.
- `DELETE_COLUMN` olayı, istemcinin kullanmadığı iki alan taşıyor. Gereksiz gibi
  görünen bu fazlalık bilinçli: olay kaydı yalnızca bildirim değil, **geçmişin
  kendisi.** Silinen bir şeyin geri kurulabilmesi için gereken her şeyi taşımalı.
- Kolon silme, kartlarını da kalıcı olarak siler. Arayüz onay ister ama geri alma
  yoktur; yalnızca geçmişte görülebilir.

**Bilinen sınırlar**

- `MOVE_COLUMN` hâlâ diğer kolonların sırasını yeniden düzenlemiyor (silme
  düzenliyor). Kolon sıralamasında seyrek de olsa boşluklu konumlar oluşabilir.
- Bir panonun tüm kolonları silinebilir. Arayüz her zaman "Kolon ekle" sunduğu için
  çıkmaz değil, ama boş pano anlamlı bir durum sayılmamalı.
