# ADR 0002 — Gerçek zamanlı protokol: STOMP over WebSocket (ham WS / SSE değil)

- **Durum:** Kabul edildi
- **Tarih:** 2026-07-24
- **Bağlam fazı:** Faz 1 (temel), Faz 3'e (ölçekleme) etkisiyle

---

## Bağlam

Bir kullanıcı kartı taşıyınca, aynı panoyu açık tutan diğer kullanıcılar bunu
**anında** (polling'siz) görmeli. Bunun için istemci→sunucu ve sunucu→istemci
**çift yönlü**, sürekli açık bir kanala ve mesajları doğru panonun izleyicilerine
dağıtan bir **yayın (pub/sub)** mekanizmasına ihtiyacımız var.

## Karar vericiler

- **Çift yönlü:** İstemci operasyon *yollar*, sunucu değişikliği *push eder*.
- **Yayın (fan-out):** Bir operasyon, o panoyu izleyen herkese ulaşmalı.
- **Spring uyumu:** Boilerplate Spring Boot 3 tabanlı; hazır destek tercih.
- **Faz 3 ölçeklenebilirliği:** Tek sunucudan çok sunucuya geçişte kod değişmeden
  broker'ı değiştirebilmek.

## Seçenekler

### 1) Ham (raw) WebSocket
Sadece açık, çift yönlü bir boru verir; mesaj formatı, "abonelik", routing ve
fan-out'un hepsini elle yazarız.
- ➕ Tam kontrol, sıfır soyutlama.
- ➖ Abonelik defteri, mesaj yönlendirme, format — hepsini yeniden icat ederiz.
  Odağımız senkron mantığı, altyapı değil. **Elendi.**

### 2) SSE (Server-Sent Events)
Sunucu→istemci **tek yönlü** push akışı.
- ➕ Basit, HTTP üzerinden çalışır.
- ➖ İstemci→sunucu yönü yok; bizde istemci de operasyon gönderiyor. Çift yönlü
  değil. **Elendi.**

### 3) STOMP over WebSocket  ← SEÇİLEN
WebSocket borusunun içinden konuşulan mesajlaşma protokolü (HTTP'nin TCP üzerinde
oluşu gibi, STOMP da WebSocket üzerinde). Hazır **destination + pub/sub** modeli:
istemci `/topic/board.{id}`'e SUBSCRIBE olur, sunucu aynı adrese yayın yapar,
**broker** doğru abonelere dağıtır.
- ➕ Çift yönlü; hazır abonelik/fan-out; Spring'in yerleşik desteği
  (`@MessageMapping`, `SimpMessagingTemplate`).
- ➕ **Faz 3'e köprü:** Tek sunucudaki basit yerleşik broker, kod değişmeden
  Redis tabanlı bir relay ile değiştirilebilir. `convertAndSend(...)` aynı kalır.
- ➖ Ham WS'e göre bir soyutlama katmanı (frame/protokol) öğrenmek gerekir —
  ama bu öğrenme hedefimizle uyumlu.

## Karar

**STOMP over WebSocket** kullanacağız. Adres kuralı: her pano bir topic'e karşılık
gelir → `/topic/board.{boardId}`. İstemciler bu adrese abone olur; operasyonlar
sunucuda uygulanıp aynı adrese yayınlanır.

Topic'ler **önceden oluşturulmaz** — bir string adrestir, ID'den türetilir
(`"/topic/board." + boardId`), ilk abone olununca broker defterinde belirir. Yani
pano sayısı arttıkça topic'ler otomatik oluşur; elle yönetim yoktur.

## Sonuçlar

- ➕ Faz 1'de iş mantığına odaklanırız; abonelik/fan-out Spring+broker'ın işi.
- ➕ Faz 3'te broker'ı Redis relay'e çevirmek, uygulama kodunu kırmaz.
- ➕ Sunucu = tek buluşma noktası (santral); istemciler doğrudan değil, sunucu
  üzerinden haberleşir → yetki/doğrulama tek noktada.
- ➖ STOMP frame modeli ek bir kavramsal katman; ham WS'in ince kontrolünden feragat
  ediyoruz (bizim için kayıp değil, kazanç).
- ➖ Tek sunucudaki yerleşik broker üretimde sınırlıdır → çok sunucuda Redis'e
  geçiş **gerekli** (bilinen, planlı borç: Faz 3).

## İlgili

- Veri modeli & çakışma: [ADR 0001](0001-operasyon-tabanli-model-ve-cakisma-cozumu.md)
- Ölçekleme (Redis pub/sub): ADR 0003 (Faz 3'te yazılacak)
