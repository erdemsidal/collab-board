package com.collabboard.mail;

import org.springframework.stereotype.Component;

/**
 * Şifre sıfırlama postasının içeriği.
 *
 * Doğrulama postasıyla (VerificationMailTemplate) aynı görsel dil. İki şablon
 * oldu; üçüncüsü gelirse ortak iskeleti ayırmanın ya da bir şablon motoruna
 * geçmenin zamanı gelir.
 */
@Component
public class PasswordResetMailTemplate {

    public String subject() {
        return "CollabBoard şifre sıfırlama";
    }

    public String html(String firstName, String link, int expiryMinutes) {
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
                        Hesabın için şifre sıfırlama isteği aldık. Yeni şifreni belirlemek için
                        aşağıdaki düğmeye tıkla — bu bağlantı <b>%d dakika</b> geçerli ve yalnızca
                        bir kez kullanılabilir.
                      </p>
                      <a href="%s" style="display:inline-block;background:#f5871f;color:#ffffff;
                         text-decoration:none;font-weight:600;font-size:15px;padding:12px 22px;
                         border-radius:9px;">Şifremi sıfırla</a>
                      <p style="margin:22px 0 0;font-size:12.5px;line-height:1.6;color:#7b8794;">
                        Düğme çalışmazsa bu adresi tarayıcına yapıştır:<br>
                        <span style="color:#4a7fc1;word-break:break-all;">%s</span>
                      </p>
                    </div>
                    <div style="padding:16px 28px 22px;border-top:1px solid #eef1f5;
                                font-size:12px;color:#7b8794;line-height:1.6;">
                      Bu isteği sen yapmadıysan bu postayı yok sayabilirsin — şifren değişmez.
                      Şifreni değiştirdiğinde tüm cihazlardaki oturumların kapatılır.
                    </div>
                  </div>
                </div>
                """.formatted(escape(firstName), expiryMinutes, link, link);
    }

    public String text(String firstName, String link, int expiryMinutes) {
        return """
                Merhaba %s,

                Hesabın için şifre sıfırlama isteği aldık. Yeni şifreni belirlemek için
                bu bağlantıyı aç (%d dakika geçerli, tek kullanımlık):

                %s

                Bu isteği sen yapmadıysan bu postayı yok sayabilirsin — şifren değişmez.
                """.formatted(firstName, expiryMinutes, link);
    }

    private String escape(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
