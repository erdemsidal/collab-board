package com.collabboard.auth.oauth;

import com.collabboard.auth.AuthService;
import com.collabboard.support.IntegrationTestBase;
import com.collabboard.user.UserIdentityRepository;
import com.collabboard.user.UserRepository;
import com.collabboard.user.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * "Google ile devam et"in sağlayıcıdan bağımsız tarafı: hesabı bulma, bağlama,
 * açma; tek kullanımlık kodun oturuma çevrilmesi; şifresiz hesaplar.
 *
 * Google'a gerçekten gidilmiyor. Hesap servisi, Google'ın kimlik jetonunda
 * gelecek bilgilerle (sahte "claims") doğrudan çağrılıyor; Google'ın kendisini
 * Spring Security doğruluyor ve o kısım Spring'in kendi testlerinde.
 *
 * Bu sınıf varsayılan bağlamda çalışır (Google ayarlanmamış): hesap tarafı
 * ayardan bağımsız. Ayarlanmış hâl GoogleLoginIntegrationTest'te.
 */
class OAuthAccountIntegrationTest extends IntegrationTestBase {

    private static final Pattern RESET_LINK = Pattern.compile("reset=([A-Za-z0-9_-]+)");
    private static final String BROWSER = "tarayici-bagi-" + UUID.randomUUID();

    @Autowired
    OAuthAccountService accountService;

    @Autowired
    GoogleOAuthProvider google;

    @Autowired
    OAuthLoginCodes loginCodes;

    @Autowired
    UserRepository userRepository;

    @Autowired
    UserIdentityRepository identityRepository;

    @Autowired
    AuthService authService;

    @Autowired
    StringRedisTemplate redis;

    // ── Sağlayıcılar ─────────────────────────────────────────

    @Test
    @DisplayName("Google ayarlanmamışsa sağlayıcı listesi boş, akış adresi yok")
    void ayarYoksaListeBos() throws Exception {
        ResponseEntity<JsonNode> providers = rest.getForEntity("/api/auth/providers", JsonNode.class);

        assertThat(providers.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(providers.getBody().get("providers").isArray()).isTrue();
        assertThat(providers.getBody().get("providers")).isEmpty();
        assertThat(rest.getForEntity("/oauth2/authorization/google", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── Hesap bulma, bağlama, açma ───────────────────────────

    @Test
    @DisplayName("Yeni kişi: hesap etkin ve şifresiz açılır, bağ kaydedilir, karşılama postası gider")
    void yeniHesapAcilir() throws Exception {
        String email = uniqueEmail("google-yeni");
        String sub = newSubject();

        User user = signIn(sub, email, true, "Ada", "Lovelace", "Ada Lovelace");

        User saved = userRepository.findById(user.getId()).orElseThrow();
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getPassword()).isNull();
        assertThat(saved.getFirstName()).isEqualTo("Ada");
        assertThat(saved.getLastName()).isEqualTo("Lovelace");
        assertThat(identityRepository.findByProviderAndSubject("google", sub))
                .hasValueSatisfying(identity -> assertThat(identity.getUserId()).isEqualTo(user.getId()));

        MimeMessage welcome = awaitMailTo(email);
        assertThat(welcome.getSubject()).isEqualTo("CollabBoard hesabın açıldı");
        String text = textOf(welcome);
        assertThat(text).contains("Merhaba Ada", "Google hesabınla", "Şifremi unuttum");
        assertThat(text).doesNotContain("—");
    }

    @Test
    @DisplayName("Aynı Google hesabı ikinci kez: aynı hesap, ikinci posta yok")
    void ayniHesapTekrar() throws Exception {
        String email = uniqueEmail("google-tekrar");
        String sub = newSubject();
        User first = signIn(sub, email, true, "Tekrar", "Gelen", null);
        awaitMailTo(email);

        User second = signIn(sub, email, true, "Tekrar", "Gelen", null);

        assertThat(second.getId()).isEqualTo(first.getId());
        Thread.sleep(300);   // posta asenkron; ikincisi gidecek olsaydı bu sürede giderdi
        assertThat(mailsTo(email)).hasSize(1);
    }

    @Test
    @DisplayName("Şifreli hesabın adresiyle gelen Google hesabı ona bağlanır; güvenlik postası gider, şifre çalışmaya devam eder")
    void varOlanHesabaBaglanir() throws Exception {
        String email = uniqueEmail("google-bagla");
        registerAndLogin("Sifreli", "Kullanici", email);
        long userId = userRepository.findByEmail(email).orElseThrow().getId();

        // Büyük/küçük harf farkı aynı adres sayılır.
        User linked = signIn(newSubject(), email.toUpperCase(Locale.ROOT), true, "Baska", "Ad", null);

        assertThat(linked.getId()).isEqualTo(userId);
        assertThat(userRepository.findById(userId).orElseThrow().getFirstName()).isEqualTo("Sifreli");
        MimeMessage security = awaitMailTo(email);
        assertThat(security.getSubject()).isEqualTo("CollabBoard hesabına Google ile giriş bağlandı");
        String text = textOf(security);
        assertThat(text).contains("Merhaba Sifreli", "Türkiye saati", "Bunu sen yapmadıysan", "Şifremi unuttum");
        assertThat(text).doesNotContain("—");

        assertThat(login(email, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Doğrulanmamış hesap Google ile doğrulanır; kayıtta konan şifre kaldırılır")
    void dogrulanmamisHesapEtkinlesir() {
        String email = uniqueEmail("google-dogrula");
        // Kayıt olmuş ama postadaki bağlantıya hiç tıklamamış.
        rest.postForEntity("/api/auth/register", Map.of("firstName", "Bekleyen", "lastName", "Hesap",
                "email", email, "password", PASSWORD), JsonNode.class);
        assertThat(login(email, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        User linked = signIn(newSubject(), email, true, "Bekleyen", "Hesap", null);

        User saved = userRepository.findById(linked.getId()).orElseThrow();
        assertThat(saved.isEnabled()).isTrue();
        // Şifreyi koyan kişi adresin sahibi olduğunu hiç kanıtlamadı: başkası olabilir.
        assertThat(saved.getPassword()).isNull();
        assertThat(login(email, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Google adresi doğrulamamışsa hesap açılmaz da bağlanmaz da")
    void dogrulanmamisAdresReddedilir() {
        String email = uniqueEmail("google-dogrulanmamis");

        assertThatThrownBy(() -> signIn(newSubject(), email, false, "Sahte", "Adres", null))
                .isInstanceOf(OAuthLoginException.class)
                .satisfies(e -> assertThat(((OAuthLoginException) e).error()).isEqualTo(OAuthLoginError.EMAIL_UNVERIFIED));
        assertThat(userRepository.findByEmailIgnoreCase(email)).isEmpty();
    }

    @Test
    @DisplayName("Ad gelmezse tam ad bölünür, o da yoksa adresin @ öncesi kullanılır")
    void adYedekleri() {
        assertThat(OAuthAccountService.names(profile(null, null, "Mehmet Ali Yılmaz"), "x@y.com"))
                .containsExactly("Mehmet Ali", "Yılmaz");
        assertThat(OAuthAccountService.names(profile(null, null, null), "erdem.sidal@y.com"))
                .containsExactly("erdem.sidal", "");
        assertThat(OAuthAccountService.names(profile("Ada", null, "Ada Lovelace"), "x@y.com"))
                .containsExactly("Ada", "Lovelace");
        assertThat(OAuthAccountService.names(profile("Ada", "King", "Ada Lovelace"), "x@y.com"))
                .containsExactly("Ada", "King");
    }

    @Test
    @DisplayName("Bu adrese gönderilmiş bekleyen pano daveti, Google ile açılan hesapta görünür ve kabul edilir")
    void bekleyenDavetGorunur() {
        String ownerToken = registerAndLogin("Pano", "Sahibi", uniqueEmail("google-sahip"));
        long boardId = createBoard(ownerToken, "Davetli pano").get("id").asLong();
        String email = uniqueEmail("google-davetli");
        assertThat(invite(boardId, ownerToken, email, "EDITOR").getStatusCode()).isEqualTo(HttpStatus.CREATED);

        User user = signIn(newSubject(), email, true, "Davetli", "Kisi", null);
        String token = authService.issueTokens(user).accessToken();

        JsonNode inbox = rest.exchange("/api/notifications", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), JsonNode.class).getBody();
        JsonNode invitation = null;
        for (JsonNode item : inbox.get("items")) {
            if ("BOARD_INVITE".equals(item.get("type").asText()) && item.get("boardId").asLong() == boardId) {
                invitation = item;
            }
        }
        assertThat(invitation).as("davet bildirimi").isNotNull();

        ResponseEntity<JsonNode> accepted = rest.exchange(
                "/api/invitations/" + invitation.get("invitationId").asLong() + "/accept", HttpMethod.POST,
                new HttpEntity<>(authHeaders(token)), JsonNode.class);
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getBoard(boardId, token).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── Tek kullanımlık kod ──────────────────────────────────

    @Test
    @DisplayName("Geçerli kod giriş yanıtının aynısını döner; ikinci kullanımda 400")
    void kodOturumaCevrilir() {
        String email = uniqueEmail("google-kod");
        User user = signIn(newSubject(), email, true, "Kod", "Sahibi", null);
        String code = loginCodes.issue(user.getId(), OAuthBrowserBinding.fingerprint(BROWSER));

        ResponseEntity<JsonNode> response = exchange(code, BROWSER);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body.get("accessToken").asText()).isNotBlank();
        assertThat(body.get("refreshToken").asText()).isNotBlank();
        assertThat(body.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(body.get("user").get("email").asText()).isEqualTo(email);
        assertThat(body.get("user").get("hasPassword").asBoolean()).isFalse();
        assertThat(body.get("user").get("providers")).hasSize(1);
        assertThat(body.get("user").get("providers").get(0).asText()).isEqualTo("google");
        // Akış bitti; tarayıcı bağı çerezi silinir.
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
                .anyMatch(c -> c.startsWith("cb_oauth=;") && c.contains("Max-Age=0"));

        JsonNode me = rest.exchange("/api/users/me", HttpMethod.GET,
                new HttpEntity<>(authHeaders(body.get("accessToken").asText())), JsonNode.class).getBody();
        assertThat(me.get("hasPassword").asBoolean()).isFalse();
        assertThat(me.get("providers").get(0).asText()).isEqualTo("google");

        ResponseEntity<JsonNode> again = exchange(code, BROWSER);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(again.getBody().get("message").asText()).contains("Giriş kodu geçersiz");
        assertThat(again.getBody().get("path").asText()).isEqualTo("/api/auth/oauth/exchange");
    }

    @Test
    @DisplayName("Süresi dolmuş kod 400; kod en fazla 60 saniye yaşar")
    void suresiDolmusKod() throws Exception {
        User user = signIn(newSubject(), uniqueEmail("google-sure"), true, "Sure", "Dolan", null);
        String code = loginCodes.issue(user.getId(), OAuthBrowserBinding.fingerprint(BROWSER));
        String key = OAuthLoginCodes.KEY_PREFIX + code;

        Long ttl = redis.getExpire(key);
        assertThat(ttl).isBetween(1L, 60L);

        // 60 saniye beklemek yerine Redis'e süreyi şimdi doldurtuyoruz.
        redis.expire(key, Duration.ofMillis(1));
        for (int i = 0; i < 50 && Boolean.TRUE.equals(redis.hasKey(key)); i++) {
            Thread.sleep(10);
        }

        assertThat(exchange(code, BROWSER).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Bilinmeyen kod ve başka tarayıcıdan gelen kod 400; başka tarayıcıdan denenen kod harcanır")
    void bilinmeyenVeYabanciKod() {
        assertThat(exchange("boyle-bir-kod-yok", BROWSER).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        User user = signIn(newSubject(), uniqueEmail("google-yabanci"), true, "Yabanci", "Tarayici", null);
        String code = loginCodes.issue(user.getId(), OAuthBrowserBinding.fingerprint(BROWSER));

        assertThat(exchange(code, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exchange(code, BROWSER).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Şifresiz hesap ───────────────────────────────────────

    @Test
    @DisplayName("Şifresiz hesapla şifreyle giriş, yanlış şifreyle tıpatıp aynı biçimde reddedilir")
    void sifresizHesapSifreyleGiremez() {
        String passwordless = uniqueEmail("google-sifresiz");
        signIn(newSubject(), passwordless, true, "Sifresiz", "Hesap", null);
        String withPassword = uniqueEmail("google-sifreli");
        registerAndLogin("Sifreli", "Hesap", withPassword);

        ResponseEntity<JsonNode> noPassword = login(passwordless, "herhangi-bir-sifre");
        ResponseEntity<JsonNode> wrongPassword = login(withPassword, "yanlis-sifre-123");

        assertThat(noPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(noPassword.getStatusCode()).isEqualTo(wrongPassword.getStatusCode());
        assertThat(noPassword.getBody().get("message").asText())
                .isEqualTo(wrongPassword.getBody().get("message").asText());
    }

    @Test
    @DisplayName("Şifresiz hesap, mevcut şifre sormadan şifre belirler ve onunla girer")
    void sifresizHesapSifreBelirler() {
        String email = uniqueEmail("google-sifre-belirle");
        User user = signIn(newSubject(), email, true, "Ilk", "Sifre", null);
        String token = authService.issueTokens(user).accessToken();

        ResponseEntity<JsonNode> set = rest.exchange("/api/users/me/password", HttpMethod.POST,
                new HttpEntity<>(Map.of("newPassword", "ilkSifrem123"), authHeaders(token)), JsonNode.class);

        assertThat(set.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(set.getBody().get("accessToken").asText()).isNotBlank();
        assertThat(set.getBody().get("refreshToken").asText()).isNotBlank();
        assertThat(login(email, "ilkSifrem123").getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode me = rest.exchange("/api/users/me", HttpMethod.GET,
                new HttpEntity<>(authHeaders(set.getBody().get("accessToken").asText())), JsonNode.class).getBody();
        assertThat(me.get("hasPassword").asBoolean()).isTrue();
        assertThat(me.get("providers").get(0).asText()).isEqualTo("google");
    }

    @Test
    @DisplayName("Şifresi olan hesap mevcut şifreyi vermeden şifre değiştiremez")
    void sifreliHesapMevcutSifreVermeli() {
        String email = uniqueEmail("google-mevcut");
        String token = registerAndLogin("Mevcut", "Sifreli", email);

        ResponseEntity<JsonNode> response = rest.exchange("/api/users/me/password", HttpMethod.POST,
                new HttpEntity<>(Map.of("newPassword", "yepyeniSifre42"), authHeaders(token)), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("message").asText()).isEqualTo("Mevcut şifre boş olamaz");
        assertThat(login(email, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login(email, "yepyeniSifre42").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Şifresiz hesap \"Şifremi unuttum\" postasıyla da şifre belirleyebilir")
    void sifresizHesapSifirlamaylaSifreBelirler() throws Exception {
        String email = uniqueEmail("google-unuttum");
        signIn(newSubject(), email, true, "Unutan", "Kisi", null);
        awaitMailTo(email);   // karşılama postası

        rest.postForEntity("/api/auth/forgot-password", Map.of("email", email), JsonNode.class);
        String token = awaitResetToken(email);
        ResponseEntity<JsonNode> reset = rest.postForEntity("/api/auth/reset-password",
                Map.of("token", token, "newPassword", "sifirlanmis123"), JsonNode.class);

        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login(email, "sifirlanmis123").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── Yardımcılar ──────────────────────────────────────────

    /** Google'dan dönen bilgilerle hesap servisini çağırır. */
    private User signIn(String sub, String email, boolean verified, String given, String family, String name) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", sub);
        claims.put("email", email);
        claims.put("email_verified", verified);
        if (given != null) {
            claims.put("given_name", given);
        }
        if (family != null) {
            claims.put("family_name", family);
        }
        if (name != null) {
            claims.put("name", name);
        }
        return accountService.resolve(google.profile(claims));
    }

    private static OAuthProfile profile(String given, String family, String full) {
        return new OAuthProfile("google", "1", "x@y.com", true, given, family, full);
    }

    /** Google'ın "sub" değerine benzeyen, testler arasında çakışmayan bir kimlik. */
    private static String newSubject() {
        return String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits()));
    }

    private ResponseEntity<JsonNode> exchange(String code, String browserCookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (browserCookie != null) {
            headers.add(HttpHeaders.COOKIE, OAuthBrowserBinding.COOKIE + "=" + browserCookie);
        }
        return rest.exchange("/api/auth/oauth/exchange", HttpMethod.POST,
                new HttpEntity<>(Map.of("code", code), headers), JsonNode.class);
    }

    private ResponseEntity<JsonNode> login(String email, String password) {
        return rest.postForEntity("/api/auth/login", Map.of("email", email, "password", password), JsonNode.class);
    }

    private List<MimeMessage> mailsTo(String email) {
        return mailSender.sent().stream().filter(m -> {
            try {
                return Arrays.stream(m.getAllRecipients())
                        .anyMatch(a -> ((InternetAddress) a).getAddress().equalsIgnoreCase(email));
            } catch (Exception e) {
                return false;
            }
        }).toList();
    }

    private MimeMessage awaitMailTo(String email) throws InterruptedException {
        for (int i = 0; i < 250 && mailsTo(email).isEmpty(); i++) {
            Thread.sleep(20);
        }
        List<MimeMessage> mails = mailsTo(email);
        assertThat(mails).as("%s adresine posta gitmeli", email).isNotEmpty();
        return mails.get(mails.size() - 1);
    }

    private String awaitResetToken(String email) throws Exception {
        for (int i = 0; i < 250; i++) {
            for (MimeMessage mail : mailsTo(email)) {
                Matcher matcher = RESET_LINK.matcher(textOf(mail));
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Sıfırlama bağlantısı içeren posta gelmedi");
    }

    /** Postanın düz metin gövdesi (bkz. PasswordResetIntegrationTest.textOf). */
    private static String textOf(Part part) throws Exception {
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
}
