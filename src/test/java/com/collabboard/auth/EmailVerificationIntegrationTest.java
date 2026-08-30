package com.collabboard.auth;

import com.collabboard.auth.entity.EmailVerificationToken;
import com.collabboard.support.IntegrationTestBase;
import com.collabboard.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E-posta doğrulama akışı.
 *
 * Kural: kayıt tek başına içeri girmeye yetmez. Hesap pasif açılır, postadaki
 * bağlantıya tıklanana kadar giriş reddedilir.
 */
class EmailVerificationIntegrationTest extends IntegrationTestBase {

    @Autowired
    UserRepository userRepository;

    @Test
    @DisplayName("Kayıt hesabı pasif açar ve doğrulama postası gönderilir")
    void kayitPasifHesapAcar() {
        String email = uniqueEmail("dogrulama");
        mailSender.clear();

        ResponseEntity<JsonNode> registered = register(email);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long userId = registered.getBody().get("id").asLong();

        assertThat(userRepository.findById(userId).orElseThrow().isEnabled()).isFalse();

        EmailVerificationToken token = pendingTokenEntity(userId);
        assertThat(token.isUsed()).isFalse();
        assertThat(token.getExpiresAt()).isAfter(LocalDateTime.now());
        // 32 baytlık rastgelelik, Base64-URL ile 43 karaktere denk gelir.
        assertThat(token.getToken()).hasSize(43);

        awaitMailSent(1);
    }

    @Test
    @DisplayName("Doğrulanmamış hesapla giriş reddedilir ve sebebi açıkça söylenir")
    void dogrulanmamisHesapGiremez() {
        String email = uniqueEmail("dogrulama");
        register(email);

        ResponseEntity<JsonNode> login = login(email);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // "Email veya şifre hatalı" demek kullanıcıyı yanlış yöne iterdi:
        // şifresi doğru, eksik olan doğrulama.
        assertThat(login.getBody().get("message").asText()).contains("doğrulanmadı");
    }

    @Test
    @DisplayName("Bağlantıya tıklandığında hesap etkinleşir ve giriş açılır")
    void dogrulamaSonrasiGirisAcilir() {
        String email = uniqueEmail("dogrulama");
        long userId = register(email).getBody().get("id").asLong();

        ResponseEntity<JsonNode> verified = verify(pendingToken(userId));
        assertThat(verified.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(userRepository.findById(userId).orElseThrow().isEnabled()).isTrue();
        assertThat(login(email).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Aynı bağlantı ikinci kez kullanılamaz")
    void jetonTekKullanimlik() {
        String email = uniqueEmail("dogrulama");
        long userId = register(email).getBody().get("id").asLong();
        String token = pendingToken(userId);

        assertThat(verify(token).getStatusCode()).isEqualTo(HttpStatus.OK);

        // İkinci tıklama: hesap zaten aktif olduğu için kullanıcıyı hataya
        // boğmuyoruz — istediği sonuç zaten gerçekleşmiş durumda.
        ResponseEntity<JsonNode> again = verify(token);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(verificationTokenRepository.findByToken(token).orElseThrow().isUsed()).isTrue();
    }

    @Test
    @DisplayName("Geçersiz jeton reddedilir")
    void gecersizJetonReddedilir() {
        ResponseEntity<JsonNode> response = verify("boyle-bir-jeton-yok");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("message").asText()).contains("geçersiz");
    }

    @Test
    @DisplayName("Süresi dolmuş jetonla hesap açılmaz")
    void suresiDolmusJetonReddedilir() {
        String email = uniqueEmail("dogrulama");
        long userId = register(email).getBody().get("id").asLong();

        EmailVerificationToken token = pendingTokenEntity(userId);
        token.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        verificationTokenRepository.save(token);

        ResponseEntity<JsonNode> response = verify(token.getToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("message").asText()).contains("süresi dolmuş");
        assertThat(userRepository.findById(userId).orElseThrow().isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Yeniden gönderim eski bağlantıyı geçersiz kılar")
    void yenidenGonderimEskisiniGecersizKilar() {
        String email = uniqueEmail("dogrulama");
        long userId = register(email).getBody().get("id").asLong();
        String eskiJeton = pendingToken(userId);

        mailSender.clear();
        ResponseEntity<JsonNode> resend = rest.postForEntity("/api/auth/resend-verification",
                Map.of("email", email), JsonNode.class);
        assertThat(resend.getStatusCode()).isEqualTo(HttpStatus.OK);
        awaitMailSent(1);

        // Aynı anda iki geçerli bağlantı dolaşmamalı: kullanıcı en son gelene güvenir.
        assertThat(verify(eskiJeton).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        String yeniJeton = pendingToken(userId);
        assertThat(yeniJeton).isNotEqualTo(eskiJeton);
        assertThat(verify(yeniJeton).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Kayıtsız adrese yeniden gönderim, adresin varlığını ele vermez")
    void kayitsizAdresYenidenGonderimdeAyniCevap() {
        ResponseEntity<JsonNode> response = rest.postForEntity("/api/auth/resend-verification",
                Map.of("email", uniqueEmail("hicyok")), JsonNode.class);

        // Kayıtlı adresle aynı cevap: aksi hâlde bu uç "bu e-posta sistemde var mı"
        // sorusunu herkese cevaplayan bir hesap sayma aracına dönüşürdü.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("message").asText()).contains("Adres kayıtlıysa");
    }

    // ── yardımcılar ──────────────────────────────────────────────────

    private ResponseEntity<JsonNode> register(String email) {
        return rest.postForEntity("/api/auth/register", Map.of(
                "firstName", "Dogrulama", "lastName", "Testi",
                "email", email, "password", PASSWORD), JsonNode.class);
    }

    private ResponseEntity<JsonNode> login(String email) {
        return rest.postForEntity("/api/auth/login",
                Map.of("email", email, "password", PASSWORD), JsonNode.class);
    }

    private ResponseEntity<JsonNode> verify(String token) {
        return rest.getForEntity("/api/auth/verify?token=" + token, JsonNode.class);
    }

    private EmailVerificationToken pendingTokenEntity(long userId) {
        return verificationTokenRepository.findByToken(pendingToken(userId)).orElseThrow();
    }

    /** Gönderim @Async olduğu için kısa süre bekleyip kontrol eder. */
    private void awaitMailSent(int expected) {
        for (int i = 0; i < 50 && mailSender.sent().size() < expected; i++) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        assertThat(mailSender.sent()).hasSize(expected);
    }
}
