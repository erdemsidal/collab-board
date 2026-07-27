package com.collabboard.auth;

import com.collabboard.auth.entity.RefreshToken;
import com.collabboard.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    /**
     * Kullanıcıya ait tüm refresh token'ları sil — logout.
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.user = :user")
    void deleteByUser(User user);

    /**
     * Kullanıcı ID ile sil.
     */
    @Modifying
    void deleteAllByUser(User user);
}
