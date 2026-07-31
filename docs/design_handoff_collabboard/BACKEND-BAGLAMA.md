# Spring Boot'a bağlama

Arayüzün tüm sunucu erişimi `collab-api.js` içinde toplanmıştır. Tek yapılandırma noktası vardır.

## 1. Adresi ver
Tweaks panelinden **Backend → Spring Boot adresi** alanına `http://localhost:8080` yaz.
(Kod tarafında: `configure({ baseUrl: "http://localhost:8080" })`.)
Boş bırakılırsa arayüz yerleşik demo verisiyle çalışır — backend gerekmez.

## 2. Beklenen sözleşme (brief'le birebir)
| Metod | Uç | Yanıt |
|---|---|---|
| POST | `/api/auth/login` | `{ accessToken, refreshToken, user }` |
| POST | `/api/auth/register` | aynı |
| POST | `/api/auth/refresh` | `{ accessToken, refreshToken? }` |
| GET | `/api/workspaces` | `[{ id, name, slug, role }]` |
| POST | `/api/workspaces` | `{ id, name, slug, role }` |
| GET | `/api/boards` | `[{ id, name, workspaceId, role, cardCount, memberCount }]` |
| POST | `/api/boards` | `{ id, ... }` — gövde `{ name, workspaceId }` |
| GET | `/api/boards/{id}` | pano + `columns[{ id, name, position, cards[{ id, title, version }] }]` |
| GET | `/api/boards/{id}/activity` | `[{ id, actorName, type, description, createdAt }]` |
| GET | `/api/boards/{id}/members` | `[{ id, firstName, lastName, email, role }]` |
| POST | `/api/boards/{id}/members` | gövde `{ email, role }` |
| DELETE | `/api/boards/{id}/members/{userId}` | 204 |

`createdAt` epoch ms veya ISO olabilir; ISO dönerseniz `relTime` için `Date.parse` ekleyin.

Access token her istekte `Authorization: Bearer …` başlığıyla gider; 401 gelince `/api/auth/refresh` ile **sessiz yenileme** yapılır, başarısızsa hata yüzeye çıkar.

## 3. Gerçek zamanlı kanal
`connectRealtime(boardId, handlers)` `@stomp/stompjs` kullanır. Sayfaya CDN'den ekleyin:

```html
<script src="https://cdn.jsdelivr.net/npm/@stomp/stompjs@7/bundles/stomp.umd.min.js"></script>
```

- Bağlantı: `ws(s)://<baseUrl>/ws`, token **STOMP CONNECT** başlığında (URL'de değil).
- Abonelikler: `/topic/board.{id}`, `/topic/board.{id}/presence`, `/user/queue/errors`
- Gönderim: `/app/board/{id}/ops` — `{ type: ADD_CARD | MOVE_CARD | EDIT_CARD | DELETE_CARD | MOVE_COLUMN, ... }`

Arayüz **iyimserdir**: işlem anında ekrana uygulanır, `/user/queue/errors` reddi gelirse çakışma bildirimi gösterilir ve pano REST'ten tazelenir.

## 4. CORS
`baseUrl` farklı porttaysa Spring tarafında origin'e izin verin:

```java
@Bean
CorsConfigurationSource cors() {
  var c = new CorsConfiguration();
  c.setAllowedOrigins(List.of("http://localhost:5173"));
  c.setAllowedMethods(List.of("GET","POST","DELETE","OPTIONS"));
  c.setAllowedHeaders(List.of("*"));
  var s = new UrlBasedCorsConfigurationSource();
  s.registerCorsConfiguration("/**", c);
  return s;
}
```

## 5. Demo anahtarları
Tweaks panelindeki `Sahte ekip hareketleri`, `Çakışma reddini simüle et`, `Rolü zorla` (OWNER/EDITOR/VIEWER) ve `Canlı sistem göstergeleri` yalnızca demo modunu etkiler; gerçek backend bağlıyken devre dışı kalır (rol zorlaması hariç — arayüz kısıtlarını denemek için).
