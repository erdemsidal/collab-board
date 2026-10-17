# ADR 0013 — Profil, şifre değiştirme ve profil fotoğrafı

- **Durum:** Kabul edildi
- **Tarih:** 2026-10-14

---

## Bağlam

Menüdeki "Profilim" hiçbir yere gitmiyordu. Kullanıcının adını düzeltmesi,
şifresini değiştirmesi (şifre sıfırlama yalnızca **unutan** içindi, ADR 0011)
ya da fotoğraf koyması mümkün değildi. Üye listesinde, çevrimiçi göstergesinde
ve geçmişte herkes yalnızca baş harfleriyle görünüyordu.

## Karar

### 1. E-posta değiştirilemez

Ad ve soyad değişir; e-posta değişmez. E-posta hem giriş adı hem davetlerin
bağlandığı anahtar (ADR 0012); değişmesi yeniden doğrulama ve bekleyen davetlerin
taşınması gerektirir. İstek gövdesinde `email` alanı gelirse sessizce yok
sayılmaz, istek `400` ile reddedilir — istemci yaptığını sandığı şeyin
yapılmadığını öğrenmeli.

### 2. Şifre değiştirme: mevcut şifre şart, diğer oturumlar kapanır

Açık bırakılmış bir oturumu ele geçiren biri şifreyi değiştirip hesabı
kilitleyememeli; bu yüzden **mevcut şifre** istenir. Değişince bütün refresh
token'lar iptal edilir (ADR 0011'deki kullanıcı başına dizinle) ve bu cihaza
**yeni bir jeton çifti** döner: şifreyi değiştiren kişi kendi oturumundan
atılmaz, diğer cihazlar atılır. Hız sınırı saatte 10.

### 3. Fotoğraf veritabanında, rastgele anahtarlı herkese açık adreste

| Seçenek | Neden seçilmedi / seçildi |
|---|---|
| Sunucunun diski | Railway gibi platformlarda disk kalıcı değil; iki sunucuda dosya birinde kalır |
| Nesne deposu (S3, R2) | Doğru çözüm ama yeni bir hesap, anahtar ve maliyet kalemi |
| **Postgres (`bytea`)** | Zaten var, verinin geri kalanıyla aynı yerde; 256×256 bir görsel ~20 KB |

Fotoğraf `/api/avatars/{anahtar}` adresinden **kimlik istemeden** sunulur, çünkü
`<img src>` istek başlığı gönderemez. Adres tahmin edilemesin diye anahtar
kullanıcı numarası değil rastgele bir UUID. Her yüklemede anahtar değişir; bu
sayede yanıt **bir yıl ve `immutable`** önbelleklenebilir — eski adres yeni
fotoğrafı asla göstermez, eski fotoğraf da silindiği için yeniden istenemez.

### 4. Dosya türüne güvenilmez

Uzantı ve istemcinin söylediği `Content-Type` yok sayılır; tür dosyanın ilk
baytlarından (imza) çıkarılır. Yalnızca PNG, JPEG ve WebP kabul edilir.
**SVG bilerek reddedilir:** SVG içinde betik çalışabilir ve herkese açık bir
adresten sunulan betik, sitenin kendi adresinden çalışmış olur. Yanıt
`X-Content-Type-Options: nosniff` taşır; tarayıcı türü kendi tahmin etmez.
Üst sınır 512 KB.

Kırpma tarayıcıda yapılır: kişi konumlar, yakınlaştırır; sunucuya yalnızca
256×256'lık sonuç gider. Hem yükleme küçülür hem sunucuda görsel işleme
kütüphanesine gerek kalmaz.

## Sonuçlar

**Kazandıklarımız**
- Kullanıcı kendi bilgilerini yönetebiliyor; şifre değiştirmek çalınmış oturumu da kapatıyor.
- Fotoğraf üye listesi, çevrimiçi göstergesi, geçmiş ve bildirimlerde görünüyor.
- Ek altyapı yok.

**Feda ettiklerimiz / bilinen sınırlar**
- Görseller veritabanını büyütür. Kişi başına tek ve küçük bir görsel için
  sorun değil; ek dosyalar (kart ekleri) gelirse nesne deposuna geçilmeli.
- Sunucu yüklenen görseli yeniden kodlamıyor; imzası doğru ama içi bozuk bir
  dosya kabul edilir ve tarayıcıda görünmez (arayüz baş harflere döner).
- E-posta değiştirme akışı yok.
