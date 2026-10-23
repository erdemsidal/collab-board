package com.collabboard.overview.dto;

import com.collabboard.presence.PresenceUser;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Ana ekran özeti: kişinin erişebildiği TÜM panolardan derlenen tek bakış.
 *
 * Sayılar listelerden bağımsızdır: liste kısaltılsa bile (ör. 20 kart) sayaç
 * gerçek toplamı söyler.
 *
 * @param boardCount         erişilebilen pano sayısı ("Panolarım" ile aynı küme)
 * @param assignedOpenCount  kişiye atanmış, son kolonda olmayan kartlar
 * @param overdueCount       bunlardan son tarihi geçmiş olanlar
 * @param activityLast7Days  bu panolarda son 7 günde yapılan hareket sayısı
 * @param assignedCards      atanmış açık kartlar, son tarihi en yakın önce (kısaltılmış)
 * @param recentActivity     bu panoların son hareketleri, en yeni önce
 * @param activityByDay      `today` ile biten 7 günün hareket sayısı, eskiden yeniye;
 *                           her zaman 7 satır (hareketsiz gün 0). Gün sınırı UTC.
 * @param doneLast7Days      bu 7 günde son kolona taşınan farklı kart sayısı
 * @param donePrev7Days      ondan önceki 7 günde aynısı (kıyas için)
 * @param liveBoards         şu an başka birinin açık olduğu panolar (en fazla 5)
 * @param team               panolarından en az birini paylaştığı kişiler, çevrimiçi önce (en fazla 12)
 * @param boardsHealth       en son hareket gören panoların doluluğu ve tıkanıklığı (en fazla 6)
 * @param aging              en uzun süredir aynı kolonda bekleyen açık kartlar (en fazla 5)
 */
public record OverviewResponse(
        int boardCount,
        int assignedOpenCount,
        int overdueCount,
        long activityLast7Days,
        List<AssignedCard> assignedCards,
        List<RecentActivity> recentActivity,
        List<DayCount> activityByDay,
        int doneLast7Days,
        int donePrev7Days,
        List<LiveBoard> liveBoards,
        List<TeamMember> team,
        List<BoardHealth> boardsHealth,
        List<AgingCard> aging
) {

    /** Bir günün hareket sayısı. */
    public record DayCount(LocalDate date, long count) {
    }

    /** Şu an başka birinin açık olduğu pano ve oradakiler (isteği yapan hariç). */
    public record LiveBoard(Long boardId, String boardName, List<Person> people) {
    }

    /**
     * Bir kişi, panodaki çevrimiçi listesiyle aynı görünümde (renk ve baş harfler
     * PresenceUser'dan gelir). Oturum kimliği dışarı verilmez.
     */
    public record Person(Long userId, String name, String initials, String color, String avatarUrl) {
        public static Person of(PresenceUser user) {
            return new Person(user.userId(), user.name(), user.initials(), user.color(), user.avatarUrl());
        }
    }

    /**
     * Panolarından en az birini paylaştığı biri.
     *
     * @param online şu an erişilebilen panolardan birinde açık mı
     * @param where  açıksa hangi panoda (yalnızca isteği yapanın erişebildiği panolar)
     */
    public record TeamMember(Long userId, String name, String initials, String color, String avatarUrl,
                             boolean online, String where) {
        public static TeamMember of(PresenceUser user, String where) {
            return new TeamMember(user.userId(), user.name(), user.initials(), user.color(),
                    user.avatarUrl(), where != null, where);
        }
    }

    /**
     * Bir panonun sağlık özeti.
     *
     * @param columns      kolonlar panodaki sırayla, kart sayısı ve WIP limitiyle
     * @param bottleneck   akış ölçümünün bulduğu darboğaz kolonunun adı (ölçülemiyorsa null)
     * @param wipFull      kart sayısı WIP limitine ulaşmış ilk kolonun adı (yoksa null)
     * @param openCards    son kolonda olmayan kartlar
     * @param overdueCards bunlardan son tarihi geçmiş olanlar
     */
    public record BoardHealth(Long boardId, String boardName, List<ColumnLoad> columns, String bottleneck,
                              String wipFull, int openCards, int overdueCards, LocalDateTime lastActivityAt) {
    }

    /** Kolonun doluluğu; wipLimit null = sınırsız. */
    public record ColumnLoad(String name, int count, Integer wipLimit) {
    }

    /**
     * Uzun süredir aynı kolonda bekleyen açık bir kart.
     *
     * @param ageSeconds şu anki kolonuna girişinden bu yana geçen süre
     * @param estimated  giriş anı olay kaydında yok, oluşturulma anı kullanıldı (alt sınır)
     */
    public record AgingCard(Long cardId, String title, Long boardId, String boardName, String columnName,
                            long ageSeconds, boolean estimated) {
    }

    /** Kişiye atanmış açık bir kart ve nerede durduğu. */
    public record AssignedCard(
            Long cardId,
            String title,
            Long boardId,
            String boardName,
            Long columnId,
            String columnName,
            LocalDate dueDate
    ) {
    }

    /**
     * Bir pano hareketi — pano geçmişindeki satırın, hangi panoda olduğunu da
     * söyleyen hâli (özet birden çok panoyu karıştırır).
     */
    public record RecentActivity(
            Long id,
            Long boardId,
            String boardName,
            String actorName,
            String actorAvatarUrl,
            String type,
            String description,
            LocalDateTime createdAt
    ) {
    }
}
