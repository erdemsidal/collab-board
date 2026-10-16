package com.collabboard.user;

import com.collabboard.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    /**
     * Davet adresiyle hesap eşleştirme. Kayıt e-postayı yazıldığı gibi saklıyor;
     * davet ise küçük harfe çevrilmiş adresle tutuluyor. "Erdem@..." ile
     * "erdem@..." aynı kişi.
     */
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmail(String email);
}
