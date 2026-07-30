package com.collabboard;

import com.collabboard.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Uygulama context'i ayağa kalkıyor mu?
 *
 * Testcontainers sayesinde artık elle "docker compose up" gerekmiyor —
 * Postgres ve Redis'i testin kendisi başlatır (bkz. IntegrationTestBase).
 */
class CollabBoardApplicationTests extends IntegrationTestBase {

    @Test
    @DisplayName("Spring context sorunsuz yükleniyor")
    void contextLoads() {
        // Buraya gelinmesi yeterli: tüm bean'ler kuruldu, Flyway migration'ları
        // çalıştı ve Hibernate şemayı doğruladı (ddl-auto: validate).
    }
}
