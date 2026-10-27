package com.collabboard.security;

import com.collabboard.auth.oauth.OAuthLoginSetup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration // Bu sınıfın bir Spring konfigürasyon sınıfı olduğunu belirtir
@EnableWebSecurity // Spring Security'yi aktif eder ve özelleştirme yapılmasına izin verir
public class SecurityConfig {

    // JWT doğrulama filtremizi enjekte ediyoruz
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JsonAuthenticationEntryPoint authenticationEntryPoint;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          JsonAuthenticationEntryPoint authenticationEntryPoint) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    /**
     * SecurityFilterChain bean'i: Tüm HTTP güvenlik kurallarını burada tanımlıyoruz.
     *
     * OAuthLoginSetup yapıcıya değil buraya verilir: Google girişi hesap açarken
     * bu sınıfın ürettiği PasswordEncoder'a dayanıyor; yapıcıda istense iki
     * nesne birbirini beklerdi (döngüsel bağımlılık).
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, OAuthLoginSetup oauthLogin) throws Exception {
        http
                // CSRF korumasını devre dışı bırakıyoruz çünkü JWT tabanlı stateless bir yapı kullanıyoruz;
                // tarayıcı cookie'leri ile çalışmadığımız için CSRF saldırısı riski yoktur
                .csrf(csrf -> csrf.disable())

                // Oturum yönetimini STATELESS yapıyoruz; sunucu tarafında hiçbir session tutulmaz,
                // her istek kendi JWT token'ı ile kimliğini kanıtlamak zorundadır
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Kimliksiz istek 401 alır (varsayılan 403'tü); istemci bunu görünce
                // jetonunu yeniler. Ayrıntı JsonAuthenticationEntryPoint'te.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint))

                // Endpoint bazlı yetkilendirme kurallarını tanımlıyoruz
                .authorizeHttpRequests(auth -> auth
                        // Kimlik doğrulama endpoint'leri herkese açık olmalı (login, register, token yenileme)
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/refresh",
                                // Doğrulama bağlantısı e-postadan gelir; tıklayan
                                // kişinin henüz oturumu yoktur.
                                "/api/auth/verify",
                                "/api/auth/resend-verification",
                                // Şifresini unutan kişinin oturumu yoktur.
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                // "Google ile devam et": düğmeler ve dönüşteki kodun takası.
                                "/api/auth/providers",
                                "/api/auth/oauth/exchange"
                        ).permitAll()

                        // Google akışının iki ayağı: Google'a gidiş ve dönüş. İkisini de
                        // Spring Security'nin filtreleri karşılar (ADR 0018). Google
                        // kurulu değilse bu adresler yoktur ve 404 döner.
                        .requestMatchers("/oauth2/authorization/**", "/login/oauth2/code/**").permitAll()

                        // Uygulama sağlık kontrolü endpoint'i; load balancer ve monitoring araçları için açık
                        .requestMatchers("/actuator/health").permitAll()

                        // Profil fotoğrafları <img> ile yüklenir; tarayıcı bu isteğe oturum
                        // jetonu ekleyemez. Adres tahmin edilemez anahtar taşır (bkz. V15).
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/avatars/*").permitAll()

                        // Metrikler — yerelde tarayıcıdan bakılabilsin diye açık.
                        // Canlıda (prod) bu uçlar hiç yayınlanmıyor: application-prod.yml
                        // dışarıya yalnızca /actuator/health'i açar, gerisi 404 döner.
                        .requestMatchers("/actuator/metrics/**", "/actuator/info").permitAll()

                        // Swagger/OpenAPI dökümantasyon endpoint'leri; geliştirme ortamında API'yi test etmek için açık
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()

                        // WebSocket EL SIKIŞMASI açık (ADR 0005): tarayıcı handshake'e
                        // özel başlık ekleyemediği için kimlik bir sonraki adımda,
                        // STOMP CONNECT frame'inde doğrulanır (WebSocketAuthInterceptor).
                        // Kimliksiz bağlantı CONNECT'te reddedilir; abone olamaz, mesaj yollayamaz.
                        .requestMatchers("/ws/**").permitAll()

                        // Arayüzün statik dosyaları — giriş ekranını da bunlar sunuyor.
                        // collab-api.js kimlik gerektirmez; içindeki çağrılar token taşır.
                        // landing.*: oturumu olmayan ziyaretçinin gördüğü tanıtım sayfası.
                        // i18n.js: arayüzün bütün metinleri (TR/EN), giriş öncesi de gerekli.
                        .requestMatchers("/", "/index.html", "/collab-api.js", "/i18n.js", "/landing.js",
                                "/landing.css", "/favicon.ico", "/favicon.svg", "/apple-touch-icon.png").permitAll()

                        // Pano uçları artık kimlik doğrulaması ister (ADR 0005).

                        // Yukarıda belirtilmeyen tüm endpoint'ler kimlik doğrulaması gerektirir
                        .anyRequest().authenticated()
                )

                // JWT filtremizi Spring'in varsayılan UsernamePasswordAuthenticationFilter'ından ÖNCE ekliyoruz;
                // böylece her istek önce JWT kontrolünden geçer, geçerliyse SecurityContext'e kullanıcı bilgisi yazılır
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        // "Google ile devam et" — yalnızca GOOGLE_CLIENT_ID verilmişse açılır.
        oauthLogin.apply(http);

        return http.build(); // Yapılandırılmış SecurityFilterChain nesnesini oluştur ve döndür
    }

    @Bean // Şifreleri hashlemek ve doğrulamak için BCrypt algoritmasını kullanıyoruz;
          // BCrypt her hash'te rastgele salt üretir, bu yüzden aynı şifre bile farklı hash'ler verir
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean // AuthenticationManager bean'i; login işleminde kullanıcı adı ve şifreyi doğrulamak için gereklidir.
          // Spring Security'nin AuthenticationConfiguration üzerinden otomatik olarak sağlanır
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}
