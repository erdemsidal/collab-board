// CollabBoard API katmanı
// -----------------------------------------------------------------------------
// Spring Boot backend'e bağlanmak için TEK yapılandırma noktası burasıdır.
//   configure({ baseUrl: "http://localhost:8080" })  -> gerçek backend
//   baseUrl boş bırakılırsa                          -> yerleşik MOCK veri
// Uç adresleri brief'teki sözleşmeyle birebir aynıdır:
//   POST /api/auth/register | /api/auth/login | /api/auth/refresh
//   GET  /api/boards | POST /api/boards | GET /api/boards/{id}
//   GET  /api/boards/{id}/activity
//   GET/POST/DELETE /api/boards/{id}/members
//   WS   /ws  ->  /topic/board.{id} , /topic/board.{id}/presence,
//                 /user/queue/errors , /app/board/{id}/ops
// -----------------------------------------------------------------------------

let cfg = { baseUrl: "", wsPath: "/ws" };
let tokens = { accessToken: null, refreshToken: null };

export function configure(next = {}) {
  cfg = { ...cfg, ...next };
  if (typeof cfg.baseUrl === "string") cfg.baseUrl = cfg.baseUrl.trim().replace(/\/$/, "");
}
export function isMock() { return !cfg.baseUrl; }
export function setTokens(t) { tokens = { ...tokens, ...t }; }
export function getTokens() { return tokens; }

async function req(path, { method = "GET", body, auth = true } = {}) {
  const headers = { "Content-Type": "application/json" };
  if (auth && tokens.accessToken) headers.Authorization = "Bearer " + tokens.accessToken;
  let res = await fetch(cfg.baseUrl + path, {
    method, headers, body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (res.status === 401 && auth && tokens.refreshToken) {
    const ok = await silentRefresh();
    if (ok) return req(path, { method, body, auth });
  }
  if (!res.ok) {
    let msg = "İstek başarısız (" + res.status + ")";
    try { const j = await res.json(); msg = j.message || j.error || msg; } catch (_) {}
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
  login: (email, password) => isMock()
    ? mock.login(email, password)
    : req("/api/auth/login", { method: "POST", body: { email, password }, auth: false }),

  register: (p) => isMock()
    ? mock.register(p)
    : req("/api/auth/register", { method: "POST", body: p, auth: false }),

  workspaces: () => isMock() ? mock.workspaces() : req("/api/workspaces"),
  boards: () => isMock() ? mock.boards() : req("/api/boards"),
  createBoard: (name, workspaceId) => isMock()
    ? mock.createBoard(name, workspaceId)
    : req("/api/boards", { method: "POST", body: { name, workspaceId } }),
  createWorkspace: (name) => isMock()
    ? mock.createWorkspace(name)
    : req("/api/workspaces", { method: "POST", body: { name } }),
  renameWorkspace: (id, name) => isMock()
    ? mock.renameWorkspace(id, name)
    : req("/api/workspaces/" + id, { method: "PATCH", body: { name } }),
  deleteWorkspace: (id) => isMock()
    ? mock.deleteWorkspace(id)
    : req("/api/workspaces/" + id, { method: "DELETE" }),

  board: (id) => isMock() ? mock.board(id) : req("/api/boards/" + id),
  activity: (id) => isMock() ? mock.activity(id) : req("/api/boards/" + id + "/activity"),
  members: (id) => isMock() ? mock.members(id) : req("/api/boards/" + id + "/members"),
  invite: (id, email, role) => isMock()
    ? mock.invite(id, email, role)
    : req("/api/boards/" + id + "/members", { method: "POST", body: { email, role } }),
  removeMember: (id, userId) => isMock()
    ? mock.removeMember(id, userId)
    : req("/api/boards/" + id + "/members/" + userId, { method: "DELETE" }),
};

/* ---------------------------- Gerçek zamanlı ----------------------------- */
// Gerçek modda @stomp/stompjs CDN'den yüklenir; token STOMP CONNECT
// çerçevesinde taşınır (URL'de değil).

export function connectRealtime(boardId, handlers = {}) {
  if (isMock()) return mock.connect(boardId, handlers);
  const Stomp = window.StompJs;
  if (!Stomp) { handlers.onStatus && handlers.onStatus("down"); return { close() {} }; }
  const url = cfg.baseUrl.replace(/^http/, "ws") + cfg.wsPath;
  const client = new Stomp.Client({
    brokerURL: url,
    connectHeaders: { Authorization: "Bearer " + tokens.accessToken },
    reconnectDelay: 3000,
    onConnect: () => {
      handlers.onStatus && handlers.onStatus("up");
      client.subscribe("/topic/board." + boardId, (m) => handlers.onOp && handlers.onOp(JSON.parse(m.body)));
      client.subscribe("/topic/board." + boardId + "/presence", (m) => handlers.onPresence && handlers.onPresence(JSON.parse(m.body)));
      client.subscribe("/user/queue/errors", (m) => handlers.onReject && handlers.onReject(JSON.parse(m.body)));
    },
    onWebSocketClose: () => handlers.onStatus && handlers.onStatus("down"),
    onStompError: () => handlers.onStatus && handlers.onStatus("down"),
  });
  client.activate();
  return {
    send(op) { if (client.connected) client.publish({ destination: "/app/board/" + boardId + "/ops", body: JSON.stringify(op) }); },
    close() { client.deactivate(); },
  };
}

/* ------------------------------- MOCK veri ------------------------------- */

const wait = (ms = 200) => new Promise((r) => setTimeout(r, ms));
let seq = 100;
const nid = () => ++seq;

const db = {
  me: { id: 1, firstName: "Erdem", lastName: "Sidal", email: "erdem@collabboard.app" },
  workspaces: [
    { id: 1, name: "Kişisel Alan", slug: "kisisel", role: "OWNER" },
    { id: 2, name: "Acme Yazılım", slug: "acme", role: "ADMIN" },
    { id: 3, name: "Nova Tasarım", slug: "nova", role: "GUEST" },
  ],
  boards: [
    { id: 11, name: "Haftalık Plan", workspaceId: 1, role: "OWNER", cardCount: 7, memberCount: 1 },
    { id: 12, name: "Ürün Yol Haritası", workspaceId: 2, role: "OWNER", cardCount: 9, memberCount: 5 },
    { id: 13, name: "Altyapı Göçü", workspaceId: 2, role: "EDITOR", cardCount: 6, memberCount: 4 },
    { id: 14, name: "Pazarlama Takvimi", workspaceId: 2, role: "VIEWER", cardCount: 4, memberCount: 3 },
  ],
  columns: {
    12: [
      { id: 21, name: "Yapılacaklar", position: 0, cards: [
        { id: 31, title: "Kart detay ekranını tasarla", version: 3, labels: ["tasarım"] },
        { id: 32, title: "Mobil kolon kaydırması", version: 1, labels: ["mobil"] },
        { id: 33, title: "Boş durum metinlerini yaz", version: 1, labels: [] },
      ] },
      { id: 22, name: "Devam Ediyor", position: 1, cards: [
        { id: 34, title: "Çalışma alanı seçici", version: 5, labels: ["ön yüz"] },
        { id: 35, title: "Presence avatar listesi", version: 2, labels: ["canlı"] },
      ] },
      { id: 23, name: "İncelemede", position: 2, cards: [
        { id: 36, title: "Redis Pub/Sub köprüsü", version: 8, labels: ["altyapı"] },
      ] },
      { id: 24, name: "Tamam", position: 3, cards: [
        { id: 37, title: "JWT sessiz yenileme", version: 4, labels: ["güvenlik"] },
        { id: 38, title: "Pano geçmişi (audit)", version: 2, labels: [] },
        { id: 39, title: "Rol tabanlı erişim", version: 6, labels: ["erişim"] },
      ] },
    ],
  },
  members: {
    12: [
      { id: 1, firstName: "Erdem", lastName: "Sidal", email: "erdem@collabboard.app", role: "OWNER" },
      { id: 2, firstName: "Ayşe", lastName: "Demir", email: "ayse@acme.co", role: "EDITOR" },
      { id: 3, firstName: "Mert", lastName: "Kaya", email: "mert@acme.co", role: "EDITOR" },
      { id: 4, firstName: "Selin", lastName: "Yıldız", email: "selin@acme.co", role: "VIEWER" },
      { id: 5, firstName: "Can", lastName: "Öztürk", email: "can@acme.co", role: "VIEWER" },
    ],
  },
  activity: {
    12: [
      { id: 1, actorName: "Ayşe Demir", type: "MOVE_CARD", description: "“Presence avatar listesi” kartını Devam Ediyor kolonuna taşıdı", createdAt: Date.now() - 1000 * 60 * 2 },
      { id: 2, actorName: "Mert Kaya", type: "ADD_CARD", description: "“Boş durum metinlerini yaz” kartını ekledi", createdAt: Date.now() - 1000 * 60 * 14 },
      { id: 3, actorName: "Erdem Sidal", type: "EDIT_CARD", description: "“Kart detay ekranını tasarla” kartını düzenledi", createdAt: Date.now() - 1000 * 60 * 41 },
      { id: 4, actorName: "Selin Yıldız", type: "JOIN", description: "panoya katıldı", createdAt: Date.now() - 1000 * 60 * 63 },
      { id: 5, actorName: "Ayşe Demir", type: "DELETE_CARD", description: "“Eski onboarding akışı” kartını sildi", createdAt: Date.now() - 1000 * 60 * 120 },
    ],
  },
};

function clone(x) { return JSON.parse(JSON.stringify(x)); }

const mock = {
  async login(email, password) {
    await wait(320);
    if (!email || !password) throw new Error("E-posta ve şifre zorunludur");
    if (password.length < 4) throw new Error("E-posta veya şifre hatalı");
    return { accessToken: "mock-access", refreshToken: "mock-refresh", user: clone(db.me) };
  },
  async register(p) {
    await wait(320);
    if (!p.password || p.password.length < 8) { const e = new Error("Şifre en az 8 karakter olmalı"); e.field = "password"; throw e; }
    db.me = { id: 1, firstName: p.firstName, lastName: p.lastName, email: p.email };
    return { accessToken: "mock-access", refreshToken: "mock-refresh", user: clone(db.me) };
  },
  async workspaces() { await wait(160); return clone(db.workspaces); },
  async boards() { await wait(160); return clone(db.boards); },
  async createBoard(name, workspaceId) {
    await wait(200);
    const b = { id: nid(), name, workspaceId: workspaceId || 1, role: "OWNER", cardCount: 0, memberCount: 1 };
    db.boards.push(b);
    db.columns[b.id] = [
      { id: nid(), name: "Yapılacaklar", position: 0, cards: [] },
      { id: nid(), name: "Devam Ediyor", position: 1, cards: [] },
      { id: nid(), name: "Tamam", position: 2, cards: [] },
    ];
    db.members[b.id] = [{ ...clone(db.me), role: "OWNER" }];
    db.activity[b.id] = [];
    return clone(b);
  },
  async createWorkspace(name) {
    await wait(200);
    const w = { id: nid(), name, slug: name.toLowerCase().replace(/\s+/g, "-"), role: "OWNER" };
    db.workspaces.push(w); return clone(w);
  },
  async renameWorkspace(id, name) {
    await wait(180);
    const w = db.workspaces.find((x) => String(x.id) === String(id));
    if (!w) throw new Error("Çalışma alanı bulunamadı");
    if (w.role !== "OWNER" && w.role !== "ADMIN") throw new Error("Bu alanı düzenleme yetkin yok");
    w.name = name;
    w.slug = name.toLowerCase().replace(/\s+/g, "-");
    return clone(w);
  },
  async deleteWorkspace(id) {
    await wait(220);
    const w = db.workspaces.find((x) => String(x.id) === String(id));
    if (!w) throw new Error("Çalışma alanı bulunamadı");
    if (w.role !== "OWNER" && w.role !== "ADMIN") throw new Error("Bu alanı silme yetkin yok");
    db.workspaces = db.workspaces.filter((x) => String(x.id) !== String(id));
    db.boards = db.boards.filter((b) => String(b.workspaceId) !== String(id));
    return null;
  },
  async board(id) {
    await wait(220);
    const b = db.boards.find((x) => String(x.id) === String(id));
    if (!b) throw new Error("Pano bulunamadı");
    seed(b);
    return { ...clone(b), columns: clone(db.columns[id]) };
  },
  async activity(id) { await wait(180); const b = db.boards.find((x) => String(x.id) === String(id)); if (b) seed(b); return clone(db.activity[id] || []); },
  async members(id) { await wait(180); const b = db.boards.find((x) => String(x.id) === String(id)); if (b) seed(b); return clone(db.members[id] || []); },
  async invite(id, email, role) {
    await wait(280);
    if (!/.+@.+\..+/.test(email)) throw new Error("Geçerli bir e-posta girin");
    const list = db.members[id] || (db.members[id] = []);
    if (list.some((m) => m.email === email)) throw new Error("Bu kişi zaten üye");
    const nameGuess = email.split("@")[0].replace(/[._]/g, " ");
    const parts = nameGuess.split(" ");
    const m = { id: nid(), firstName: cap(parts[0] || "Yeni"), lastName: cap(parts[1] || "Üye"), email, role };
    list.push(m); return clone(m);
  },
  async removeMember(id, userId) {
    await wait(200);
    db.members[id] = (db.members[id] || []).filter((m) => String(m.id) !== String(userId));
    return null;
  },
  // Mock gerçek zamanlı kanal: yerel olayları yayınlar, sahte bir ekip
  // arkadaşının hareketlerini üretir, çakışma reddini simüle eder.
  connect(boardId, handlers) {
    let alive = true;
    const others = [
      { id: 2, name: "Ayşe Demir" },
      { id: 3, name: "Mert Kaya" },
    ];
    setTimeout(() => alive && handlers.onStatus && handlers.onStatus("up"), 420);
    setTimeout(() => alive && handlers.onPresence && handlers.onPresence(
      [{ id: 1, name: "Erdem Sidal" }, ...others]), 520);
    return {
      alive: () => alive,
      others,
      send(op) { /* iyimser arayüz: sunucu yankısına gerek yok */ },
      close() { alive = false; },
    };
  },
  db,
};

// Mock: 12 dışındaki panolar ilk açılışta makul içerikle doldurulur.
const SEED_TITLES = [
  ["Sprint hedeflerini yaz", "Toplantı notlarını derle", "Faturaları gönder"],
  ["Tasarım incelemesi", "Migration betiği"],
  ["Haftalık rapor"],
];
const SEED_PEOPLE = [
  { firstName: "Ayşe", lastName: "Demir", email: "ayse@acme.co", role: "EDITOR" },
  { firstName: "Mert", lastName: "Kaya", email: "mert@acme.co", role: "EDITOR" },
  { firstName: "Selin", lastName: "Yıldız", email: "selin@acme.co", role: "VIEWER" },
  { firstName: "Can", lastName: "Öztürk", email: "can@acme.co", role: "VIEWER" },
];
function seed(b) {
  const id = b.id;
  if (!db.columns[id]) {
    const names = ["Yapılacaklar", "Devam Ediyor", "Tamam"];
    db.columns[id] = names.map((name, i) => ({
      id: nid(), name, position: i,
      cards: (SEED_TITLES[i] || []).slice(0, Math.max(0, Math.min(3, b.cardCount - i))).map((t) => ({ id: nid(), title: t, version: 1, labels: [] })),
    }));
  }
  if (!db.members[id]) {
    const list = [{ ...clone(db.me), role: b.role === "OWNER" ? "OWNER" : "EDITOR" }];
    for (let i = 0; i < Math.max(0, (b.memberCount || 1) - 1); i++) {
      const p = SEED_PEOPLE[i % SEED_PEOPLE.length];
      list.push({ id: nid(), ...p });
    }
    db.members[id] = list;
  }
  if (!db.activity[id]) db.activity[id] = [];
}

function cap(s) { return s ? s[0].toUpperCase() + s.slice(1) : s; }
export { mock };
