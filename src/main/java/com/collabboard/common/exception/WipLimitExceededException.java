package com.collabboard.common.exception;

/**
 * Kolonun WIP limiti dolu — karta yer yok.
 *
 * Bu bir HATA DEĞİL, kuralın çalışmasıdır: Kanban'da limit dolduğunda yeni iş
 * başlatmak yasaktır, mevcut iş bitirilir. Yine de operasyonu geri almak için
 * RuntimeException olarak fırlatılıyor; @Transactional işlemi geri alır ve
 * gönderene kibar bir reddetme mesajı döner.
 */
public class WipLimitExceededException extends RuntimeException {

    private final Long columnId;

    public WipLimitExceededException(Long columnId, String columnName, int limit) {
        super(String.format("%s kolonu dolu (WIP limiti: %d). Yeni kart eklemeden önce "
                + "buradaki bir işi tamamla.", columnName, limit));
        this.columnId = columnId;
    }

    public Long getColumnId() {
        return columnId;
    }
}
