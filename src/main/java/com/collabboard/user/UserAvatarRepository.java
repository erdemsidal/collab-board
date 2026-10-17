package com.collabboard.user;

import com.collabboard.user.entity.UserAvatar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAvatarRepository extends JpaRepository<UserAvatar, String> {

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM UserAvatar a WHERE a.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
