package com.collabboard.mail;

/**
 * Gönderen bilgisi: "CollabBoard <info@site.com>" → ad "CollabBoard", adres "info@site.com".
 * Yalnızca adres yazılmışsa ad boş kalır.
 */
record MailFrom(String name, String address) {

    static MailFrom parse(String value) {
        if (value == null) {
            return new MailFrom(null, "");
        }
        int open = value.indexOf('<');
        int close = value.lastIndexOf('>');
        if (open >= 0 && close > open) {
            String name = value.substring(0, open).trim();
            return new MailFrom(name.isEmpty() ? null : name, value.substring(open + 1, close).trim());
        }
        return new MailFrom(null, value.trim());
    }
}
