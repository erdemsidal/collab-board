package com.collabboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableCaching
@EnableJpaAuditing
// E-posta gönderimi @Async ile arka plana alınır; kayıt isteği SMTP'yi beklemez.
// Sanal iş parçacıkları açık olduğu için ayrı bir havuz yapılandırmaya gerek yok.
@EnableAsync
public class CollabBoardApplication {

    public static void main(String[] args) {
        SpringApplication.run(CollabBoardApplication.class, args);
    }
}
