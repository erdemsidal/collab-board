package com.collabboard.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService customUserDetailsService;

    // Constructor injection (Spring Boot'ta @Autowired yazmaya gerek yoktur,
    // otomatik enjekte edilir)
    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, CustomUserDetailsService customUserDetailsService) {
        this.tokenProvider = tokenProvider;
        this.customUserDetailsService = customUserDetailsService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        try {
            // 1. Request Header'dan token'ı al
            String jwt = getJwtFromRequest(request);

            // 2. Token boş değilse ve bizim tokenProvider üzerinden doğrulandıysa
            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {

                // 3. Token'ın içinden kullanıcının email/kullanıcı adını çıkar
                String username = tokenProvider.getUsernameFromToken(jwt);

                // 4. Veritabanından o kullanıcıyı bul ve detaylarını yükle
                UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);

                // 5. Spring Security için doğrulanmış (Authenticated) bir obje oluştur
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());

                // 6. Bu isteğin detaylarını (kullanıcının IP adresi, session bilgisi vb.) ekle
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // 7. Oluşturulan kimliği Spring'in Güvenlik Context'ine yerleştir
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            // Hata olursa filtre zinciri kopmasın, uygulama çökmesin diye logla
            logger.error("Kullanıcı kimlik doğrulaması yapılamadı: " + ex.getMessage());
        }

        // 8. İşlem başarılı da olsa, başarısız da olsa diğer filtrelere ve controller'a
        // gitmesine izin ver
        filterChain.doFilter(request, response);
    }

    // Kod okunabilirliğini artırmak için token çıkarma işlemini ayrı bir metoda
    // aldık
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        // Header'da bilgi var mı ve "Bearer " ile başlıyor mu kontrolü
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7); // "Bearer " kısmını atıp sadece token string'ini döndürür
        }
        return null;
    }
}
