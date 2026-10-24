package com.collabboard.common.exception;

/**
 * Kolonun WIP limiti dolu — karta yer yok.
 *
 * Bu bir HATA DEĞİL, kuralın çalışmasıdır: Kanban'da limit dolduğunda yeni iş
 * başlatmak yasaktır, mevcut iş bitirilir. Yine de operasyonu geri almak için
 * RuntimeException olarak fırlatılıyor; @Transactional işlemi geri alır ve
 * gönderene kibar bir reddetme mesajı döner.
 */
public class WipLimitExceededException extends LocalizedException {

    private final Long columnId;

    public WipLimitExceededException(Long columnId, String columnName, int limit) {
        // Limit metin olarak gider: MessageFormat sayıyı dile göre biçimlemesin.
        super("error.wipLimit", columnName, String.valueOf(limit));
        this.columnId = columnId;
    }

    public Long getColumnId() {
        return columnId;
    }
}
