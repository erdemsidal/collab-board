package com.collabboard.mail;

/**
 * Bütün postaların ortak HTML iskeleti: başlıkta ürün adı, ortada içerik, altta not.
 *
 * Şablon motoru (Thymeleaf vb.) yok: altı kısa posta için ayrı bir motor, dosya
 * düzeni ve bağımlılık fazla. Tasarım e-posta istemcilerine göre kısıtlı: dış
 * CSS, web fontu ve JavaScript çalışmaz; stiller satır içinde, düzen tablo
 * yerine basit bloklarla.
 *
 * Metinler (dile göre değişen kısım) MailText'ten gelir; burası yalnızca biçim.
 * Kişiden gelen her değer (ad, pano adı, yorum) buraya girmeden kaçışlanır.
 */
final class MailLayout {

    private MailLayout() {
    }

    static String page(String brand, String content, String footer) {
        return """
                <div style="font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;
                            background:#f4f6f9;padding:32px 16px;">
                  <div style="max-width:520px;margin:0 auto;background:#ffffff;border-radius:14px;
                              border:1px solid #dde3ea;overflow:hidden;">
                    <div style="padding:26px 28px 20px;border-bottom:1px solid #eef1f5;">
                      <div style="font-size:18px;font-weight:700;color:#1f2d3d;">%s</div>
                    </div>
                    <div style="padding:26px 28px;">
                %s
                    </div>
                    <div style="padding:16px 28px 22px;border-top:1px solid #eef1f5;
                                font-size:12px;color:#7b8794;line-height:1.6;">
                      %s
                    </div>
                  </div>
                </div>
                """.formatted(escape(brand), content, footer);
    }

    /** "Merhaba Ada," satırı; metin kaçışlanmış gelir. */
    static String greeting(String html) {
        return "<p style=\"margin:0 0 14px;font-size:16px;color:#1f2d3d;\">" + html + "</p>\n";
    }

    /** Gövde paragrafı. last: düğmeden ya da alıntıdan önceki son paragraf, altı daha geniş. */
    static String paragraph(String html, boolean last) {
        return "<p style=\"margin:0 0 " + (last ? "22px" : "14px")
                + ";font-size:14px;line-height:1.6;color:#4a5a6a;\">" + html + "</p>\n";
    }

    /** Yorum alıntısı (bahsetme postası). */
    static String quote(String html) {
        return "<div style=\"margin:0 0 22px;padding:12px 14px;border-left:3px solid #f5871f;"
                + "background:#f7f9fb;font-size:14px;line-height:1.6;color:#1f2d3d;\">" + html + "</div>\n";
    }

    static String button(String href, String label) {
        return "<a href=\"" + escape(href) + "\" style=\"display:inline-block;background:#f5871f;color:#ffffff;"
                + "text-decoration:none;font-weight:600;font-size:15px;padding:12px 22px;"
                + "border-radius:9px;\">" + escape(label) + "</a>\n";
    }

    /** Düğme çalışmazsa kopyalanacak adres. */
    static String fallbackLink(String intro, String href) {
        return "<p style=\"margin:22px 0 0;font-size:12.5px;line-height:1.6;color:#7b8794;\">"
                + escape(intro) + "<br>"
                + "<span style=\"color:#4a7fc1;word-break:break-all;\">" + escape(href) + "</span></p>\n";
    }

    /** Cümle içinde koyu yazılan değer (kişi, pano, rol). */
    static String strong(String value) {
        return "<b style=\"color:#1f2d3d;\">" + escape(value) + "</b>";
    }

    /** Postanın HTML gövdesine giren her değer: etiket enjeksiyonunu engelle. */
    static String escape(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
