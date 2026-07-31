package com.collabboard.workspace;

import com.collabboard.support.IntegrationTestBase;
import com.collabboard.user.UserService;
import com.collabboard.workspace.entity.Workspace;
import com.collabboard.workspace.entity.WorkspaceRole;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Çalışma alanı (şirket) yetkilendirmesi — özelliğin asıl vaadi:
 * "ekibe bir kez davet et, şirketin tüm panolarına erişsin."
 *
 * REST uçları henüz yazılmadığı için şirket işlemleri servis üzerinden yapılıyor;
 * pano erişimi ise gerçek HTTP ile sınanıyor (asıl doğrulamak istediğimiz o).
 */
class WorkspaceAccessIntegrationTest extends IntegrationTestBase {

    @Autowired
    WorkspaceService workspaceService;

    @Autowired
    WorkspaceAccessService workspaceAccessService;

    @Autowired
    UserService userService;

    /** Şirket kurar ve panoyu o şirkette açar; panonun id'sini döner. */
    private long boardInWorkspace(String ownerToken, String ownerEmail, Workspace workspace, String boardName) {
        ResponseEntity<JsonNode> response = rest.exchange("/api/boards", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", boardName, "workspaceId", workspace.getId()),
                        authHeaders(ownerToken)), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").asLong();
    }

    private ResponseEntity<JsonNode> getBoard(long boardId, String token) {
        return rest.exchange("/api/boards/" + boardId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), JsonNode.class);
    }

    @Test
    @DisplayName("Şirket üyesi, panoya AYRICA davet edilmeden erişebilir")
    void sirketUyesiTumPanolaraErisir() {
        String ownerEmail = uniqueEmail("ws");
        String memberEmail = uniqueEmail("ws");
        String ownerToken = registerAndLogin("Sirket", "Sahibi", ownerEmail);
        String memberToken = registerAndLogin("Ekip", "Uyesi", memberEmail);

        Workspace workspace = workspaceService.create("Acme Yazılım", userService.getUserByEmail(ownerEmail));
        long boardId = boardInWorkspace(ownerToken, ownerEmail, workspace, "Şirket Panosu");

        // Üye ŞİRKETE eklenir — panoya tek kelime davet YOK.
        workspaceAccessService.addMember(workspace.getId(),
                userService.getUserByEmail(memberEmail).getId(), WorkspaceRole.MEMBER);

        assertThat(getBoard(boardId, memberToken).getStatusCode()).isEqualTo(HttpStatus.OK);

        // "Panolarım" listesinde de görünür; şirket MEMBER'ı panoda EDITOR sayılır.
        JsonNode myBoards = rest.exchange("/api/boards", HttpMethod.GET,
                new HttpEntity<>(authHeaders(memberToken)), JsonNode.class).getBody();
        assertThat(myBoards.toString()).contains("Şirket Panosu");
        JsonNode row = myBoards.get(0);
        assertThat(row.get("myRole").asText()).isEqualTo("EDITOR");
    }

    @Test
    @DisplayName("GUEST şirketin panolarını göremez")
    void misafirSirketPanolariniGoremez() {
        String ownerEmail = uniqueEmail("ws");
        String guestEmail = uniqueEmail("ws");
        String ownerToken = registerAndLogin("Sirket", "Sahibi", ownerEmail);
        String guestToken = registerAndLogin("Dis", "Paydas", guestEmail);

        Workspace workspace = workspaceService.create("Gizli Şirket", userService.getUserByEmail(ownerEmail));
        long boardId = boardInWorkspace(ownerToken, ownerEmail, workspace, "İç Pano");

        workspaceAccessService.addMember(workspace.getId(),
                userService.getUserByEmail(guestEmail).getId(), WorkspaceRole.GUEST);

        // Şirkette görünüyor ama panolara erişimi yok — yalnızca ayrıca davet edildikleri.
        assertThat(getBoard(boardId, guestToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Pano bazlı istisna, şirket rolünü ezer (üye tek panoda kısıtlanabilir)")
    void panoIstisnasiSirketRolunuEzer() {
        String ownerEmail = uniqueEmail("ws");
        String memberEmail = uniqueEmail("ws");
        String ownerToken = registerAndLogin("Sirket", "Sahibi", ownerEmail);
        String memberToken = registerAndLogin("Ekip", "Uyesi", memberEmail);

        Workspace workspace = workspaceService.create("Karma Şirket", userService.getUserByEmail(ownerEmail));
        long boardId = boardInWorkspace(ownerToken, ownerEmail, workspace, "Hassas Pano");

        workspaceAccessService.addMember(workspace.getId(),
                userService.getUserByEmail(memberEmail).getId(), WorkspaceRole.MEMBER);

        // Şirket rolü MEMBER → normalde EDITOR olurdu. Bu panoda VIEWER'a düşürüyoruz.
        addMember(boardId, ownerToken, memberEmail, "VIEWER");

        JsonNode myBoards = rest.exchange("/api/boards", HttpMethod.GET,
                new HttpEntity<>(authHeaders(memberToken)), JsonNode.class).getBody();
        assertThat(myBoards.get(0).get("myRole").asText()).isEqualTo("VIEWER");
    }

    @Test
    @DisplayName("Şirketten çıkarılan kişi TÜM panolara erişimini kaybeder")
    void sirkettenCikarilanTumPanolariKaybeder() {
        String ownerEmail = uniqueEmail("ws");
        String memberEmail = uniqueEmail("ws");
        String ownerToken = registerAndLogin("Sirket", "Sahibi", ownerEmail);
        String memberToken = registerAndLogin("Ayrilan", "Calisan", memberEmail);

        Workspace workspace = workspaceService.create("Ayrılık Testi", userService.getUserByEmail(ownerEmail));
        long board1 = boardInWorkspace(ownerToken, ownerEmail, workspace, "Pano A");
        long board2 = boardInWorkspace(ownerToken, ownerEmail, workspace, "Pano B");

        Long memberId = userService.getUserByEmail(memberEmail).getId();
        workspaceAccessService.addMember(workspace.getId(), memberId, WorkspaceRole.MEMBER);

        assertThat(getBoard(board1, memberToken).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getBoard(board2, memberToken).getStatusCode()).isEqualTo(HttpStatus.OK);

        // Tek işlem: şirket üyeliğini sil → her iki panoya da erişim biter.
        // Özelliğin asıl kazancı bu: panoları tek tek dolaşmaya gerek yok.
        workspaceAccessService.removeMember(workspace.getId(), memberId);

        assertThat(getBoard(board1, memberToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(getBoard(board2, memberToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Çalışma alanı belirtilmezse pano kişisel alana açılır")
    void alanBelirtilmezseKisiselAlanaAcilir() {
        String email = uniqueEmail("ws");
        String token = registerAndLogin("Yalniz", "Kullanici", email);

        long boardId = createBoard(token, "Kişisel Panom").get("id").asLong();

        // Erişebiliyor olması, kişisel alanın oluşturulup panonun ona bağlandığını gösterir.
        assertThat(getBoard(boardId, token).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
