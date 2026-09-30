package com.collabboard.auth;

import com.collabboard.auth.entity.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByToken(String token);

    /**
     * Kullanıcının kullanılmamış jetonlarını kullanılmış say.
     *
     * Yeniden gönderimde çağrılır: aynı anda birden fazla geçerli bağlantı
     * dolaşmasın. Kullanıcı en son gelen postaya güvenir, eskiler ölmelidir.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE EmailVerificationToken t SET t.usedAt = :now "
            + "WHERE t.userId = :userId AND t.usedAt IS NULL")
    int invalidateAllForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
