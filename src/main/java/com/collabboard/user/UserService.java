package com.collabboard.user;

import com.collabboard.common.exception.ResourceNotFoundException;
import com.collabboard.i18n.AppLocale;
import com.collabboard.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * ID ile kullanıcı getir — cache'li.
     */
    @Cacheable(value = "users", key = "#id")
    public User getUserById(Long id) {
        log.debug("Kullanıcı getiriliyor: id={}", id);
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound.user", id));
    }

    /**
     * Email ile kullanıcı getir.
     */
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound.user", email));
    }

    /**
     * Kişinin kayıtlı dili (ADR 0020). İstek dışında, kişiye bir şey söylenirken
     * kullanılır: canlı kanaldaki reddetmeler. Kişi bulunamazsa varsayılan dil.
     */
    public Locale localeOf(String email) {
        return userRepository.findByEmail(email).map(AppLocale::of).orElse(AppLocale.DEFAULT);
    }
}
