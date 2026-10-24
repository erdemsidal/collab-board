package com.collabboard.auth.oauth;

import com.collabboard.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Google ayarlanmışken akışın iki ayağı: Google'a gidiş ve dönüş.
 *
 * Kimlik bilgileri sahte; Google'a gerçekten gidilmiyor. Gidişte üretilen
 * yönlendirme adresi sınanıyor (Google'ın göreceği her şey orada). Dönüşte
 * Google'ın jeton ucunu çağırmak gerektiği için başarılı dönüş, Spring'in
 * kimlik jetonunu doğruladıktan sonra çağırdığı başarı işleyicisinden itibaren
 * sınanıyor; başarısız dönüşler ise gerçek HTTP isteğiyle.
 *
 * AYRI BAĞLAM: bu sınıfın ayarları farklı olduğu için Spring ayrı bir uygulama
 * bağlamı kurar. Google'ın açık olmasını gerektiren her şey bu sınıfta, tek
 * bağlamda toplu duruyor; geri kalanı OAuthAccountIntegrationTest'te.
 *
 * Genel adres bilerek testin kendi adresinden farklı: dönüş adresinin istekten
 * değil app.base-url'den kurulduğu böylece görünür.
 */
@TestPropertySource(properties = {
        "app.oauth.google.client-id=" + GoogleLoginIntegrationTest.CLIENT_ID,
        "app.oauth.google.client-secret=sahte-gizli-anahtar",
        "app.base-url=" + GoogleLoginIntegrationTest.BASE_URL
})
class GoogleLoginIntegrationTest extends IntegrationTestBase {

    static final String CLIENT_ID = "test-istemci.apps.googleusercontent.com";
    static final String BASE_URL = "https://collab.example.test";
    static final String REDIRECT_URI = BASE_URL + "/login/oauth2/code/google";

    /** Yönlendirmeleri İZLEMEYEN istemci: yönlendirmenin kendisini görmek istiyoruz. */
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @Autowired
    OAuthLoginSuccessHandler successHandler;

    @Test
    @DisplayName("Google ayarlıysa sağlayıcı listesinde görünür")
    void saglayiciListesi() {
        JsonNode body = rest.getForEntity("/api/auth/providers", JsonNode.class).getBody();

        assertThat(body.get("providers")).hasSize(1);
        assertThat(body.get("providers").get(0).get("id").asText()).isEqualTo("google");
        assertThat(body.get("providers").get(0).get("name").asText()).isEqualTo("Google");
    }

    @Test
    @DisplayName("Akış Google'a doğru adresle yönlenir; oturum çerezi doğmaz")
    void googleaYonlenir() throws Exception {
        HttpResponse<String> response = get("/oauth2/authorization/google", null);

        assertThat(response.statusCode()).isEqualTo(302);
        URI location = URI.create(response.headers().firstValue("Location").orElseThrow());
        assertThat(location.getScheme()).isEqualTo("https");
        assertThat(location.getHost()).isEqualTo("accounts.google.com");

        Map<String, String> query = query(location);
        assertThat(query.get("response_type")).isEqualTo("code");
        assertThat(query.get("client_id")).isEqualTo(CLIENT_ID);
        assertThat(query.get("redirect_uri")).isEqualTo(REDIRECT_URI);
        assertThat(query.get("scope").split(" ")).contains("openid", "email", "profile");
        assertThat(query.get("state")).isNotBlank();
        assertThat(query.get("nonce")).isNotBlank();
        assertThat(query.get("code_challenge")).isNotBlank();
        assertThat(query.get("code_challenge_method")).isEqualTo("S256");

        List<String> cookies = response.headers().allValues("Set-Cookie");
        assertThat(cookies).noneMatch(c -> c.startsWith("JSESSIONID"));
        assertThat(cookies).anyMatch(c -> c.startsWith(OAuthBrowserBinding.COOKIE + "=")
                && c.contains("HttpOnly") && c.contains("SameSite=Lax") && c.contains("Secure"));
    }

    @Test
    @DisplayName("Kişi izin ekranında vazgeçerse ana sayfaya oauth_error=cancelled ile döner")
    void vazgecilirse() throws Exception {
        Flow flow = start();

        HttpResponse<String> back = get("/login/oauth2/code/google?error=access_denied&state=" + flow.state(),
                flow.cookie());

        assertThat(back.statusCode()).isEqualTo(302);
        assertThat(back.headers().firstValue("Location")).hasValue(BASE_URL + "/?oauth_error=cancelled");
        assertThat(back.headers().allValues("Set-Cookie")).noneMatch(c -> c.startsWith("JSESSIONID"));
    }

    @Test
    @DisplayName("Dönüş akışı başlatan tarayıcıdan gelmezse reddedilir; akış da harcanır")
    void baskaTarayicidanDonus() throws Exception {
        Flow flow = start();
        String callback = "/login/oauth2/code/google?code=sahte-kod&state=" + flow.state();

        HttpResponse<String> foreign = get(callback, null);
        assertThat(foreign.headers().firstValue("Location")).hasValue(BASE_URL + "/?oauth_error=failed");

        // Akış tek kullanımlık: doğru tarayıcı aynı dönüşü sonradan getirse de bulunamaz.
        HttpResponse<String> replay = get(callback, flow.cookie());
        assertThat(replay.headers().firstValue("Location")).hasValue(BASE_URL + "/?oauth_error=failed");
    }

    @Test
    @DisplayName("Başarılı dönüş tek kullanımlık kodla ana sayfaya yönlenir; kod aynı tarayıcıda oturuma çevrilir")
    void basariliDonus() throws Exception {
        String email = uniqueEmail("google-donus");
        String browser = "tarayici-" + UUID.randomUUID();

        MockHttpServletResponse redirect = completeLogin(email, true, browser);

        String location = redirect.getRedirectedUrl();
        assertThat(location).startsWith(BASE_URL + "/?oauth=");
        assertThat(redirect.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        String code = location.substring(location.indexOf("oauth=") + "oauth=".length());
        assertThat(code).hasSizeGreaterThanOrEqualTo(43);   // 256 bit, Base64-URL

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, OAuthBrowserBinding.COOKIE + "=" + browser);
        ResponseEntity<JsonNode> tokens = rest.exchange("/api/auth/oauth/exchange", HttpMethod.POST,
                new HttpEntity<>(Map.of("code", code), headers), JsonNode.class);

        assertThat(tokens.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tokens.getBody().get("user").get("email").asText()).isEqualTo(email);
        assertThat(tokens.getBody().get("user").get("hasPassword").asBoolean()).isFalse();
        // Jeton adreste değil, yalnızca bu yanıtın gövdesinde.
        assertThat(location).doesNotContain(tokens.getBody().get("accessToken").asText());
    }

    @Test
    @DisplayName("Google adresi doğrulamamışsa ana sayfaya oauth_error=email_unverified ile döner")
    void dogrulanmamisAdres() throws Exception {
        MockHttpServletResponse redirect = completeLogin(uniqueEmail("google-dogrulanmamis"), false, "tarayici");

        assertThat(redirect.getRedirectedUrl()).isEqualTo(BASE_URL + "/?oauth_error=email_unverified");
    }

    // ── Yardımcılar ──────────────────────────────────────────

    private record Flow(String state, String cookie) {
    }

    /** Akışı başlatır: state ve tarayıcı bağı çerezi. */
    private Flow start() throws Exception {
        HttpResponse<String> response = get("/oauth2/authorization/google", null);
        String state = query(URI.create(response.headers().firstValue("Location").orElseThrow())).get("state");
        String cookie = response.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith(OAuthBrowserBinding.COOKIE + "="))
                .map(c -> c.substring(0, c.indexOf(';')))
                .findFirst().orElseThrow();
        return new Flow(state, cookie);
    }

    /**
     * Spring kimlik jetonunu doğruladıktan sonraki adım: başarı işleyicisi,
     * Google'ın kimlik jetonundaki bilgilerle çağrılır.
     */
    private MockHttpServletResponse completeLogin(String email, boolean verified, String browser) throws Exception {
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", "https://accounts.google.com");
        claims.put("sub", String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits())));
        claims.put("email", email);
        claims.put("email_verified", verified);
        claims.put("given_name", "Donen");
        claims.put("family_name", "Kisi");
        OidcIdToken idToken = new OidcIdToken("kimlik-jetonu", Instant.now(), Instant.now().plusSeconds(60), claims);
        DefaultOidcUser principal = new DefaultOidcUser(List.of(new OidcUserAuthority(idToken)), idToken);
        OAuth2AuthenticationToken authentication =
                new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/google");
        request.setCookies(new Cookie(OAuthBrowserBinding.COOKIE, browser));
        MockHttpServletResponse response = new MockHttpServletResponse();
        successHandler.onAuthenticationSuccess(request, response, authentication);
        return response;
    }

    private HttpResponse<String> get(String path, String cookie) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static Map<String, String> query(URI uri) {
        Map<String, String> params = new HashMap<>();
        Arrays.stream(uri.getRawQuery().split("&")).forEach(pair -> {
            int eq = pair.indexOf('=');
            params.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        });
        return params;
    }
}
