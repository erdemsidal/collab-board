package com.collabboard.admin;

import com.collabboard.admin.dto.AdminDashboardResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Yönetim paneli (ADR 0024): yalnızca site sahipleri (APP_ADMIN_EMAILS).
 *
 * Oturumu olmayan 401 alır, bütün kimlik isteyen adresler gibi. Oturumu olan
 * ama sahip olmayan, olmayan bir adres istemiş gibi 404 alır.
 */
@RestController
public class AdminController {

    private final AdminAccess access;
    private final AdminDashboardService dashboard;

    public AdminController(AdminAccess access, AdminDashboardService dashboard) {
        this.access = access;
        this.dashboard = dashboard;
    }

    /**
     * @param tz tarayıcının saat dilimi ("Europe/Istanbul"); "bugün" ve grafiğin
     *           günleri buna göre. Yoksa ya da tanınmazsa UTC.
     */
    @GetMapping("/api/admin/dashboard")
    public ResponseEntity<AdminDashboardResponse> dashboard(@AuthenticationPrincipal UserDetails user,
                                                            @RequestParam(required = false) String tz,
                                                            HttpServletRequest request) throws NoResourceFoundException {
        // İlk iş: sahip değilse başka hiçbir şeye bakmadan 404.
        access.requireOwner(user.getUsername(), request);
        return ResponseEntity.ok()
                // Kişisel veri (e-postalar) taşıyor: tarayıcı ya da ara katman saklamasın.
                .cacheControl(CacheControl.noStore())
                .body(dashboard.build(dashboard.resolveZone(tz)));
    }
}
