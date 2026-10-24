package com.collabboard.mail;

import org.springframework.stereotype.Component;

/**
 * Dış hesapla (Google) yeni açılan hesaba giden karşılama postası.
 *
 * Kayıt formundan gelen kişi doğrulama postası alıyor; Google ile gelen
 * kişinin adresini Google doğruladığı için ona doğrulama gerekmiyor. Yine de
 * hesabın açıldığını ve bundan sonra nasıl gireceğini bir postayla öğrenmeli.
 */
@Component
public class WelcomeMailTemplate {

    public String subject() {
        return "CollabBoard hesabın açıldı";
    }

    public String html(String firstName, String providerName, String link) {
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
                      <p style="margin:0 0 22px;font-size:14px;line-height:1.6;color:#4a5a6a;">
                        CollabBoard hesabını %s hesabınla açtın. Bundan sonra giriş ekranında
                        "%s ile devam et" demen yeterli.
                      </p>
                      <a href="%s" style="display:inline-block;background:#f5871f;color:#ffffff;
                         text-decoration:none;font-weight:600;font-size:15px;padding:12px 22px;
                         border-radius:9px;">CollabBoard'u aç</a>
                    </div>
                    <div style="padding:16px 28px 22px;border-top:1px solid #eef1f5;
                                font-size:12px;color:#7b8794;line-height:1.6;">
                      İstersen bir şifre de belirleyebilirsin: giriş ekranındaki "Şifremi unuttum"
                      bağlantısından ya da profil sayfandan.
                    </div>
                  </div>
                </div>
                """.formatted(escape(firstName), escape(providerName), escape(providerName), link);
    }

    public String text(String firstName, String providerName, String link) {
        return """
                Merhaba %s,

                CollabBoard hesabını %s hesabınla açtın. Bundan sonra giriş ekranında
                "%s ile devam et" demen yeterli.

                %s

                İstersen bir şifre de belirleyebilirsin: giriş ekranındaki "Şifremi unuttum"
                bağlantısından ya da profil sayfandan.
                """.formatted(firstName, providerName, providerName, link);
    }

    private String escape(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
