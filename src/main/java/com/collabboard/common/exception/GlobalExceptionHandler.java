package com.collabboard.common.exception;

import com.collabboard.common.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Merkezi hata yönetimi — Tüm controller exception'ları burada yakalanır.
 *
 * Her hata tipi için tutarlı ApiErrorResponse döner.
 * Production'da beklenmeyen hatalar Sentry'ye otomatik gider (logback üzerinden).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── 404 — Kaynak bulunamadı ──────────────────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException ex,
                                                            HttpServletRequest request) {
        log.warn("Kaynak bulunamadı: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    /**
     * Olmayan bir adres istendi: tarayıcının kendiliğinden istediği /favicon.ico,
     * canlıda kapalı olan Swagger ya da yanlış yazılmış bir yol.
     *
     * Bu yakalayıcı olmadan aşağıdaki genel yakalayıcıya düşüp 500 dönüyor ve
     * loga hata olarak yazılıyordu. Her sayfa açılışında bir "hata" birikmesi,
     * gerçek hataları loglarda gözden kaçırtır. Sunucunun bozulduğu bir durum
     * değil; logda iz bırakmaya değmez.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException ex,
                                                             HttpServletRequest request) {
        log.debug("Olmayan adres istendi: {}", request.getRequestURI());
        return buildResponse(HttpStatus.NOT_FOUND, "Böyle bir adres yok.", request);
    }

    // ── 400 — Geçersiz istek ─────────────────────────────

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(BadRequestException ex,
                                                              HttpServletRequest request) {
        log.warn("Geçersiz istek: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    // ── 400 — Validation hataları (@Valid) ────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                              HttpServletRequest request) {
        List<ApiErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::mapFieldError)
                .toList();

        log.warn("Validation hatası: {} alan hatalı", fieldErrors.size());

        ApiErrorResponse body = ApiErrorResponse.withFieldErrors(
                HttpStatus.BAD_REQUEST.value(),
                "Validation Error",
                "İstek doğrulama hatası",
                request.getRequestURI(),
                fieldErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // ── 400 — Constraint violation ───────────────────────

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
                                                                       HttpServletRequest request) {
        log.warn("Constraint violation: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    // ── 409 — Conflict ───────────────────────────────────

    /**
     * İstek gövdesi okunamadı: bozuk JSON, yanlış türde değer ya da tanınmayan alan.
     *
     * Bu bir istemci hatası (400). Ele alınmadığında en alttaki genel yakalayıcıya
     * düşüp 500 dönüyordu — sunucu çökmüş gibi görünüyor ve hata izleme servisini
     * gereksiz yere uyarıyordu. Tanınmayan alanın reddedilmesi de bilinçli: örneğin
     * profil güncellemesine "email" eklemek sessizce yok sayılmaz, açıkça reddedilir.
     */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(Exception ex, HttpServletRequest request) {
        log.warn("Okunamayan istek gövdesi: {} | Path: {}", ex.getMessage(), request.getRequestURI());
        return buildResponse(HttpStatus.BAD_REQUEST,
                "İstek gövdesi okunamadı: biçim hatalı ya da izin verilmeyen bir alan içeriyor.", request);
    }

    /** Yüklenen dosya Spring'in sınırını aştı (application.yml: spring.servlet.multipart). */
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleUploadTooLarge(Exception ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.PAYLOAD_TOO_LARGE, "Dosya çok büyük (en fazla 512 KB).", request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(ConflictException ex,
                                                            HttpServletRequest request) {
        log.warn("Conflict: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    // ── 403 — Token refresh hatası ───────────────────────

    @ExceptionHandler(TokenRefreshException.class)
    public ResponseEntity<ApiErrorResponse> handleTokenRefresh(TokenRefreshException ex,
                                                                HttpServletRequest request) {
        log.warn("Token refresh hatası: {}", ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    // ── 403 — Yetki yok (pano erişimi) ───────────────────

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiErrorResponse> handleForbidden(ForbiddenException ex,
                                                            HttpServletRequest request) {
        log.warn("Yetkisiz erişim: {}", ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    // ── 429 — Kişi başına sınır ──────────────────────────

    /** Gövde ve Retry-After başlığı, kimliksiz uçların 429'uyla aynı (RateLimitFilter). */
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiErrorResponse> handleTooManyRequests(TooManyRequestsException ex,
                                                                  HttpServletRequest request) {
        log.warn("Kişi başına sınır aşıldı: {}", request.getRequestURI());
        ResponseEntity<ApiErrorResponse> response = buildResponse(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request);
        return ResponseEntity.status(response.getStatusCode())
                .header(org.springframework.http.HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(response.getBody());
    }

    // ── 401 — Authentication hatası ──────────────────────

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException ex,
                                                                  HttpServletRequest request) {
        log.warn("Authentication hatası: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNAUTHORIZED, "Geçersiz kimlik bilgileri", request);
    }

    /**
     * Hesap var, şifre doğru, ama e-posta henüz doğrulanmamış.
     *
     * AuthenticationException'dan ÖNCE ele alınmalı (Spring en özel eşleşmeyi
     * seçer): genel "Geçersiz kimlik bilgileri" mesajı kullanıcıyı şifresini
     * yanlış girdiğini sanmaya iter, oysa tek yapması gereken postasına bakmak.
     */
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiErrorResponse> handleDisabled(DisabledException ex,
                                                            HttpServletRequest request) {
        log.info("Doğrulanmamış hesapla giriş denemesi");
        return buildResponse(HttpStatus.FORBIDDEN,
                "E-posta adresin henüz doğrulanmadı. Gelen kutunu kontrol et "
                        + "ya da yeni bir doğrulama bağlantısı iste.", request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(BadCredentialsException ex,
                                                                  HttpServletRequest request) {
        log.warn("Hatalı giriş denemesi: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNAUTHORIZED, "Email veya şifre hatalı", request);
    }

    // ── 403 — Erişim reddedildi ──────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                                HttpServletRequest request) {
        log.warn("Erişim reddedildi: {}", ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, "Bu işlem için yetkiniz yok", request);
    }

    // ── 500 — Beklenmeyen hata (fallback) ────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneral(Exception ex,
                                                           HttpServletRequest request) {
        // ERROR seviyesinde logla — Sentry otomatik yakalar
        log.error("Beklenmeyen hata: {} | Path: {}", ex.getMessage(), request.getRequestURI(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "Beklenmeyen bir hata oluştu. Lütfen daha sonra tekrar deneyin.", request);
    }

    // ── Helper ───────────────────────────────────────────

    private ResponseEntity<ApiErrorResponse> buildResponse(HttpStatus status, String message,
                                                            HttpServletRequest request) {
        ApiErrorResponse body = ApiErrorResponse.of(
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(body);
    }

    private ApiErrorResponse.FieldError mapFieldError(FieldError fieldError) {
        return new ApiErrorResponse.FieldError(
                fieldError.getField(),
                fieldError.getDefaultMessage(),
                fieldError.getRejectedValue()
        );
    }
}
