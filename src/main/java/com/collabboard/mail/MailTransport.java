package com.collabboard.mail;

/**
 * Postanın sunucudan çıktığı yol.
 *
 * İki yol var, MAIL_PROVIDER ile seçilir:
 *  - smtp  (varsayılan): posta sunucusuna doğrudan bağlanılır (Gmail vb.).
 *  - brevo: posta HTTPS üzerinden Brevo'ya verilir, gerisini o yapar.
 *
 * Neden iki yol? Bazı barındırma platformları (Railway'in Trial/Hobby planları)
 * istenmeyen posta gönderimini önlemek için SMTP portlarını (25/465/587)
 * kapatıyor. HTTPS (443) her yerde açık; aynı posta o kapıdan çıkabiliyor.
 */
public interface MailTransport {

    /**
     * Postayı gönderir. Hata fırlatır; yutma kararı MailService'te.
     *
     * @param from "Ad <adres>" ya da yalnızca adres
     */
    void send(String from, String to, String subject, String html, String text) throws Exception;
}
