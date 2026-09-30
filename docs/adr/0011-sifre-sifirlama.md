# ADR 0011 — Şifre sıfırlama

- **Durum:** Kabul edildi
- **Tarih:** 2026-09-25

---

## Bağlam

Şifresini unutan kullanıcının geri dönüş yolu yoktu; hesabı sonsuza dek
kilitli kalıyordu. E-posta doğrulamayla (ADR 0009) posta ve tek kullanımlık
jeton altyapısı hazır olduğu için akışın kendisi küçük bir iş.

Ama bu jeton doğrulama jetonundan **çok daha değerli.** Doğrulama jetonu
sızarsa en fazla bir hesap etkinleşir; sıfırlama jetonu sızarsa hesap
**ele geçer.** Aynı deseni kopyalamak yetmez, üç yerde sıkılaştırmak gerekiyor.

## Karar

### 1. Jeton özetlenerek saklanır

Veritabanında ham jeton değil, **SHA-256 özeti** durur. Gelen jeton aynı şekilde
özetlenip aranır. Veritabanı yedeği çalınsa bile içindeki özetlerden bağlantı
yeniden üretilemez.

Neden BCrypt değil? BCrypt'in yavaşlığı, insanların seçtiği tahmin edilebilir
şifrelere karşı bir savunma. Buradaki girdi 256 bit rastgele bir dize —
sözlük saldırısı ya da ön hesaplanmış tablo işe yaramaz. Yavaş bir algoritma
yalnızca her istekte boşa işlemci harcardı.

Doğrulama jetonları (V11) ham saklanmaya devam ediyor: sızmalarının bedeli
küçük ve mevcut veriyi göç ettirmek bu değişikliğin kapsamı dışında.

### 2. Kısa ömür: 30 dakika

Doğrulama bağlantısı 24 saat yaşıyor, çünkü kişi postayı ne zaman açacağını
bilmiyor olabilir. Sıfırlamayı ise kişi **kendisi** tetikledi ve postayı o an
bekliyor. Uzun ömür, yalnızca bağlantının çalınabileceği pencereyi büyütür.

### 3. Sıfırlama bütün oturumları kapatır

Şifre sıfırlamanın yaygın sebebi, şifrenin çalındığından şüphelenmek. Yalnızca
şifreyi değiştirmek o durumda saldırganı dışarı atmaz: elindeki refresh token
7 gün boyunca yeni erişim jetonu üretmeye devam eder.

Bunun için `RefreshTokenService`'e kullanıcı başına bir dizin eklendi
(`user_refresh_tokens:<userId>`). Önceden jetondan kullanıcıya gitmek kolaydı
ama tersi yoktu — bir kullanıcının bütün oturumlarını bulmanın yolu yoktu.

Erişim jetonları (JWT) sunucuda tutulmadığı için geri alınamaz; en fazla
15 dakikalık ömürleri dolana kadar geçerli kalırlar. Kabul edilen bir boşluk.

### Diğer kararlar

- **Kayıtsız adres aynı cevabı alır**, posta gitmez — doğrulama postasındaki
  gerekçeyle aynı: uç, kayıtlı adresleri sorgulama aracına dönüşmemeli.
- **Doğrulanmamış hesap sıfırlamayla etkinleşir.** Sıfırlama postasını
  açabilmek, doğrulama postasının kanıtladığı şeyin aynısını kanıtlıyor.
- **Şifre kuralı kayıttakiyle aynı.** Sıfırlama, kayıtta reddedilecek bir
  şifreyi kabul etmenin arka kapısı olmamalı.
- **Yeni istek eskisini geçersiz kılar**; aynı anda yalnızca bir bağlantı geçerli.
- **Hız sınırı:** istek saatte 3 (başkasının gelen kutusunu korumak için),
  tamamlama saatte 10.

## Bu çalışma sırasında kapatılan iki sızıntı

Oturum kapatmayı eklerken fark edildi:

- `RefreshTokenService` refresh token'ları **loglara düz metin** yazıyordu.
  Logları okuyabilen herkes — izleme servisi, destek ekibi, sızan bir yedek —
  o oturumları devralabilirdi. Artık yalnızca ilk 8 karakter yazılıyor.
- `TokenRefreshException` jetonu **hata mesajına** gömüyordu; mesaj hem
  loglanıyor hem istemciye dönüyordu. Çıkarıldı.

## Sonuçlar

**Kazandıklarımız**
- Şifresini unutan kullanıcı artık hesabına dönebiliyor.
- Şifre çalındığında sıfırlama gerçekten koruyor — açık oturumlar da kapanıyor.
- Loglar artık oturum jetonu taşımıyor.

**Bilinen sınırlar**
- Süresi dolan sıfırlama jetonları da (doğrulama jetonları gibi) tabloda
  birikiyor; temizleyen bir zamanlanmış iş yok.
- Açık erişim jetonları sıfırlamadan sonra en fazla 15 dakika daha geçerli.
- Giriş yapmış kullanıcının kendi şifresini değiştirmesi (mevcut şifreyi
  bilerek) ayrı bir akış ve henüz yok.
