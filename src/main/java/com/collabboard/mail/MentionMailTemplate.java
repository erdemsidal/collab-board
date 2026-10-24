package com.collabboard.mail;

import org.springframework.stereotype.Component;

/**
 * "Seni bir yorumda andı" postası.
 *
 * Kısa tutulur: kim, hangi kart, yorumun ilk 200 karakteri ve kartı açan
 * bağlantı. Yorumun tamamı ve cevap yazmak uygulamada; posta yalnızca haber verir.
 */
@Component
public class MentionMailTemplate {

    /** Postadaki yorum alıntısının en fazla uzunluğu. */
    static final int EXCERPT_LENGTH = 200;

    public String subject(String authorName, String cardTitle) {
        return authorName + " seni bir yorumda andı: " + cardTitle;
    }

    public String html(String authorName, String boardName, String cardTitle, String body, String link) {
        return """
                <div style="font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;
                            background:#f4f6f9;padding:32px 16px;">
                  <div style="max-width:520px;margin:0 auto;background:#ffffff;border-radius:14px;
                              border:1px solid #dde3ea;overflow:hidden;">
                    <div style="padding:26px 28px 20px;border-bottom:1px solid #eef1f5;">
                      <div style="font-size:18px;font-weight:700;color:#1f2d3d;">CollabBoard</div>
                    </div>
                    <div style="padding:26px 28px;">
                      <p style="margin:0 0 16px;font-size:14px;line-height:1.6;color:#4a5a6a;">
                        <b style="color:#1f2d3d;">%s</b>, <b style="color:#1f2d3d;">%s</b> panosundaki
                        <b style="color:#1f2d3d;">%s</b> kartına yazdığı yorumda seni andı:
                      </p>
                      <div style="margin:0 0 22px;padding:12px 14px;border-left:3px solid #f5871f;
                                  background:#f7f9fb;font-size:14px;line-height:1.6;color:#1f2d3d;">%s</div>
                      <a href="%s" style="display:inline-block;background:#f5871f;color:#ffffff;
                         text-decoration:none;font-weight:600;font-size:15px;padding:12px 22px;
                         border-radius:9px;">Kartı aç</a>
                    </div>
                    <div style="padding:16px 28px 22px;border-top:1px solid #eef1f5;
                                font-size:12px;color:#7b8794;line-height:1.6;">
                      Bu postayı, CollabBoard'da bir yorumda adın geçtiği için aldın.
                    </div>
                  </div>
                </div>
                """.formatted(escape(authorName), escape(boardName), escape(cardTitle),
                escape(excerpt(body)), escape(link));
    }

    public String text(String authorName, String boardName, String cardTitle, String body, String link) {
        return """
                %s, "%s" panosundaki "%s" kartına yazdığı yorumda seni andı:

                %s

                Kartı aç: %s
                """.formatted(authorName, boardName, cardTitle, excerpt(body), link);
    }

    /**
     * Yorumun en fazla 200 karakterlik önizlemesi; kesildiyse son karakter "…".
     * Satır sonları tek boşluğa iner: alıntı bir önizleme, yorumun kopyası değil.
     * Kod noktasıyla sayılır ki bir harf ya da işaret ortadan bölünmesin.
     */
    static String excerpt(String body) {
        String flat = body == null ? "" : body.strip().replaceAll("\\s+", " ");
        if (flat.codePointCount(0, flat.length()) <= EXCERPT_LENGTH) {
            return flat;
        }
        int end = flat.offsetByCodePoints(0, EXCERPT_LENGTH - 1);
        return flat.substring(0, end).stripTrailing() + "…";
    }

    private String escape(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
