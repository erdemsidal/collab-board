package com.collabboard.security;

import com.collabboard.common.dto.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Kimliği olmayan — jetonu eksik, bozuk ya da süresi dolmuş — isteğe 401 döner.
 *
 * Spring Security, oturum açma yöntemi tanımlanmamış (stateless) bir yapıda
 * varsayılan olarak 403 döner. 403 "kim olduğunu biliyorum, izin yok" demektir;
 * istemci bunu "jetonu yenile" diye okuyamaz. Süresi dolan erişim jetonu 403
 * aldığı için tarayıcı hiç yenileme yapmıyor, kullanıcıyı 15 dakikada bir
 * oturumdan atıyordu. 401 ise "kimliğini kanıtla" demektir: istemci refresh
 * token ile yeni erişim jetonu alıp isteği tekrarlar.
 *
 * Yetkisi olmayan ama kimliği belli kullanıcı (üyesi olmadığı pano) 403 almaya
 * devam eder — o durumda yenileme bir şey değiştirmez.
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        objectMapper.writeValue(response.getWriter(), ApiErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(), "Unauthorized",
                "Oturum gerekli ya da süresi dolmuş.", request.getRequestURI()));
    }
}
