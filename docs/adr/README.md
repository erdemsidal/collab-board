# Mimari Karar Kayıtları (ADR)

Bu klasör, projedeki önemli teknik kararları ve **gerekçelerini** tutar. Her ADR
şu iskeleti izler: **Bağlam → Seçenekler → Karar → Sonuçlar (feda ettiklerimiz).**

Amaç: "ne kodladık" değil, **"neden o kararı verdik"** sorusunun cevabını kalıcı
kılmak. Bir kararı sonradan sorgularken (veya mülakatta savunurken) buraya bakarız.

## Kayıtlar

| No | Karar | Durum |
|----|-------|-------|
| [0001](0001-operasyon-tabanli-model-ve-cakisma-cozumu.md) | Operasyon tabanlı model + versiyonlama/LWW (OT/CRDT değil) | Kabul edildi |
| [0002](0002-gercek-zamanli-protokol-stomp.md) | Gerçek zamanlı protokol: STOMP over WebSocket | Kabul edildi |
| [0003](0003-cakisma-cozumu-versiyon-kontrolu.md) | Çakışma çözümü: optimistic versiyon kontrolü + snapshot resync | Kabul edildi |
| 0004 | Çok sunucuya ölçekleme: Redis pub/sub | Planlandı (Faz 3) |
