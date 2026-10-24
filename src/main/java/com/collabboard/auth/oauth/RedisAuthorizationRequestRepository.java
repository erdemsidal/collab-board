package com.collabboard.auth.oauth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Google'a gönderilip dönüşü beklenen akışı Redis'te tutar, HttpSession'da değil.
 *
 * Uygulama oturumsuz (JWT) ve birden çok sunucuda çalışabiliyor (ADR 0004).
 * Spring'in varsayılanı akışı HttpSession'a koyar: kişi Google'dan döndüğünde
 * isteği başka bir sunucu karşılarsa akış bulunamaz, ayrıca her girişte bir
 * oturum çerezi doğar. Burada akış, Google'ın geri getirdiği "state" değeriyle
 * Redis'e yazılır; dönüşü hangi sunucu karşılarsa karşılasın bulur.
 *
 * GÜVENLİK
 *  - Saklanan şey bizim yazdığımız sabit biçimli bir JSON'dur ve yalnızca bu
 *    biçime çözülür. Tarayıcıdan gelen hiçbir şey çözümlenmez: state yalnızca
 *    anahtar olarak kullanılır, çerez yalnızca karşılaştırılır.
 *  - Akış tek kullanımlıktır: dönüşte kayıt okunurken silinir (GETDEL). Aynı
 *    dönüş adresi ikinci kez açılırsa akış bulunamaz.
 *  - Akış onu başlatan tarayıcıya bağlıdır (OAuthBrowserBinding).
 *  - 10 dakika içinde dönülmezse kayıt kendiliğinden silinir.
 */
@Component
public class RedisAuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final Logger log = LoggerFactory.getLogger(RedisAuthorizationRequestRepository.class);

    static final String KEY_PREFIX = "oauth2_auth_request:";
    static final Duration TTL = Duration.ofMinutes(10);

    /** Spring'in ürettiği state 43 karakter; daha uzunu bizden çıkmamıştır. */
    private static final int MAX_STATE_LENGTH = 128;

    /** Yalnızca StoredRequest'i okuyup yazar; tür bilgisi taşımaz, sınıf adı çözmez. */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final StringRedisTemplate redis;
    private final OAuthBrowserBinding binding;

    public RedisAuthorizationRequestRepository(StringRedisTemplate redis, OAuthBrowserBinding binding) {
        this.redis = redis;
        this.binding = binding;
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            removeAuthorizationRequest(request, response);
            return;
        }
        String fingerprint = binding.issue(response);
        try {
            redis.opsForValue().set(KEY_PREFIX + authorizationRequest.getState(),
                    JSON.writeValueAsString(StoredRequest.of(authorizationRequest, fingerprint)), TTL);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Yetkilendirme isteği saklanamadı", e);
        }
    }

    /** Akışa bakar, silmez. Spring giriş akışında bunu değil remove'u kullanır. */
    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String state = state(request);
        return state == null ? null : restore(redis.opsForValue().get(KEY_PREFIX + state), request);
    }

    /** Google'dan dönüşte çağrılır: akışı okur ve aynı anda siler. */
    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                 HttpServletResponse response) {
        String state = state(request);
        return state == null ? null : restore(redis.opsForValue().getAndDelete(KEY_PREFIX + state), request);
    }

    private OAuth2AuthorizationRequest restore(String json, HttpServletRequest request) {
        if (json == null) {
            return null;   // süresi dolmuş, kullanılmış ya da hiç olmamış
        }
        StoredRequest stored;
        try {
            stored = JSON.readValue(json, StoredRequest.class);
        } catch (JsonProcessingException e) {
            log.warn("Saklanan yetkilendirme isteği okunamadı");
            return null;
        }
        if (!OAuthBrowserBinding.same(stored.binding(), binding.fingerprint(request))) {
            // Akış başka bir tarayıcıda başlatılmış (ya da çerez silinmiş).
            log.warn("Google dönüşü akışı başlatan tarayıcıdan gelmedi; reddedildi");
            return null;
        }
        return stored.toRequest();
    }

    private static String state(HttpServletRequest request) {
        String state = request.getParameter(OAuth2ParameterNames.STATE);
        return state == null || state.isBlank() || state.length() > MAX_STATE_LENGTH ? null : state;
    }

    /**
     * Akışın Redis'teki hâli. Değerler Spring'in ürettiği dizeler: nonce, PKCE
     * doğrulayıcısı, kayıt adı (registration_id).
     */
    record StoredRequest(
            String authorizationUri,
            String clientId,
            String redirectUri,
            Set<String> scopes,
            String state,
            Map<String, String> additionalParameters,
            Map<String, String> attributes,
            String authorizationRequestUri,
            String binding
    ) {
        static StoredRequest of(OAuth2AuthorizationRequest request, String binding) {
            return new StoredRequest(request.getAuthorizationUri(), request.getClientId(), request.getRedirectUri(),
                    request.getScopes(), request.getState(), strings(request.getAdditionalParameters()),
                    strings(request.getAttributes()), request.getAuthorizationRequestUri(), binding);
        }

        OAuth2AuthorizationRequest toRequest() {
            return OAuth2AuthorizationRequest.authorizationCode()
                    .authorizationUri(authorizationUri)
                    .clientId(clientId)
                    .redirectUri(redirectUri)
                    .scopes(scopes)
                    .state(state)
                    .additionalParameters(new LinkedHashMap<String, Object>(additionalParameters))
                    .attributes(new LinkedHashMap<String, Object>(attributes))
                    .authorizationRequestUri(authorizationRequestUri)
                    .build();
        }

        private static Map<String, String> strings(Map<String, Object> values) {
            Map<String, String> copy = new LinkedHashMap<>();
            values.forEach((key, value) -> copy.put(key, String.valueOf(value)));
            return copy;
        }
    }
}
