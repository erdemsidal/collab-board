package com.collabboard.auth;

import com.collabboard.auth.entity.PasswordResetToken;
import com.collabboard.support.IntegrationTestBase;
import com.collabboard.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Şifre sıfırlama akışı.
 *
 * Ham jeton veritabanında YOK (yalnızca özeti var), bu yüzden testler onu
 * gönderilen postanın içinden okuyor — tıpkı kullanıcının yapacağı gibi. Bu
 * aynı zamanda postadaki bağlantının gerçekten çalıştığını da sınıyor.
 */
class PasswordResetIntegrationTest extends IntegrationTestBase {

    private static final Pattern RESET_LINK = Pattern.compile("reset=([A-Za-z0-9_-]+)");
    private static final String NEW_PASSWORD = "yeniParola987";

    @Autowired
    PasswordResetTokenRepository resetTokenRepository;

    @Autowired
    UserRepository userRepository;

    @BeforeEach
    void temizle() {
        mailSender.clear();
    }

    @Test
    @DisplayName("Postadaki bağlantıyla yeni şifre belirlenir; eskisi artık çalışmaz")
    void yeniSifreBelirlenir() throws Exception {
        String email = uniqueEmail("sifirla");
        registerAndLogin("Unutkan", "Kullanici", email);

        String token = requestResetAndReadToken(email);

        ResponseEntity<JsonNode> reset = reset(token, NEW_PASSWORD);
        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(login(email, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login(email, NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Sıfırlama, açık oturumların hepsini kapatır")
    void tumOturumlarKapanir() throws Exception {
        String email = uniqueEmail("sifirla");
        registerAndLogin("Calinan", "Hesap", email);

        // Şifreyi çalan birinin başka bir cihazda açık duran oturumu.
        String acikOturum = login(email, PASSWORD).getBody().get("refreshToken").asText();

        reset(requestResetAndReadToken(email), NEW_PASSWORD);

        // Yalnızca şifreyi değiştirmek yetmezdi: elindeki refresh token 7 gün
        // boyunca yeni erişim jetonu üretmeye devam ederdi.
        assertThat(refresh(acikOturum).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Aynı bağlantı ikinci kez kullanılamaz")
    void jetonTekKullanimlik() throws Exception {
        String email = uniqueEmail("sifirla");
        registerAndLogin("Tek", "Kullanim", email);
        String token = requestResetAndReadToken(email);

        assertThat(reset(token, NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> again = reset(token, "baskaParola555");
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(again.getBody().get("message").asText()).contains("daha önce kullanılmış");
        // İkinci deneme şifreyi değiştirmemiş olmalı.
        assertThat(login(email, NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Süresi dolmuş bağlantı reddedilir")
    void suresiDolmusBaglantiReddedilir() throws Exception {
        String email = uniqueEmail("sifirla");
        registerAndLogin("Gec", "Kalan", email);
        String token = requestResetAndReadToken(email);

        PasswordResetToken stored = resetTokenRepository
                .findByTokenHash(PasswordResetService.hash(token)).orElseThrow();
        stored.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        resetTokenRepository.save(stored);

        ResponseEntity<JsonNode> response = reset(token, NEW_PASSWORD);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("message").asText()).contains("süresi dolmuş");
        assertThat(login(email, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Veritabanında ham jeton değil, yalnızca özeti durur")
    void hamJetonSaklanmaz() throws Exception {
        String email = uniqueEmail("sifirla");
        registerAndLogin("Ozet", "Kontrol", email);
        String token = requestResetAndReadToken(email);

        // Tablonun tamamında ham jeton geçmemeli; yalnızca SHA-256 özeti bulunmalı.
        assertThat(resetTokenRepository.findAll())
                .extracting(PasswordResetToken::getTokenHash)
                .doesNotContain(token)
                .contains(PasswordResetService.hash(token));
    }

    @Test
    @DisplayName("Yeni istek önceki bağlantıyı geçersiz kılar")
    void yeniIstekEskisiniGecersizKilar() throws Exception {
        String email = uniqueEmail("sifirla");
        registerAndLogin("Iki", "Istek", email);
        String eski = requestResetAndReadToken(email);
        mailSender.clear();
        String yeni = requestResetAndReadToken(email);

        assertThat(reset(eski, NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reset(yeni, NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Kayıtsız adres aynı cevabı alır ve posta gitmez")
    void kayitsizAdresEleVerilmez() throws Exception {
        ResponseEntity<JsonNode> response = rest.postForEntity("/api/auth/forgot-password",
                Map.of("email", uniqueEmail("hicyok")), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("message").asText()).contains("Adres kayıtlıysa");

        Thread.sleep(300);   // gönderim asenkron; bir şey gidecek olsaydı gitmiş olurdu
        assertThat(mailSender.sent()).isEmpty();
    }

    @Test
    @DisplayName("Doğrulanmamış hesap, sıfırlamayla etkinleşir")
    void dogrulanmamisHesapEtkinlesir() throws Exception {
        String email = uniqueEmail("sifirla");
        long userId = rest.postForEntity("/api/auth/register", Map.of(
                "firstName", "Hic", "lastName", "Dogrulamadi",
                "email", email, "password", PASSWORD), JsonNode.class).getBody().get("id").asLong();
        awaitMail();   // doğrulama postası
        mailSender.clear();

        reset(requestResetAndReadToken(email), NEW_PASSWORD);

        // Sıfırlama postasını açabilmek, adresin sahibi olmanın kanıtı.
        assertThat(userRepository.findById(userId).orElseThrow().isEnabled()).isTrue();
        assertThat(login(email, NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Kısa şifre reddedilir; sıfırlama, kayıt kuralının arka kapısı değil")
    void kisaSifreReddedilir() throws Exception {
        String email = uniqueEmail("sifirla");
        registerAndLogin("Kisa", "Sifre", email);

        ResponseEntity<JsonNode> response = reset(requestResetAndReadToken(email), "kisa");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(login(email, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── yardımcılar ──────────────────────────────────────────────────

    /**
     * Sıfırlama ister, gelen postadaki bağlantıdan jetonu çıkarır.
     *
     * "Son gelen posta" yetmez: kayıt sırasında giden doğrulama postası da
     * asenkron ve geç düşebilir. İçinde sıfırlama bağlantısı olan postayı bekliyoruz.
     */
    private String requestResetAndReadToken(String email) throws Exception {
        int before = mailSender.sent().size();
        rest.postForEntity("/api/auth/forgot-password", Map.of("email", email), JsonNode.class);

        for (int i = 0; i < 100; i++) {
            var sent = mailSender.sent();
            for (int j = sent.size() - 1; j >= before; j--) {
                Matcher m = RESET_LINK.matcher(textOf(sent.get(j)));
                if (m.find()) {
                    return m.group(1);
                }
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Sıfırlama bağlantısı içeren posta gelmedi");
    }

    private MimeMessage awaitMail() throws InterruptedException {
        for (int i = 0; i < 100 && mailSender.sent().isEmpty(); i++) {
            Thread.sleep(20);
        }
        assertThat(mailSender.sent()).as("posta gönderilmiş olmalı").isNotEmpty();
        return mailSender.sent().get(mailSender.sent().size() - 1);
    }

    /**
     * Postanın düz metin gövdesi.
     *
     * Ham MIME'a regex uygulamak yanlış sonuç verirdi: gövde quoted-printable
     * kodlanır, "=" işareti "=3D" olur ve uzun satırlar bölünür. Parçaları
     * çözerek okumak gerekiyor.
     */
    private String textOf(Part part) throws Exception {
        // Önce içeriğin kendisine bakılıyor, başlığa değil: sahte gönderici
        // saveChanges() çağırmadığı için dış başlık henüz yazılmamış olur ve
        // varsayılan "text/plain" der — içerik aslında çok parçalıyken.
        Object content = part.getContent();
        if (content instanceof String text) {
            return part.isMimeType("text/plain") ? text : "";
        }
        if (content instanceof Multipart multipart) {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart child = multipart.getBodyPart(i);
                out.append(textOf(child));
            }
            return out.toString();
        }
        return "";
    }

    private ResponseEntity<JsonNode> reset(String token, String newPassword) {
        return rest.postForEntity("/api/auth/reset-password",
                Map.of("token", token, "newPassword", newPassword), JsonNode.class);
    }

    private ResponseEntity<JsonNode> login(String email, String password) {
        return rest.postForEntity("/api/auth/login",
                Map.of("email", email, "password", password), JsonNode.class);
    }

    private ResponseEntity<JsonNode> refresh(String refreshToken) {
        return rest.postForEntity("/api/auth/refresh",
                Map.of("refreshToken", refreshToken), JsonNode.class);
    }
}
