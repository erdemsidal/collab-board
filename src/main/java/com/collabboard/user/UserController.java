package com.collabboard.user;

import com.collabboard.auth.dto.TokenRefreshResponse;
import com.collabboard.user.dto.ChangePasswordRequest;
import com.collabboard.user.dto.UpdateProfileRequest;
import com.collabboard.user.dto.UserResponse;
import com.collabboard.user.entity.User;
import com.collabboard.user.entity.UserAvatar;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

@RestController
public class UserController {

    private final UserService userService;
    private final ProfileService profileService;
    private final UserResponseMapper userResponseMapper;

    public UserController(UserService userService, ProfileService profileService,
                          UserResponseMapper userResponseMapper) {
        this.userService = userService;
        this.profileService = profileService;
        this.userResponseMapper = userResponseMapper;
    }

    /** Mevcut oturumdaki kullanıcı bilgisi; şifresi var mı, hangi dış hesaplar bağlı, dahil. */
    @GetMapping("/api/users/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getUserByEmail(userDetails.getUsername());
        return ResponseEntity.ok(userResponseMapper.toResponse(user));
    }

    /** Ad, soyad ve dil; yalnızca gönderilen alanlar değişir. */
    @PatchMapping("/api/users/me")
    public ResponseEntity<UserResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request,
                                                      @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(profileService.updateProfile(user.getUsername(),
                request.firstName(), request.lastName(), request.locale()));
    }

    /**
     * Şifre değişince yeni oturum jetonları döner; istemci eskilerinin yerine bunları koyar.
     * Hesabın henüz şifresi yoksa (Google ile açıldıysa) mevcut şifre istenmez.
     */
    @PostMapping("/api/users/me/password")
    public ResponseEntity<TokenRefreshResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                                               @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(profileService.changePassword(
                user.getUsername(), request.currentPassword(), request.newPassword()));
    }

    @PutMapping(value = "/api/users/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserResponse> uploadAvatar(@RequestParam("file") MultipartFile file,
                                                     @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(profileService.setAvatar(user.getUsername(), file));
    }

    @DeleteMapping("/api/users/me/avatar")
    public ResponseEntity<UserResponse> removeAvatar(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(profileService.removeAvatar(user.getUsername()));
    }

    /**
     * Profil fotoğrafı — KİMLİKSİZ erişilir.
     *
     * Görseller <img src="..."> ile yüklenir ve tarayıcı bu isteğe Authorization
     * başlığı ekleyemez (oturum jetonu çerezde değil, bellekte). Adres tahmin
     * edilemez bir anahtar taşıdığı için kimliksiz sunmak fotoğrafları
     * taranabilir kılmaz. Anahtar fotoğraf değişince değiştiği için yanıt
     * süresiz önbelleğe alınabilir.
     */
    @GetMapping(AvatarUrls.PREFIX + "{key}")
    public ResponseEntity<byte[]> avatar(@PathVariable String key) {
        UserAvatar avatar = profileService.avatar(key);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatar.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(avatar.getData());
    }
}
