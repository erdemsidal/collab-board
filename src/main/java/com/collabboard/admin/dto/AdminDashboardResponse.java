package com.collabboard.admin.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Yönetim panelinin tek yanıtı (GET /api/admin/dashboard).
 *
 * "Bugün", "son 7 gün" ve "son 30 gün" takvim günleridir ve {@code timeZone}
 * saatine göre sayılır (tarayıcı kendi saat dilimini gönderir). Son 7 gün
 * bugünü de içerir: bugün ve önceki 6 gün. Grafiğin 30 çubuğunun toplamı bu
 * yüzden "son 30 günde kayıt" sayısına eşittir.
 *
 * Kart başlığı, yorum metni gibi içerik hiçbir alanda yok. Pano adı yalnızca
 * "en hareketli panolar" listesinde.
 */
public record AdminDashboardResponse(
        Instant generatedAt,
        String timeZone,
        Users users,
        List<DayCount> signupsByDay,
        Activity activity,
        Retention retention,
        Methods methods,
        List<LocaleCount> locales,
        Content content,
        List<RecentSignup> recentSignups,
        List<ActiveBoard> topBoards
) {

    /** verified: e-postası doğrulanmış (hesabı açık); unverified: doğrulama bekleyen. */
    public record Users(long total, long today, long last7Days, long last30Days, long verified, long unverified) {}

    public record DayCount(LocalDate date, long count) {}

    /**
     * Son görülmeye göre aktif kişi sayıları.
     *
     * @param onlineNow şu an bir panoyu açık tutan kişi (canlı bağlantılardan);
     *                  Redis okunamazsa null
     */
    public record Activity(Integer onlineNow, long today, long last7Days, long last30Days) {}

    /**
     * Basit bir geri gelme ölçüsü: 7 ile 30 gün önce kayıt olanlar (cohort)
     * içinden son 7 günde siteye yeniden gelenler (returned).
     */
    public record Retention(long cohort, long returned) {}

    /** google: yalnızca Google; password: yalnızca e-posta ve şifre; both: ikisi de bağlı. */
    public record Methods(long google, long password, long both) {}

    public record LocaleCount(String locale, long count) {}

    /** Var olan satırlar sayılır; silinen pano, kart ve yorumlar dahil değil. */
    public record Content(Counter workspaces, Counter boards, Counter cards, Counter comments) {}

    public record Counter(long total, long last7Days) {}

    /**
     * @param method   "google", "password" ya da "both"
     * @param verified e-posta doğrulanmış mı
     * @param boards   üyesi olduğu pano sayısı
     */
    public record RecentSignup(String name, String email, Instant createdAt, String method,
                               boolean verified, Instant lastSeenAt, long boards) {}

    /** @param activity son 7 gündeki işlem sayısı (kart, kolon, yorum, etiket, liste) */
    public record ActiveBoard(String name, long members, long activity) {}
}
