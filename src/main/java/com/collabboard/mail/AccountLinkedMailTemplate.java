package com.collabboard.mail;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Var olan hesaba dış hesapla (Google) giriş bağlandığında giden güvenlik postası.
 *
 * Bağlama sessiz olmamalı: aynı adresin Google hesabına erişen biri artık bu
 * hesaba da girebilir. Hesabın sahibi bunu kendisi yapmadıysa hemen öğrenmeli
 * ve ne yapacağını bilmeli.
 */
@Component
public class AccountLinkedMailTemplate {

    /**
     * Kullanıcılar Türkiye'de, sunucu UTC'de çalışıyor. Postadaki saat kişinin
     * kendi saati olmalı; aksi hâlde "ben o saatte bir şey yapmadım" diye
     * gereksiz yere endişelenir.
     */
    private static final DateTimeFormatter WHEN = DateTimeFormatter
            .ofPattern("d MMMM yyyy, HH:mm", Locale.forLanguageTag("tr"))
            .withZone(ZoneId.of("Europe/Istanbul"));

    public String subject(String providerName) {
        return "CollabBoard hesabına " + providerName + " ile giriş bağlandı";
    }

    public String html(String firstName, String providerName, Instant linkedAt, String link) {
        return """
                <div style="font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;
                            background:#f4f6f9;padding:32px 16px;">
                  <div style="max-width:520px;margin:0 auto;background:#ffffff;border-radius:14px;
                              border:1px solid #dde3ea;overflow:hidden;">
                    <div style="padding:26px 28px 20px;border-bottom:1px solid #eef1f5;">
                      <div style="font-size:18px;font-weight:700;color:#1f2d3d;">CollabBoard</div>
                    </div>
                    <div style="padding:26px 28px;">
                      <p style="margin:0 0 14px;font-size:16px;color:#1f2d3d;">Merhaba %s,</p>
                      <p style="margin:0 0 14px;font-size:14px;line-height:1.6;color:#4a5a6a;">
                        CollabBoard hesabına %s ile giriş bağlandı (%s, Türkiye saati).
                        Artık giriş ekranında "%s ile devam et" diyerek de girebilirsin.
                      </p>
                      <p style="margin:0 0 22px;font-size:14px;line-height:1.6;color:#4a5a6a;">
                        <b style="color:#1f2d3d;">Bunu sen yapmadıysan</b> şifreni hemen değiştir:
                        giriş ekranındaki "Şifremi unuttum" bağlantısına tıkla. %s hesabının
                        şifresini de değiştirmeni öneririz.
                      </p>
                      <a href="%s" style="display:inline-block;background:#f5871f;color:#ffffff;
                         text-decoration:none;font-weight:600;font-size:15px;padding:12px 22px;
                         border-radius:9px;">CollabBoard'u aç</a>
                    </div>
                    <div style="padding:16px 28px 22px;border-top:1px solid #eef1f5;
                                font-size:12px;color:#7b8794;line-height:1.6;">
                      Bunu sen yaptıysan başka bir şey yapmana gerek yok.
                    </div>
                  </div>
                </div>
                """.formatted(escape(firstName), escape(providerName), WHEN.format(linkedAt),
                escape(providerName), escape(providerName), link);
    }

    public String text(String firstName, String providerName, Instant linkedAt, String link) {
        return """
                Merhaba %s,

                CollabBoard hesabına %s ile giriş bağlandı (%s, Türkiye saati).
                Artık giriş ekranında "%s ile devam et" diyerek de girebilirsin.

                Bunu sen yapmadıysan şifreni hemen değiştir: giriş ekranındaki
                "Şifremi unuttum" bağlantısına tıkla. %s hesabının şifresini de
                değiştirmeni öneririz.

                %s

                Bunu sen yaptıysan başka bir şey yapmana gerek yok.
                """.formatted(firstName, providerName, WHEN.format(linkedAt), providerName, providerName, link);
    }

    private String escape(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
