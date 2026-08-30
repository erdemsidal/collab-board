package com.collabboard.mail;

import org.springframework.stereotype.Component;

/**
 * Doğrulama postasının içeriği.
 *
 * Şablon motoru (Thymeleaf vb.) eklemedik: tek bir posta için ayrı bir motor,
 * ayrı bir dosya düzeni ve ayrı bir bağımlılık demek. İkinci ve üçüncü posta
 * geldiğinde bu karar yeniden gözden geçirilmeli.
 *
 * Tasarım e-posta istemcilerine göre kısıtlı: dış CSS, web fontu ve JavaScript
 * çalışmaz; bu yüzden stiller satır içinde ve düzen tablo yerine basit bloklarla.
 */
@Component
public class VerificationMailTemplate {

    public String subject() {
        return "CollabBoard hesabını doğrula";
    }

    public String html(String firstName, String link, int expiryHours) {
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
                        Hesabını kullanmaya başlamak için e-posta adresini doğrulaman gerekiyor.
                        Aşağıdaki düğmeye tıkla — bu bağlantı <b>%d saat</b> geçerli.
                      </p>
                      <a href="%s" style="display:inline-block;background:#f5871f;color:#ffffff;
                         text-decoration:none;font-weight:600;font-size:15px;padding:12px 22px;
                         border-radius:9px;">Hesabımı doğrula</a>
                      <p style="margin:22px 0 0;font-size:12.5px;line-height:1.6;color:#7b8794;">
                        Düğme çalışmazsa bu adresi tarayıcına yapıştır:<br>
                        <span style="color:#4a7fc1;word-break:break-all;">%s</span>
                      </p>
                    </div>
                    <div style="padding:16px 28px 22px;border-top:1px solid #eef1f5;
                                font-size:12px;color:#7b8794;line-height:1.6;">
                      Bu hesabı sen açmadıysan bu postayı yok sayabilirsin —
                      doğrulanmayan hesaplar kullanılamaz.
                    </div>
                  </div>
                </div>
                """.formatted(escape(firstName), expiryHours, link, link);
    }

    public String text(String firstName, String link, int expiryHours) {
        return """
                Merhaba %s,

                CollabBoard hesabını kullanmaya başlamak için e-posta adresini doğrula.
                Bu bağlantı %d saat geçerli:

                %s

                Bu hesabı sen açmadıysan bu postayı yok sayabilirsin.
                """.formatted(firstName, expiryHours, link);
    }

    /** Ad postanın HTML gövdesine giriyor; etiket enjeksiyonunu engelle. */
    private String escape(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
