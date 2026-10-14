// CollabBoard — sunucu erişim katmanı
// -----------------------------------------------------------------------------
// Tasarım teslimindeki collab-api.js'in ÜRETİM sürümü. Arayüzün tüm sunucu
// erişimi burada toplanır; ekran kodu fetch/STOMP ayrıntısı bilmez.
//
// Teslim sürümünden farkları (hepsi bilinçli):
//   1. Mock veri çıkarıldı — uygulama gerçek backend ile aynı origin'den servis
//      ediliyor. (Tasarım denemeleri için mock'lu kopya docs/ altında duruyor.)
//   2. register() oturum AÇMAZ: hesap e-posta doğrulanana kadar pasif kalır.
//      Doğrulama ve yeniden gönderim uçları buraya eklendi.
//   3. presence yükü normalize edilir: sunucu {type, users:[...]} gönderir,
//      arayüz düz bir kişi listesi bekler.
// -----------------------------------------------------------------------------

let cfg = { baseUrl: "", wsPath: "/ws" };
let tokens = { accessToken: null, refreshToken: null };

export function configure(next = {}) {
  cfg = { ...cfg, ...next };
  if (typeof cfg.baseUrl === "string") cfg.baseUrl = cfg.baseUrl.trim().replace(/\/$/, "");
}
let tokenListener = null;

/**
 * Jetonlar değişince çağrılır: arka planda sessiz yenileme de dahil.
 *
 * Yenileme refresh token'ı DÖNDÜRÜR (eskisi geçersizleşir). Arayüz yeni çifti
 * kalıcı depoya yazmazsa, sayfa yenilendiğinde eski — artık geçersiz — jetonla
 * açılır ve oturum düşer.
 */
export function onTokens(fn) { tokenListener = fn; }
export function setTokens(t) {
  tokens = { ...tokens, ...t };
  tokenListener && tokenListener(tokens);
}
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

/**
 * Dosya yükleme. req()'den ayrı, çünkü gövde JSON değil FormData: Content-Type
 * başlığını tarayıcı (sınır işaretiyle birlikte) kendisi koymalı.
 */
async function upload(path, method, blob, filename) {
  const form = new FormData();
  form.append("file", blob, filename);
  const send = () => fetch(cfg.baseUrl + path, {
    method, body: form, headers: { Authorization: "Bearer " + tokens.accessToken },
  });
  let res = await send();
  if (res.status === 401 && tokens.refreshToken && await silentRefresh()) res = await send();
  if (!res.ok) {
    let msg = "Yükleme başarısız (" + res.status + ")";
    try { const j = await res.json(); msg = j.message || msg; } catch (_) {}
    const err = new Error(msg); err.status = res.status; throw err;
  }
  return res.json();
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
   * Kayıt. Oturum AÇMAZ — hesap pasif açılır ve e-postaya doğrulama bağlantısı
   * gider. Eskiden burada otomatik giriş zincirleniyordu; doğrulama gelince o
   * kısayol anlamını yitirdi, çünkü kayıt artık tek başına içeri girdirmiyor.
   */
  register: (p) => req("/api/auth/register", { method: "POST", body: p, auth: false }),

  /** Postadaki bağlantıdaki jetonu karşılar; başarılıysa hesap etkinleşir. */
  verifyEmail: (token) =>
    req("/api/auth/verify?token=" + encodeURIComponent(token), { auth: false }),

  /** Doğrulama postasını yeniden ister. Adres kayıtlı olmasa da hata vermez. */
  resendVerification: (email) =>
    req("/api/auth/resend-verification", { method: "POST", body: { email }, auth: false }),

  /** Şifre sıfırlama bağlantısı ister. Adres kayıtlı olmasa da hata vermez. */
  forgotPassword: (email) =>
    req("/api/auth/forgot-password", { method: "POST", body: { email }, auth: false }),

  /** Postadaki jetonla yeni şifreyi belirler. Şifre gövdede, asla URL'de değil. */
  resetPassword: (token, newPassword) =>
    req("/api/auth/reset-password", { method: "POST", body: { token, newPassword }, auth: false }),

  workspaces: () => req("/api/workspaces"),
  createWorkspace: (name) => req("/api/workspaces", { method: "POST", body: { name } }),
  renameWorkspace: (id, name) => req("/api/workspaces/" + id, { method: "PATCH", body: { name } }),
  deleteWorkspace: (id) => req("/api/workspaces/" + id, { method: "DELETE" }),

  boards: () => req("/api/boards"),
  createBoard: (name, workspaceId) => req("/api/boards", { method: "POST", body: { name, workspaceId } }),
  board: (id) => req("/api/boards/" + id),
  activity: (id) => req("/api/boards/" + id + "/activity"),

  // Geçmiş ve akış — ikisi de aynı olay kaydından hesaplanır.
  timeline: (id) => req("/api/boards/" + id + "/timeline"),
  history: (id, upTo) => req("/api/boards/" + id + "/history?upTo=" + upTo),
  flow: (id) => req("/api/boards/" + id + "/flow"),

  members: (id) => req("/api/boards/" + id + "/members"),
  changeRole: (id, userId, role) =>
    req("/api/boards/" + id + "/members/" + userId, { method: "PATCH", body: { role } }),
  removeMember: (id, userId) => req("/api/boards/" + id + "/members/" + userId, { method: "DELETE" }),

  // Davetler — üyelik davet edilenin onayıyla başlar.
  invite: (id, email, role) => req("/api/boards/" + id + "/invitations", { method: "POST", body: { email, role } }),
  pendingInvitations: (id) => req("/api/boards/" + id + "/invitations"),
  cancelInvitation: (id, invId) => req("/api/boards/" + id + "/invitations/" + invId, { method: "DELETE" }),
  acceptInvitation: (invId) => req("/api/invitations/" + invId + "/accept", { method: "POST" }),
  declineInvitation: (invId) => req("/api/invitations/" + invId + "/decline", { method: "POST" }),

  // Bildirimler
  notifications: (limit = 30) => req("/api/notifications?limit=" + limit),
  markNotificationRead: (nid) => req("/api/notifications/" + nid + "/read", { method: "POST" }),
  markAllNotificationsRead: () => req("/api/notifications/read-all", { method: "POST" }),

  // Profil
  me: () => req("/api/users/me"),
  updateProfile: (firstName, lastName) => req("/api/users/me", { method: "PATCH", body: { firstName, lastName } }),
  /** Şifre değişince sunucu yeni jeton çifti verir; eskiler geçersiz. */
  changePassword: async (currentPassword, newPassword) => {
    const t = await req("/api/users/me/password", { method: "POST", body: { currentPassword, newPassword } });
    setTokens({ accessToken: t.accessToken, refreshToken: t.refreshToken });
    return t;
  },
  uploadAvatar: (blob) => upload("/api/users/me/avatar", "PUT", blob, "avatar." + (blob.type.split("/")[1] || "png")),
  removeAvatar: () => req("/api/users/me/avatar", { method: "DELETE" }),
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

  // Panodan kendi isteğimizle çıkınca (close) soket de kapanır; bu bir kopma
  // değildir, "Bağlantı koptu" uyarısı çıkmamalı.
  let closing = false;
  const client = new Stomp.Client({
    brokerURL: url,
    reconnectDelay: 3000,
    // Her (yeniden) bağlanmada GÜNCEL jeton: bağlantı açılırken alınan jeton
    // 15 dakikada süresini doldurur, kopup yeniden bağlanan istemci onunla reddedilirdi.
    beforeConnect: () => { client.connectHeaders = { Authorization: "Bearer " + tokens.accessToken }; },
    onConnect: () => {
      handlers.onStatus && handlers.onStatus("up");
      client.subscribe("/topic/board." + boardId,
        (m) => handlers.onOp && handlers.onOp(JSON.parse(m.body)));
      client.subscribe("/topic/board." + boardId + "/presence",
        (m) => {
          // Sunucu {type:"PRESENCE", users:[{userId,name,initials,color}]} gönderir;
          // arayüze düz bir kişi listesi veriyoruz.
          const body = JSON.parse(m.body);
          const users = (body.users || []).map((u) => ({ id: u.userId, name: u.name, avatarUrl: u.avatarUrl }));
          handlers.onPresence && handlers.onPresence(users);
        });
      client.subscribe("/user/queue/errors",
        (m) => handlers.onReject && handlers.onReject(JSON.parse(m.body)));
      // Presence listesine katıl — sunucu bizi çevrimiçi olarak işaretlesin.
      client.publish({ destination: "/app/board/" + boardId + "/presence/join", body: "{}" });
    },
    onWebSocketClose: () => !closing && handlers.onStatus && handlers.onStatus("down"),
    onStompError: async (frame) => {
      if (closing) return;
      handlers.onStatus && handlers.onStatus("down");
      // Büyük olasılıkla jetonun süresi doldu: yenile, istemci kendisi yeniden bağlanır.
      if (tokens.refreshToken && await silentRefresh()) return;
      // Yenilenemiyorsa oturum geçersiz: yeniden denemeyi KES. Kesilmezse istemci
      // birkaç saniyede bir aynı geçersiz jetonla bağlanmaya çalışmayı sonsuza dek sürdürür.
      client.deactivate();
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
    close() { closing = true; client.deactivate(); },
  };
}

/**
 * Kullanıcının kişisel kanalı: davetler ve bildirimler.
 *
 * Pano bağlantısından AYRI ve oturum boyunca açık — kişi hangi ekranda olursa
 * olsun (ana ekran, başka bir pano, profil) davet anında önüne düşsün diye.
 * Sunucu bu adrese yalnızca bağlanan kişinin kendisine ait mesajları koyar.
 *
 * handlers.onEvent(ev):  {type:"NOTIFICATION", notification} | {type:"INVITATION_RESOLVED", invitationId, status}
 * handlers.onConnect():  her (yeniden) bağlanmada — kopukken kaçan bildirimleri çekmek için
 * handlers.onAuthLost(): oturum yenilenemedi; bağlantı kalıcı olarak kapandı
 */
export function connectInbox(handlers = {}) {
  const Stomp = window.StompJs;
  if (!Stomp) return { close() {} };

  const httpBase = cfg.baseUrl || window.location.origin;
  const client = new Stomp.Client({
    brokerURL: httpBase.replace(/^http/, "ws") + cfg.wsPath,
    reconnectDelay: 4000,
    beforeConnect: () => { client.connectHeaders = { Authorization: "Bearer " + tokens.accessToken }; },
    onConnect: () => {
      client.subscribe("/user/queue/notifications", (m) => handlers.onEvent && handlers.onEvent(JSON.parse(m.body)));
      handlers.onConnect && handlers.onConnect();
    },
    onStompError: async () => {
      if (tokens.refreshToken && await silentRefresh()) return;   // yeni jetonla kendisi yeniden bağlanır
      client.deactivate();                                         // oturum geçersiz: denemeyi kes
      handlers.onAuthLost && handlers.onAuthLost();
    },
  });
  client.activate();
  return { close() { client.deactivate(); } };
}
