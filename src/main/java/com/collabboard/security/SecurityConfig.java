package com.collabboard.security;

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

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean // SecurityFilterChain bean'i: Tüm HTTP güvenlik kurallarını burada tanımlıyoruz
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF korumasını devre dışı bırakıyoruz çünkü JWT tabanlı stateless bir yapı kullanıyoruz;
                // tarayıcı cookie'leri ile çalışmadığımız için CSRF saldırısı riski yoktur
                .csrf(csrf -> csrf.disable())

                // Oturum yönetimini STATELESS yapıyoruz; sunucu tarafında hiçbir session tutulmaz,
                // her istek kendi JWT token'ı ile kimliğini kanıtlamak zorundadır
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Endpoint bazlı yetkilendirme kurallarını tanımlıyoruz
                .authorizeHttpRequests(auth -> auth
                        // Kimlik doğrulama endpoint'leri herkese açık olmalı (login, register, token yenileme)
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/refresh"
                        ).permitAll()

                        // Uygulama sağlık kontrolü endpoint'i; load balancer ve monitoring araçları için açık
                        .requestMatchers("/actuator/health").permitAll()

                        // Metrikler — bu bir DEMO projesi olduğu için tarayıcıdan
                        // bakılabilsin diye açık.
                        // ⚠️ ÜRETİMDE: metrikler iç yapı hakkında bilgi verir; ya kimlik
                        // doğrulamasına bağlanmalı ya da ayrı bir yönetim portuna
                        // (management.server.port) taşınıp dışarıya kapatılmalıdır.
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
                        .requestMatchers("/", "/index.html", "/collab-api.js", "/favicon.ico").permitAll()

                        // Pano uçları artık kimlik doğrulaması ister (ADR 0005).

                        // Yukarıda belirtilmeyen tüm endpoint'ler kimlik doğrulaması gerektirir
                        .anyRequest().authenticated()
                )

                // JWT filtremizi Spring'in varsayılan UsernamePasswordAuthenticationFilter'ından ÖNCE ekliyoruz;
                // böylece her istek önce JWT kontrolünden geçer, geçerliyse SecurityContext'e kullanıcı bilgisi yazılır
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

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
