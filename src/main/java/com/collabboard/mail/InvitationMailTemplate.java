package com.collabboard.mail;

import org.springframework.stereotype.Component;

/**
 * Henüz hesabı olmayan birine giden davet postası.
 *
 * Hesabı olan kişiye posta gönderilmiyor: daveti uygulamanın içinde, canlı
 * olarak görüyor. Hesabı olmayan kişinin ise davetten haberdar olmasının başka
 * yolu yok — bağlantı kayıt ekranını bu adres dolu olarak açar.
 */
@Component
public class InvitationMailTemplate {

    public String subject(String inviterName, String boardName) {
        return inviterName + " seni CollabBoard'da \"" + boardName + "\" panosuna davet etti";
    }

    public String html(String inviterName, String boardName, String roleLabel, String link) {
        return """
                <div style="font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;
                            background:#f4f6f9;padding:32px 16px;">
                  <div style="max-width:520px;margin:0 auto;background:#ffffff;border-radius:14px;
                              border:1px solid #dde3ea;overflow:hidden;">
                    <div style="padding:26px 28px 20px;border-bottom:1px solid #eef1f5;">
                      <div style="font-size:18px;font-weight:700;color:#1f2d3d;">CollabBoard</div>
                    </div>
                    <div style="padding:26px 28px;">
                      <p style="margin:0 0 22px;font-size:14px;line-height:1.6;color:#4a5a6a;">
                        <b style="color:#1f2d3d;">%s</b>, seni <b style="color:#1f2d3d;">%s</b> panosuna
                        <b style="color:#1f2d3d;">%s</b> olarak davet etti. Daveti görmek için bu
                        e-posta adresiyle hesap oluştur; giriş yaptığında davet seni bekliyor olacak.
                      </p>
                      <a href="%s" style="display:inline-block;background:#f5871f;color:#ffffff;
                         text-decoration:none;font-weight:600;font-size:15px;padding:12px 22px;
                         border-radius:9px;">Hesap oluştur</a>
                    </div>
                    <div style="padding:16px 28px 22px;border-top:1px solid #eef1f5;
                                font-size:12px;color:#7b8794;line-height:1.6;">
                      Bu kişiyi tanımıyorsan postayı yok sayabilirsin; davet sen kabul etmedikçe
                      hiçbir şey yapmaz.
                    </div>
                  </div>
                </div>
                """.formatted(escape(inviterName), escape(boardName), escape(roleLabel), link);
    }

    public String text(String inviterName, String boardName, String roleLabel, String link) {
        return """
                %s, seni CollabBoard'da "%s" panosuna %s olarak davet etti.

                Daveti görmek için bu e-posta adresiyle hesap oluştur:
                %s

                Bu kişiyi tanımıyorsan postayı yok sayabilirsin.
                """.formatted(inviterName, boardName, roleLabel, link);
    }

    private String escape(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
