package com.collabboard.common.tx;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Bir işi, içinde bulunulan veritabanı işlemi BAŞARIYLA kaydedildikten sonra çalıştırır.
 *
 * Canlı bildirimler için gerekli: davet daha veritabanına yazılmadan istemciye
 * "davet geldi" mesajı giderse, kişinin hemen bastığı "Kabul et" daveti
 * bulamaz. İşlem geri alınırsa da hiç gerçekleşmemiş bir olayı duyurmuş
 * oluruz. İşlem yoksa iş hemen çalışır.
 */
public final class AfterCommit {

    private AfterCommit() {
    }

    public static void run(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }
}
