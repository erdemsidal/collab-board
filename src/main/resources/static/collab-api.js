// CollabBoard — sunucu erişim katmanı
// -----------------------------------------------------------------------------
// Tasarım teslimindeki collab-api.js'in ÜRETİM sürümü. Arayüzün tüm sunucu
// erişimi burada toplanır; ekran kodu fetch/STOMP ayrıntısı bilmez.
//
// Teslim sürümünden farkları (hepsi bilinçli):
//   1. Mock veri çıkarıldı — uygulama gerçek backend ile aynı origin'den servis
//      ediliyor. (Tasarım denemeleri için mock'lu kopya docs/ altında duruyor.)
//   2. register(), backend'in sözleşmesine uyarlandı: kayıt token DÖNDÜRMEZ,
//      bu yüzden kayıttan hemen sonra login çağrılır ve tokenlı yanıt döner.
//   3. presence yükü normalize edilir: sunucu {type, users:[...]} gönderir,
//      arayüz düz bir kişi listesi bekler.
// -----------------------------------------------------------------------------

let cfg = { baseUrl: "", wsPath: "/ws" };
let tokens = { accessToken: null, refreshToken: null };

export function configure(next = {}) {
  cfg = { ...cfg, ...next };
  if (typeof cfg.baseUrl === "string") cfg.baseUrl = cfg.baseUrl.trim().replace(/\/$/, "");
}
export function setTokens(t) { tokens = { ...tokens, ...t }; }
export function getTokens() { return tokens; }

async function req(path, { method = "GET", body, auth = true } = {}) {
  const headers = { "Content-Type": "application/json" };
  if (auth && tokens.accessToken) headers.Authorization = "Bearer " + tokens.accessToken;

  const res = await fetch(cfg.baseUrl + path, {
    method, headers, body: body === undefined ? undefined : JSON.stringify(body),
  });

  // Access token'ın süresi dolduysa sessizce yenile ve isteği bir kez tekrarla.
  if (res.status === 401 && auth && tokens.refreshToken) {
    if (await silentRefresh()) return req(path, { method, body, auth });
  }

  if (!res.ok) {
    let msg = "İstek başarısız (" + res.status + ")";
    try {
      const j = await res.json();
      // Doğrulama hatalarında alan bazlı mesaj daha anlamlıdır.
      msg = (j.fieldErrors && j.fieldErrors[0] && j.fieldErrors[0].message) || j.message || j.error || msg;
    } catch (_) {}
    const err = new Error(msg); err.status = res.status; throw err;
  }
  return res.status === 204 ? null : res.json();
}

async function silentRefresh() {
  try {
    const r = await fetch(cfg.baseUrl + "/api/auth/refresh", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken: tokens.refreshToken }),
    });
    if (!r.ok) return false;
    const j = await r.json();
    setTokens({ accessToken: j.accessToken, refreshToken: j.refreshToken || tokens.refreshToken });
    return true;
  } catch (_) { return false; }
}

/* ------------------------------- REST API -------------------------------- */

export const api = {
  login: (email, password) =>
    req("/api/auth/login", { method: "POST", body: { email, password }, auth: false }),

  /**
   * Kayıt + otomatik giriş.
   * Backend kayıt sonrası token üretmez (bilinçli bir güvenlik tercihi); arayüz
   * ise kullanıcıyı kayıttan sonra doğrudan içeri almak ister. İki beklentiyi
   * burada birleştiriyoruz — ekran kodu bu ayrıntıyı bilmek zorunda kalmıyor.
   */
  register: async (p) => {
    await req("/api/auth/register", { method: "POST", body: p, auth: false });
    return req("/api/auth/login", { method: "POST", body: { email: p.email, password: p.password }, auth: false });
  },

  workspaces: () => req("/api/workspaces"),
  createWorkspace: (name) => req("/api/workspaces", { method: "POST", body: { name } }),
  renameWorkspace: (id, name) => req("/api/workspaces/" + id, { method: "PATCH", body: { name } }),
  deleteWorkspace: (id) => req("/api/workspaces/" + id, { method: "DELETE" }),

  boards: () => req("/api/boards"),
  createBoard: (name, workspaceId) => req("/api/boards", { method: "POST", body: { name, workspaceId } }),
  board: (id) => req("/api/boards/" + id),
  activity: (id) => req("/api/boards/" + id + "/activity"),
  members: (id) => req("/api/boards/" + id + "/members"),
  invite: (id, email, role) => req("/api/boards/" + id + "/members", { method: "POST", body: { email, role } }),
  removeMember: (id, userId) => req("/api/boards/" + id + "/members/" + userId, { method: "DELETE" }),
};

/* ---------------------------- Gerçek zamanlı ----------------------------- */

/**
 * Panonun canlı kanalına bağlanır.
 * Token STOMP CONNECT çerçevesinde taşınır — URL'de değil, çünkü URL'ler
 * sunucu loglarına ve tarayıcı geçmişine düşer.
 */
export function connectRealtime(boardId, handlers = {}) {
  const Stomp = window.StompJs;
  if (!Stomp) { handlers.onStatus && handlers.onStatus("down"); return { send() {}, close() {} }; }

  // baseUrl boşsa (aynı origin) adresi sayfanın kendisinden türet.
  const httpBase = cfg.baseUrl || window.location.origin;
  const url = httpBase.replace(/^http/, "ws") + cfg.wsPath;

  const client = new Stomp.Client({
    brokerURL: url,
    connectHeaders: { Authorization: "Bearer " + tokens.accessToken },
    reconnectDelay: 3000,
    onConnect: () => {
      handlers.onStatus && handlers.onStatus("up");
      client.subscribe("/topic/board." + boardId,
        (m) => handlers.onOp && handlers.onOp(JSON.parse(m.body)));
      client.subscribe("/topic/board." + boardId + "/presence",
        (m) => {
          // Sunucu {type:"PRESENCE", users:[{userId,name,initials,color}]} gönderir;
          // arayüze düz bir kişi listesi veriyoruz.
          const body = JSON.parse(m.body);
          const users = (body.users || []).map((u) => ({ id: u.userId, name: u.name }));
          handlers.onPresence && handlers.onPresence(users);
        });
      client.subscribe("/user/queue/errors",
        (m) => handlers.onReject && handlers.onReject(JSON.parse(m.body)));
      // Presence listesine katıl — sunucu bizi çevrimiçi olarak işaretlesin.
      client.publish({ destination: "/app/board/" + boardId + "/presence/join", body: "{}" });
    },
    onWebSocketClose: () => handlers.onStatus && handlers.onStatus("down"),
    onStompError: (frame) => {
      handlers.onStatus && handlers.onStatus("down");
      handlers.onFatal && handlers.onFatal(frame);
    },
  });

  client.activate();
  return {
    send(op) {
      if (client.connected) {
        client.publish({
          destination: "/app/board/" + boardId + "/ops",
          headers: { "content-type": "application/json" },
          body: JSON.stringify(op),
        });
      }
    },
    close() { client.deactivate(); },
  };
}
