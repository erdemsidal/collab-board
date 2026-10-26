package com.collabboard.user;

import com.collabboard.admin.AdminAccess;
import com.collabboard.user.dto.UserResponse;
import com.collabboard.user.entity.User;
import org.springframework.stereotype.Component;

/**
 * Kullanıcıyı istemciye giden biçime çevirir.
 *
 * Tek yerde olması önemli: giriş yanıtı, /api/users/me ve profil işlemleri
 * aynı alanları döndürmeli. Bağlı hesaplar (providers) ayrı bir tablodan
 * geldiği için bu iş entity'nin kendisinden yapılamıyor. "Site sahibi mi?"
 * bilgisi de entity'de değil, ortam değişkeninde (AdminAccess).
 */
@Component
public class UserResponseMapper {

    private final UserIdentityRepository identityRepository;
    private final AdminAccess adminAccess;

    public UserResponseMapper(UserIdentityRepository identityRepository, AdminAccess adminAccess) {
        this.identityRepository = identityRepository;
        this.adminAccess = adminAccess;
    }

    public UserResponse toResponse(User user) {
        return UserResponse.fromEntity(user, identityRepository.findProvidersByUserId(user.getId()),
                adminAccess.isOwner(user.getEmail()));
    }
}
