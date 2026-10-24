package com.collabboard.overview;

import com.collabboard.board.BoardMemberRepository;
import com.collabboard.board.entity.Board;
import com.collabboard.overview.dto.OverviewResponse.LiveBoard;
import com.collabboard.overview.dto.OverviewResponse.Person;
import com.collabboard.overview.dto.OverviewResponse.TeamMember;
import com.collabboard.presence.PresenceService;
import com.collabboard.presence.PresenceUser;
import com.collabboard.user.UserRepository;
import com.collabboard.workspace.WorkspaceMemberRepository;
import com.collabboard.workspace.entity.WorkspaceRole;
import org.springframework.stereotype.Component;

import java.text.Collator;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ana ekran özetinin kişiler bölümü: şu an kim nerede, kimlerle çalışıyorum.
 *
 * Çevrimiçi bilgisi YALNIZCA isteği yapanın erişebildiği panolardan okunur. Bir ekip
 * arkadaşı göremediğim bir panodaysa "çevrimdışı" görünür; "nerede" alanı erişemediğim
 * bir panonun adını sızdıramaz.
 *
 * Maliyet pano sayısından bağımsız: presence tek Redis turu, üyeler iki sorgu,
 * kişiler tek sorgu.
 */
@Component
class TeamSection {

    static final int LIVE_BOARD_LIMIT = 5;
    static final int TEAM_LIMIT = 12;

    /** Türkçe sıralama: "Çağla" C'den sonra, "Şule" S'den sonra gelsin. */
    private static final Collator TURKISH = Collator.getInstance(Locale.forLanguageTag("tr"));

    record Result(List<LiveBoard> liveBoards, List<TeamMember> team) {
    }

    private final PresenceService presenceService;
    private final BoardMemberRepository boardMemberRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;

    TeamSection(PresenceService presenceService, BoardMemberRepository boardMemberRepository,
                WorkspaceMemberRepository workspaceMemberRepository, UserRepository userRepository) {
        this.presenceService = presenceService;
        this.boardMemberRepository = boardMemberRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.userRepository = userRepository;
    }

    /** @param boards isteği yapanın erişebildiği panolar (boş olmamalı) */
    Result summarize(Map<Long, Board> boards, Long me) {
        // Kendisi dışında biri açık olan panolar; en kalabalık önce.
        List<LiveBoard> live = presenceService.onlineByBoard(boards.keySet()).entrySet().stream()
                .map(e -> new LiveBoard(e.getKey(), boards.get(e.getKey()).getName(),
                        e.getValue().stream().filter(u -> !u.userId().equals(me)).map(Person::of).toList()))
                .filter(board -> !board.people().isEmpty())
                .sorted(Comparator.comparingInt((LiveBoard b) -> b.people().size()).reversed()
                        .thenComparing(LiveBoard::boardId, Comparator.reverseOrder()))
                .toList();

        // Kişi birden çok panoda açıksa "nerede" en kalabalık olanı gösterir.
        Map<Long, String> where = new HashMap<>();
        live.forEach(b -> b.people().forEach(p -> where.putIfAbsent(p.userId(), b.boardName())));

        return new Result(live.stream().limit(LIVE_BOARD_LIMIT).toList(), team(boards, me, where));
    }

    /**
     * En az bir panoyu paylaştığı kişiler: panoların doğrudan üyeleri ve o panoların
     * alanlarının üyeleri (misafir hariç — alan rolü ona pano açmaz). Çevrimiçiler önce,
     * sonra ada göre.
     */
    private List<TeamMember> team(Map<Long, Board> boards, Long me, Map<Long, String> where) {
        Set<Long> people = new HashSet<>(boardMemberRepository.findUserIdsByBoardIdIn(boards.keySet()));
        Set<Long> workspaces = new HashSet<>();
        boards.values().forEach(b -> workspaces.add(b.getWorkspaceId()));
        people.addAll(workspaceMemberRepository.findUserIdsByWorkspaceIdInAndRoleNot(workspaces, WorkspaceRole.GUEST));
        people.remove(me);
        if (people.isEmpty()) {
            return List.of();
        }

        return userRepository.findPeople(people).stream()
                // Renk ve baş harfler panodaki çevrimiçi listesiyle aynı yerden.
                .map(row -> TeamMember.of(PresenceUser.of(null, row.getId(), row.getFirstName(),
                        row.getLastName(), row.getAvatarKey()), where.get(row.getId())))
                .sorted(Comparator.comparing((TeamMember m) -> !m.online())
                        .thenComparing(TeamMember::name, TURKISH))
                .limit(TEAM_LIMIT)
                .toList();
    }
}
