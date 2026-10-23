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

    /**
     * Kişi listeleri için hafif satırlar, tek sorguda.
     *
     * findAllById yerine: User'ın rolleri EAGER yüklenir ve liste sorgusunda her
     * kullanıcı için ayrı bir rol sorgusu doğar. Ad, soyad ve fotoğraf yetiyor.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT u.id AS id, u.firstName AS firstName, u.lastName AS lastName, u.avatarKey AS avatarKey
            FROM User u WHERE u.id IN :ids
            """)
    java.util.List<PersonRow> findPeople(
            @org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);

    interface PersonRow {
        Long getId();

        String getFirstName();

        String getLastName();

        String getAvatarKey();
    }
}
