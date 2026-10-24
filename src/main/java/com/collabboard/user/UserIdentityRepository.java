package com.collabboard.user;

import com.collabboard.user.entity.UserIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, Long> {

    Optional<UserIdentity> findByProviderAndSubject(String provider, String subject);

    /** Kişinin bağlı sağlayıcıları, tekrarsız: ["google"]. */
    @Query("SELECT DISTINCT i.provider FROM UserIdentity i WHERE i.userId = :userId ORDER BY i.provider")
    List<String> findProvidersByUserId(@Param("userId") Long userId);
}
