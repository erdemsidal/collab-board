// Sturnio — tanıtım sayfası ("A · Gece")
// -----------------------------------------------------------------------------
// Oturumu olmayan ziyaretçinin ilk ekranı. index.html'den ayrı bir modül:
// ana betik küçük kalır, oturumu açık kullanıcı bu dosyayı hiç indirmez.
//
// Sunucuya hiç dokunmaz. Hero'daki pano ve bölüm görselleri birer canlandırmadır
// (ekip ve kartlar uydurma), ama gösterdikleri davranışların hepsi uygulamada
// gerçekten var: canlı taşıma, canlı imleç, WIP limiti, geçmiş, akış ölçümü,
// onaylı davet. Sayfada sosyal kanıt yok (müşteri logosu, yorum, kullanıcı
// sayısı) ve şimdilik geliştirici adı ya da GitHub bağlantısı da yok.
//
// Hareket kuralları: yalnızca transform/opacity; ekran dışındaki ya da arka
// plandaki sekmedeki döngüler bekler; prefers-reduced-motion açıksa hiçbir şey
// kıpırdamaz ve her görsel anlamlı tek bir kare gösterir (tasarımdaki kare).
//
// Metinlerin hepsi i18n.js'te (landing.*). Üst çubukta ve mobil menüde dil
// düğmesi var: seçilince sayfa aynı kaydırma konumunda yeni dille yeniden
// çizilir; seçim girişten sonra hesaba yazılır (ADR 0020).
// -----------------------------------------------------------------------------

import { t, BRAND, BRAND_MARK, langSwitch, setLocale, ago, unit } from "./i18n.js";

const REDUCE = typeof matchMedia === "function" && matchMedia("(prefers-reduced-motion: reduce)").matches;
const FONT_CSS = "https://fonts.googleapis.com/css2?family=Inter:wght@400..800&family=JetBrains+Mono:wght@500;600&display=swap";
const STOP = Symbol("dur");

/* ── Küçük yardımcılar ── */
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

/* Canlandırmanın ekibi (uydurma). Renkler koyu zemin için. */
const PEOPLE = {
  ayse:  { name: "Ayşe",  initials: "AY", color: "#B197FC", rest: [0.17, 0.84] },
  mert:  { name: "Mert",  initials: "MK", color: "#4DABF7", rest: [0.76, 0.8] },
  deniz: { name: "Deniz", initials: "DA", color: "#3DD68C" },
};
const av = (key, cls = "") =>
  `<span class="lp-av ${cls}" data-who="${key}" style="--c:${PEOPLE[key].color}">${PEOPLE[key].initials}</span>`;

/* ── İkonlar ── */
const MARK = `<svg viewBox="0 0 64 64" aria-hidden="true"><path fill-rule="evenodd" d="${BRAND_MARK}"/></svg>`;
const stroke = (d, size = 16, w = 2) =>
  `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="${w}" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${d}</svg>`;
const ARROW = stroke(`<path d="M5 12h14M13 6l6 6-6 6"/>`, 16, 2.2);
const HISTORY = stroke(`<path d="M3 12a9 9 0 1 0 3-6.7L3 8"/><path d="M3 3v5h5"/><path d="M12 7v5l3 2"/>`);
const CHEVRON = stroke(`<path d="M6 9l6 6 6-6"/>`, 18);
const CHECK = stroke(`<path d="M5 12.5l4.5 4.5L19 7.5"/>`, 15, 2.4);
const MENU = `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path class="l1" d="M4 7h16"/><path class="l2" d="M4 12h16"/><path class="l3" d="M4 17h16"/></svg>`;
const POINTER = (color) =>
  `<svg width="16" height="18" viewBox="0 0 18 20" aria-hidden="true"><path d="M1.5 1.5 16 10.2 9.4 11.7 6.2 18.2Z" fill="${color}" stroke="#0A0C10" stroke-width="1.5" stroke-linejoin="round"/></svg>`;

/* ── İçerik ── (metinler i18n.js'te: landing.*; burada yalnızca yapı, renk ve ikon) */
const NAV = [["lp-ozellikler", "features"], ["lp-nasil", "how"], ["lp-roller", "roles"], ["lp-hakkinda", "about"], ["lp-sss", "faq"]];

const FEATURES = [
  { id: "live", color: "#FF7A1A", icon: "M13 2L4 14h7l-1 8 9-12h-7z" },
  { id: "cursors", color: "#B197FC", icon: "M4 3l14 8-6 1.5L9 19z" },
  { id: "conflict", color: "#FF9B9B", icon: "M12 3l8 4v5c0 4.5-3.4 8.2-8 9-4.6-.8-8-4.5-8-9V7zM9 12l2 2 4-4" },
  { id: "history", color: "#3DD68C", icon: "M3 12a9 9 0 1 0 3-6.7L3 8M3 3v5h5M12 7v5l3 2" },
  { id: "flow", color: "#4DABF7", icon: "M4 20V10M10 20V4M16 20v-7M22 20H2" },
  { id: "wip", color: "#FF7A1A", icon: "M4 6h16M4 12h10M4 18h6" },
];
/** Akış paneli: kolon, çubuk boyu, renk, ortalama bekleme (gün; bitmiş kolonda yok). */
const FLOW = [
  ["todo", "30%", "#4A5466", 0.8],
  ["doing", "55%", "#4DABF7", 1.9],
  ["review", "88%", "#FF9B9B", 3.4],
  ["done", "12%", "#3DD68C", null],
];
/** Yaşlanan iş: kart, kolon, beklediği gün, renk. */
const AGING = [
  ["ode", "review", 6, "#FF9B9B"],
  ["api", "doing", 3, "#FFD27A"],
  ["onb", "todo", 1, "#A9B1C0"],
];
const STEPS = [["01", "s1"], ["02", "s2"], ["03", "s3"]];
/** Yetki satırları: Yönetici, Editör, İzleyici. */
const ROLES = [
  ["live", 1, 1, 1],
  ["edit", 1, 1, 0],
  ["manage", 1, 0, 0],
  ["history", 1, 1, 1],
];
const PRINCIPLES = ["p1", "p2", "p3", "p4"];
const FAQ = ["q1", "q2", "q3", "q4", "q5"];

/* ── Hero panosu: kartlar ve senaryo ── */
/** Kartlar; tag: son tarih rozetinin türü (metni landing.demo.tags). */
const CARDS = {
  onb:    { tag: "date" },
  ode:    { tag: "soon" },
  dark:   {},
  api:    { tag: "late" },
  push:   {},
  giris:  {},
  fatura: {},          // canlı yazılarak eklenir
};
const cardTitle = (id) => t(`landing.demo.cards.${id}`);
const COLS = ["todo", "doing", "done"];
const WIP = 3;                              // ortadaki kolonun limiti: "2 / 3"
const START = [["onb", "ode", "dark"], ["api", "push"], ["giris"]];
/** Hareketsiz kare = tasarımdaki an: Ayşe "Ödeme sayfası"nı taşıyor, yeri boş duruyor. */
const STILL = [["onb", "ph", "dark"], ["api", "push"], ["giris"]];
/** feed: akış şeridindeki satır, toast: [başlık, ayrıntı] (landing.demo.feed / .toast anahtarları). */
const SCRIPT = [
  { kind: "move", who: "ayse", id: "ode", to: 1, at: 2, feed: "moveOde", toast: ["moved", "moveOde"] },
  { kind: "move", who: "mert", id: "push", to: 2, at: 0, feed: "movePush", toast: ["moved", "movePush"] },
  { kind: "add", who: "mert", id: "fatura", to: 0, at: 0, feed: "addFatura", toast: ["added", "addFatura"] },
  { kind: "move", who: "ayse", id: "onb", to: 1, at: 2, feed: "moveOnb", toast: ["moved", "moveOnb"] },
];
const feedText = (who, key) => t(`landing.demo.feed.${key}`, { who: PEOPLE[who].name });
/** Demo ekibinin adları: metinlerde {ayse}, {mert}, {deniz}. */
const names = () => Object.fromEntries(Object.entries(PEOPLE).map(([k, p]) => [k, p.name]));
/** Senaryonun geçtiği bütün durumlar: pano yüksekliği en kalabalık kolona göre bir kez ayarlanır. */
const STATES = (() => {
  const out = [START, STILL];
  let cur = START;
  for (const s of SCRIPT) {
    cur = cur.map((c) => c.filter((id) => id !== s.id));
    cur[s.to].splice(s.at, 0, s.id);
    out.push(cur);
  }
  return out;
})();

/* ── Geçmiş görseli: üç an (metinleri landing.past.times.<an>When / <an>What) ── */
const TIMES = {
  past: { p: 0.28, cols: [["onb"], ["api", "push"], ["giris"]], flash: "giris" },
  mid:  { p: 0.62, cols: [["onb", "api"], ["push"], ["giris"]], flash: "api" },
  now:  { p: 1, cols: [["onb", "api"], ["push"], ["giris"]] },
};
const TIME_ORDER = ["mid", "now", "past"];

/**
 * Tanıtım sayfasını root'a çizer.
 * onLogin / onRegister: giriş ekranını ilgili modda açar (index.html verir).
 * onForgot (isteğe bağlı): "Şifremi unuttum"; verilmezse onLogin çağrılır.
 * Dönen fonksiyon sayfayı söker; çağrılmasa da root başka bir ekranla
 * doldurulunca zamanlayıcılar ve dinleyiciler kendiliğinden durur.
 */
export function renderLanding(root, options = {}) {
  const { onLogin, onRegister, onForgot } = options;
  ensureFonts();
  root.innerHTML = template();
  const page = root.querySelector(".lp");
  const cleanups = [];
  let stopped = false;
  const alive = () => !stopped && page.isConnected;
  const stop = () => {
    if (stopped) return;
    stopped = true;
    for (const fn of cleanups.splice(0)) { try { fn(); } catch (_) { /* söküm sırasında hata önemsiz */ } }
  };
  const listen = (target, type, fn, opts) => {
    target.addEventListener(type, fn, opts);
    cleanups.push(() => target.removeEventListener(type, fn, opts));
  };
  // index.html root'u başka bir ekranla doldurunca her şey dursun.
  if (typeof MutationObserver === "function") {
    const mo = new MutationObserver(() => { if (!page.isConnected) stop(); });
    mo.observe(root, { childList: true });
    cleanups.push(() => mo.disconnect());
  }

  const menu = setupHeader(page, listen, alive, stop);

  page.addEventListener("click", (e) => {
    // Dil: sayfa aynı yerde, yeni dille yeniden çizilir. Mobil menüden seçildiyse menü açık kalır.
    const lang = e.target.closest("[data-lang]");
    if (lang) {
      // Tıklama yeni sayfanın "dışarı tıklandı, menüyü kapat" dinleyicisine ulaşmasın.
      e.stopPropagation();
      if (lang.getAttribute("aria-pressed") === "true") return;
      const inMenu = !!lang.closest(".lp-menu");
      const y = window.scrollY;
      stop();
      setLocale(lang.dataset.lang, { pick: true });
      renderLanding(root, options);
      window.scrollTo(0, y);
      if (inMenu) root.querySelector(".lp-menu-btn")?.click();
      root.querySelector(`${inMenu ? ".lp-menu" : ".lp-header-acts"} [data-lang="${lang.dataset.lang}"]`)?.focus({ preventScroll: true });
      return;
    }
    const act = e.target.closest("[data-act]");
    if (act) {
      e.preventDefault();
      const fn = { login: onLogin, register: onRegister, forgot: onForgot || onLogin }[act.dataset.act];
      // Giriş ekranı kaydırılmış açılmasın; sayfa söküldüğü için döngüler de durur.
      stop();
      window.scrollTo(0, 0);
      fn?.();
      return;
    }
    const go = e.target.closest("[data-scroll]");
    if (!go || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
    // Bölüme kaydır; adres çubuğuna # yazılmaz (?verify / ?reset akışları adresi okur).
    e.preventDefault();
    menu.close();
    const id = go.dataset.scroll;
    const behavior = REDUCE ? "auto" : "smooth";
    if (id === "lp-top") window.scrollTo({ top: 0, behavior });
    const target = page.querySelector("#" + id);
    if (!target) return;
    if (id !== "lp-top") target.scrollIntoView({ behavior, block: "start" });
    // Klavyeyle gelen, bölümün başından devam etsin.
    if (!target.hasAttribute("tabindex")) target.setAttribute("tabindex", "-1");
    target.focus({ preventScroll: true });
  });

  if (!REDUCE && typeof IntersectionObserver === "function") {
    page.classList.add("has-io");
    watchReveal(page, cleanups);
    watchLoops(page, listen, cleanups);
  }
  startHero(page, alive, cleanups);
  startTimeTravel(page, alive, cleanups);
  return stop;
}

/** Inter + JetBrains Mono; sayfa ilk açıldığında bir kez eklenir. */
function ensureFonts() {
  if (document.getElementById("lp-fonts")) return;
  const add = (rel, href, extra = {}) => {
    const l = document.createElement("link");
    l.rel = rel; l.href = href;
    Object.assign(l, extra);
    document.head.appendChild(l);
  };
  add("preconnect", "https://fonts.googleapis.com");
  add("preconnect", "https://fonts.gstatic.com", { crossOrigin: "anonymous" });
  add("stylesheet", FONT_CSS, { id: "lp-fonts" });
}

/* ══════════════════ Üst çubuk ve mobil menü ══════════════════ */
function setupHeader(page, listen, alive, stop) {
  const header = page.querySelector(".lp-header");
  const btn = header.querySelector(".lp-menu-btn");
  const isOpen = () => header.classList.contains("is-menu");
  const set = (open) => {
    header.classList.toggle("is-menu", open);
    btn.setAttribute("aria-expanded", String(open));
    btn.setAttribute("aria-label", t(open ? "landing.closeMenu" : "landing.openMenu"));
  };
  listen(btn, "click", () => set(!isOpen()));
  listen(document, "keydown", (e) => {
    if (e.key === "Escape" && isOpen()) { set(false); btn.focus(); }
  });
  listen(document, "click", (e) => { if (isOpen() && !header.contains(e.target)) set(false); });
  if (typeof matchMedia === "function") {
    const wide = matchMedia("(min-width: 921px)");
    listen(wide, "change", () => { if (wide.matches) set(false); });
  }

  // Hero'nun üstünde zeminle bir; aşağı inince buzlu, hafif gölgeli bir şerit.
  const onScroll = () => {
    if (!alive()) return stop();
    header.classList.toggle("is-scrolled", window.scrollY > 8);
  };
  listen(window, "scroll", onScroll, { passive: true });
  onScroll();
  return { close: () => set(false) };
}

/**
 * Bölümler görünür olunca bir kez belirir (.is-in). Animasyon "backwards"
 * dolgulu: bittiğinde hover gibi kendi transform'ları serbest kalır.
 */
function watchReveal(page, cleanups) {
  const io = new IntersectionObserver((entries) => {
    for (const en of entries) {
      if (!en.isIntersecting) continue;
      en.target.classList.add("is-in");
      io.unobserve(en.target);
    }
  }, { rootMargin: "0px 0px -40px 0px" });
  page.querySelectorAll(".lp-reveal").forEach((el) => io.observe(el));
  cleanups.push(() => io.disconnect());
}

/**
 * CSS döngüleri (.lp-loop içindeki nabız, çubuk, davet…) yalnızca ekrandayken
 * tanımlıdır (.lp-run); sekme arka plandayken hepsi durur (.is-hidden).
 */
function watchLoops(page, listen, cleanups) {
  const io = new IntersectionObserver((entries) => {
    for (const en of entries) en.target.classList.toggle("lp-run", en.isIntersecting);
  });
  page.querySelectorAll(".lp-loop").forEach((el) => io.observe(el));
  cleanups.push(() => io.disconnect());
  const vis = () => page.classList.toggle("is-hidden", document.visibilityState === "hidden");
  listen(document, "visibilitychange", vis);
  vis();
}

/**
 * Duraklatılabilir zaman: verilen öğelerden biri ekrandayken ve sekme
 * öndeyken akar; değilse sleep() bekler. Sayfa sökülünce STOP fırlatır.
 */
function makeGate(els, alive, cleanups) {
  let inView = false, hidden = document.visibilityState === "hidden", dead = false;
  const waiters = [];
  const paused = () => !inView || hidden;
  const wake = () => { if (dead || !paused()) waiters.splice(0).forEach((r) => r()); };
  const seen = new Set();
  let io = null;
  if (typeof IntersectionObserver === "function") {
    io = new IntersectionObserver((entries) => {
      for (const en of entries) en.isIntersecting ? seen.add(en.target) : seen.delete(en.target);
      inView = seen.size > 0;
      wake();
    });
    els.forEach((el) => io.observe(el));
  } else {
    inView = true;
  }
  const onVis = () => { hidden = document.visibilityState === "hidden"; wake(); };
  document.addEventListener("visibilitychange", onVis);
  cleanups.push(() => {
    dead = true;
    io?.disconnect();
    document.removeEventListener("visibilitychange", onVis);
    waiters.splice(0).forEach((r) => r());
  });
  return {
    async sleep(ms) {
      await new Promise((r) => setTimeout(r, ms));
      while (paused() && !dead) await new Promise((r) => waiters.push(r));
      if (dead || !alive()) throw STOP;
    },
  };
}

/** Döngüyü çalıştır; STOP sessizce biter, başka hata konsola düşer. */
function run(loop) {
  (async () => {
    try { for (;;) await loop(); }
    catch (e) { if (e !== STOP) console.error(e); }
  })();
}

/* ══════════════════ Sayfa iskeleti ══════════════════ */
function template() {
  const navLinks = NAV.map(([id, key]) => `<a href="#${id}" data-scroll="${id}">${t(`landing.nav.${key}`)}</a>`).join("");
  const year = new Date().getFullYear();
  const who = names();
  const signIn = t("common.signIn"), signUp = t("common.signUp");
  return `
<div class="lp">
  <a class="lp-skip" href="#lp-main" data-scroll="lp-main">${t("landing.skip")}</a>
  <header class="lp-header">
    <div class="lp-wrap lp-header-in">
      <a class="lp-brand" href="#lp-top" data-scroll="lp-top"><span class="lp-mark">${MARK}</span>${esc(BRAND)}</a>
      <nav class="lp-nav" aria-label="${t("landing.sections")}">${navLinks}</nav>
      <div class="lp-header-acts">
        ${langSwitch("lp-lang")}
        <button type="button" class="lp-btn lp-btn-ghost lp-btn-sm" data-act="login">${signIn}</button>
        <button type="button" class="lp-btn lp-btn-primary lp-btn-sm lp-hdr-reg" data-act="register">${signUp}</button>
        <button type="button" class="lp-menu-btn" aria-expanded="false" aria-controls="lp-menu" aria-label="${t("landing.openMenu")}">${MENU}</button>
      </div>
    </div>
    <div class="lp-menu" id="lp-menu">
      <nav class="lp-wrap lp-menu-in" aria-label="${t("landing.menu")}">
        ${navLinks}
        <div class="lp-menu-lang"><span>${t("lang.label")}</span>${langSwitch("lp-lang")}</div>
        <div class="lp-menu-acts">
          <button type="button" class="lp-btn lp-btn-primary" data-act="register">${signUp}</button>
          <button type="button" class="lp-btn lp-btn-ghost" data-act="login">${signIn}</button>
        </div>
      </nav>
    </div>
  </header>

  <main id="lp-main">
  <section class="lp-wrap lp-hero lp-loop" id="lp-top" aria-labelledby="lp-hero-h">
    <div class="lp-hero-copy">
      <h1 class="lp-rise" id="lp-hero-h" style="--d:.06s">${t("landing.hero.title1")}<br><span class="lp-accent">${t("landing.hero.title2")}</span></h1>
      <p class="lp-lead lp-rise" style="--d:.12s">${t("landing.hero.lead")}</p>
      <div class="lp-ctas lp-rise" style="--d:.18s">
        <button type="button" class="lp-btn lp-btn-primary lp-btn-lg" data-act="register">${signUp} ${ARROW}</button>
        <a class="lp-btn lp-btn-ghost lp-btn-lg" href="#lp-nasil" data-scroll="lp-nasil">${t("landing.hero.how")}</a>
      </div>
    </div>

    <div class="lp-hero-vis lp-rise" style="--d:.16s">
      <div class="lp-demo" role="img" aria-label="${esc(t("landing.demo.label", who))}">
        <div class="lp-win" aria-hidden="true">
          <div class="lp-win-bar">
            <span class="lp-win-title">${t("landing.demo.board")}</span>
            <span class="lp-role">${t("landing.demo.role")}</span>
            <span class="lp-presence">${av("ayse")}${av("mert")}${av("deniz")}</span>
            <span class="lp-live"><span class="lp-dot lp-pulse"></span>${t("landing.demo.live")}</span>
          </div>
          <div class="lp-board">
            ${COLS.map((c, i) => `<div class="lp-col"><div class="lp-col-h"><span>${t(`landing.demo.cols.${c}`)}</span><span class="lp-count${i === 1 ? " is-wip" : ""}">${i === 1 ? `2 / ${WIP}` : i ? "1" : "3"}</span></div></div>`).join("")}
            <div class="lp-layer"></div>
            <div class="lp-toasts"></div>
          </div>
        </div>
      </div>
    </div>
  </section>

  <section class="lp-wrap lp-ticker-sec" aria-label="${t("landing.ticker.label")}">
    <div class="lp-ticker">
      <span class="lp-ticker-l">${t("landing.ticker.title")}</span>
      <p class="lp-sr">${t("landing.ticker.sr")}</p>
      <ul class="lp-ticker-list" aria-hidden="true"></ul>
    </div>
  </section>

  <section class="lp-wrap lp-sec" id="lp-ozellikler" aria-labelledby="lp-h-ozellikler">
    <h2 class="lp-h2 lp-h2-xl lp-reveal" id="lp-h-ozellikler" style="max-width:720px">${t("landing.featuresTitle")}</h2>
    <div class="lp-feats lp-loop">
      ${FEATURES.map((f, i) => `
      <article class="lp-feat lp-reveal" style="--d:${(i % 3) * 0.07}s">
        <span class="lp-fi" style="--c:${f.color};--i:${i}">${stroke(`<path d="${f.icon}"/>`, 18)}</span>
        <h3>${t(`landing.features.${f.id}.title`)}</h3>
        <p>${t(`landing.features.${f.id}.text`)}</p>
      </article>`).join("")}
    </div>
  </section>

  <section class="lp-wrap lp-spot" aria-labelledby="lp-h-gecmis">
    <div class="lp-spot-copy lp-reveal">
      <h2 class="lp-h2 lp-h2-m" id="lp-h-gecmis">${t("landing.past.title")}</h2>
      <p class="lp-body">${t("landing.past.body")}</p>
      <ul class="lp-dash" style="--c:#3DD68C">
        <li>${t("landing.past.point1")}</li>
        <li>${t("landing.past.point2")}</li>
        <li>${esc(t("landing.past.point3"))}</li>
      </ul>
    </div>
    <div class="lp-spot-vis lp-reveal" style="--d:.1s">
      <div class="lp-panel lp-tt" data-step="mid" style="--p:${TIMES.mid.p}" role="img" aria-label="${esc(t("landing.past.label"))}">
        <div aria-hidden="true">
          <div class="lp-tt-h">
            <span class="lp-tt-ico">${HISTORY}</span>
            <span class="lp-tt-txt">${timeText("mid")}</span>
            <span class="lp-tt-now">${t("landing.past.backToNow")}</span>
          </div>
          <div class="lp-slider"><span class="lp-slider-track"></span><span class="lp-slider-fill"></span><span class="lp-slider-run"><span class="lp-slider-thumb"></span></span></div>
          <div class="lp-tt-board">
            <div class="lp-tt-col"><span class="lp-tt-col-h">${t("landing.demo.cols.todo")}</span><span class="lp-tt-card" data-tt="onb">${t("landing.past.cards.onb")}</span><span class="lp-tt-card" data-tt="api">${t("landing.past.cards.api")}</span></div>
            <div class="lp-tt-col"><span class="lp-tt-col-h">${t("landing.demo.cols.doing")}</span><span class="lp-tt-card" data-tt="push">${t("landing.past.cards.push")}</span></div>
            <div class="lp-tt-col is-del"><span class="lp-tt-col-h">${t("landing.demo.cols.review")} <span class="lp-tt-del">${t("landing.past.deleted")}</span></span><span class="lp-tt-card" data-tt="giris">${t("landing.past.cards.giris")}</span></div>
          </div>
        </div>
      </div>
    </div>
  </section>

  <section class="lp-wrap lp-spot lp-spot-rev" aria-labelledby="lp-h-akis">
    <div class="lp-spot-vis lp-reveal" style="--d:.1s">
      <div class="lp-panel lp-flow lp-loop" role="img" aria-label="${esc(t("landing.flow.label"))}">
        <div aria-hidden="true">
          <div class="lp-flow-h">${t("landing.flow.head")} <span class="lp-bn">${t("landing.flow.bottleneck", { column: t("landing.demo.cols.review") })}</span></div>
          <div class="lp-flow-rows">
            ${FLOW.map(([col, w, c, days], i) => `<div class="lp-flow-row"><span class="lp-flow-nm">${t(`landing.demo.cols.${col}`)}</span><span class="lp-flow-bar"><i style="--w:${w};--c:${c};--d:${i * 0.12}s"></i></span><span class="lp-flow-val">${days == null ? t("common.noValue") : unit("day", days, 1)}</span></div>`).join("")}
          </div>
          <div class="lp-aging">
            <p class="lp-aging-h">${t("landing.flow.aging")}</p>
            <div class="lp-aging-rows">
              ${AGING.map(([card, col, days, c]) => `<div class="lp-aging-row"><span class="nm">${cardTitle(card)}</span><span class="col">${t(`landing.demo.cols.${col}`)}</span><span class="d" style="color:${c}">${unit("day", days)}</span></div>`).join("")}
            </div>
          </div>
        </div>
      </div>
    </div>
    <div class="lp-spot-copy lp-reveal">
      <h2 class="lp-h2 lp-h2-m" id="lp-h-akis">${t("landing.flow.title")}</h2>
      <p class="lp-body">${t("landing.flow.body")}</p>
    </div>
  </section>

  <section class="lp-wrap lp-spot lp-spot-last" aria-labelledby="lp-h-ekip">
    <div class="lp-spot-copy lp-reveal">
      <h2 class="lp-h2 lp-h2-m" id="lp-h-ekip">${t("landing.team.title")}</h2>
      <p class="lp-body">${t("landing.team.body")}</p>
    </div>
    <div class="lp-spot-vis lp-team lp-loop lp-reveal" style="--d:.1s" role="img" aria-label="${esc(t("landing.team.label", who))}">
      <div class="lp-inv" aria-hidden="true">
        ${av("mert", "lp-av-lg")}
        <div class="lp-inv-b">
          <p class="lp-inv-t">${t("landing.team.inviteTitle")}</p>
          <p class="lp-inv-p">${t("landing.team.inviteBody", who)}</p>
          <div class="lp-inv-acts">
            <span class="lp-inv-btns"><span class="lp-inv-yes">${t("common.accept")}</span><span class="lp-inv-no">${t("common.decline")}</span></span>
            <span class="lp-inv-ok">${CHECK}${t("landing.team.accepted")}</span>
          </div>
        </div>
      </div>
      <div class="lp-acc" aria-hidden="true">
        <span class="lp-dot"></span>
        <span>${t("landing.team.acceptedBy", who)}</span>
      </div>
    </div>
  </section>

  <section class="lp-band" id="lp-nasil" aria-labelledby="lp-h-nasil">
    <div class="lp-wrap lp-band-in">
      <h2 class="lp-h2 lp-h2-l lp-reveal" id="lp-h-nasil">${t("landing.steps.title")}</h2>
      <ol class="lp-steps">
        ${STEPS.map(([n, key], i) => `<li class="lp-step lp-reveal" style="--d:${i * 0.08}s"><span class="lp-step-n">${n}</span><h3>${t(`landing.steps.${key}.title`)}</h3><p>${t(`landing.steps.${key}.text`)}</p></li>`).join("")}
      </ol>
    </div>
  </section>

  <section class="lp-wrap lp-sec lp-roles" id="lp-roller" aria-labelledby="lp-h-roller">
    <div class="lp-roles-copy lp-reveal">
      <h2 class="lp-h2 lp-h2-s" id="lp-h-roller">${t("landing.roles.title")}</h2>
      <p class="lp-body">${t("landing.roles.body")}</p>
    </div>
    <div class="lp-table-wrap lp-reveal" style="--d:.1s">
      <table class="lp-table">
        <caption class="lp-sr">${t("landing.roles.caption")}</caption>
        <thead><tr><td></td><th scope="col" style="color:#FFB37A">${t("role.name.OWNER")}</th><th scope="col" style="color:#9EC3FF">${t("role.name.EDITOR")}</th><th scope="col">${t("role.name.VIEWER")}</th></tr></thead>
        <tbody>
          ${ROLES.map(([what, ...v]) => `<tr><th scope="row">${t(`landing.roles.rows.${what}`)}</th>${v.map((y) => `<td class="${y ? "yes" : "no"}"><span aria-hidden="true">${y ? "✓" : t("common.noValue")}</span><span class="lp-sr">${t(y ? "landing.roles.yes" : "landing.roles.no")}</span></td>`).join("")}</tr>`).join("")}
        </tbody>
      </table>
    </div>
  </section>


  <section class="lp-band lp-band-top" id="lp-hakkinda" aria-labelledby="lp-h-hakkinda">
    <div class="lp-wrap lp-band-in lp-about">
      <div class="lp-about-copy lp-reveal">
        <h2 class="lp-h2 lp-h2-l" id="lp-h-hakkinda">${esc(t("landing.about.title"))}</h2>
        <p class="lp-body lp-body-l">${esc(t("landing.about.body"))}</p>
      </div>
      <div class="lp-principles">
        ${PRINCIPLES.map((key, i) => `<div class="lp-principle lp-reveal" style="--d:${i * 0.06}s"><h3>${t(`landing.about.${key}.title`)}</h3><p>${t(`landing.about.${key}.text`)}</p></div>`).join("")}
      </div>
    </div>
  </section>

  <section class="lp-wrap lp-sec lp-faq-sec" id="lp-sss" aria-labelledby="lp-h-sss">
    <h2 class="lp-h2 lp-h2-s lp-center lp-reveal" id="lp-h-sss">${t("landing.faq.title")}</h2>
    <div class="lp-faq">
      ${FAQ.map((key, i) => `<details class="lp-qa lp-reveal" style="--d:${i * 0.05}s"><summary><span>${t(`landing.faq.${key}.q`)}</span><span class="lp-qa-i">${CHEVRON}</span></summary><p>${t(`landing.faq.${key}.a`)}</p></details>`).join("")}
    </div>
  </section>

  <section class="lp-wrap lp-cta-sec" aria-labelledby="lp-h-cta">
    <div class="lp-cta lp-reveal">
      <div class="lp-cta-copy">
        <h2 class="lp-h2 lp-h2-s" id="lp-h-cta">${t("landing.cta.title")}</h2>
        <p>${t("landing.cta.body")}</p>
      </div>
      <div class="lp-cta-acts">
        <button type="button" class="lp-btn lp-btn-primary lp-btn-lg" data-act="register">${signUp}</button>
        <button type="button" class="lp-btn lp-btn-ghost lp-btn-lg" data-act="login">${signIn}</button>
      </div>
    </div>
  </section>
  </main>

  <footer class="lp-foot">
    <div class="lp-wrap lp-foot-in">
      <div class="lp-foot-brand">
        <p class="lp-foot-logo"><span class="lp-mark lp-mark-sm">${MARK}</span>${esc(BRAND)}</p>
        <p class="lp-foot-tag">${t("landing.foot.tagline")}</p>
      </div>
      <nav class="lp-foot-nav" aria-label="${t("landing.foot.product")}">
        <span class="lp-foot-h">${t("landing.foot.productH")}</span>
        <a href="#lp-ozellikler" data-scroll="lp-ozellikler">${t("landing.nav.features")}</a><a href="#lp-nasil" data-scroll="lp-nasil">${t("landing.nav.how")}</a><a href="#lp-roller" data-scroll="lp-roller">${t("landing.nav.roles")}</a>
      </nav>
      <nav class="lp-foot-nav" aria-label="${t("landing.foot.project")}">
        <span class="lp-foot-h">${t("landing.foot.projectH")}</span>
        <a href="#lp-hakkinda" data-scroll="lp-hakkinda">${t("landing.nav.about")}</a><a href="#lp-sss" data-scroll="lp-sss">${t("landing.nav.faq")}</a>
      </nav>
      <nav class="lp-foot-nav" aria-label="${t("landing.foot.account")}">
        <span class="lp-foot-h">${t("landing.foot.accountH")}</span>
        <button type="button" data-act="login">${signIn}</button><button type="button" data-act="register">${signUp}</button><button type="button" data-act="forgot">${t("auth.forgot")}</button>
      </nav>
    </div>
    <div class="lp-wrap lp-copy">© ${year} ${esc(BRAND)}</div>
  </footer>
</div>`;
}

/** Geçmiş görselinin üstündeki satır: "<b>21 Ekim, 14:32</b> · 38. hareket · ...". */
function timeText(key) {
  return `<b>${esc(t(`landing.past.times.${key}When`))}</b> · ${esc(t(`landing.past.times.${key}What`, names()))}`;
}

/* ══════════════════ Hero canlandırması ══════════════════
   Kolonlar yalnızca çerçeve; kartlar üstlerindeki tek bir katmanda mutlak
   konumlu durur ve yerleri yalnızca transform ile değişir. Bir kart taşınınca
   kolonlar yeniden hesaplanır, kartlar yeni yerlerine kayar — tarayıcı sayfayı
   yeniden dizmez. Sahne, pano ve akış şeridi ekran dışındayken ya da sekme
   arka plandayken bekler. */

function startHero(page, alive, cleanups) {
  const board = page.querySelector(".lp-board");
  const layer = board.querySelector(".lp-layer");
  const toasts = board.querySelector(".lp-toasts");
  const colEls = [...board.querySelectorAll(".lp-col")];
  const counts = colEls.map((c) => c.querySelector(".lp-count"));
  const faces = Object.fromEntries([...page.querySelectorAll(".lp-presence [data-who]")].map((e) => [e.dataset.who, e]));
  const ticker = makeTicker(page.querySelector(".lp-ticker-list"));

  const cards = {};
  for (const [id, c] of Object.entries(CARDS)) {
    const el = document.createElement("div");
    el.className = "lp-card is-gone";
    el.innerHTML = `<div class="lp-card-in"><span class="lp-card-t">${esc(cardTitle(id))}</span>${c.tag ? `<span class="lp-tag ${c.tag}">${esc(t(`landing.demo.tags.${c.tag}`))}</span>` : ""}</div>`;
    layer.appendChild(el);
    cards[id] = el;
  }
  const ph = document.createElement("div");
  ph.className = "lp-ph";
  layer.appendChild(ph);
  const cursors = {};
  for (const key of ["ayse", "mert"]) {
    const p = PEOPLE[key];
    const el = document.createElement("div");
    el.className = "lp-cursor";
    el.style.setProperty("--c", p.color);
    el.innerHTML = `${POINTER(p.color)}<span class="lp-cursor-l">${p.name}</span>`;
    layer.appendChild(el);
    cursors[key] = { el, label: el.querySelector(".lp-cursor-l") };
  }

  const S = { cols: START.map((c) => [...c]), pos: {}, carry: null, lifted: null, adding: null, phH: 0 };

  // ── Ölçü ──
  const pad = () => {
    const cs = getComputedStyle(colEls[0]);
    return { l: parseFloat(cs.paddingLeft) || 0, r: parseFloat(cs.paddingRight) || 0, b: parseFloat(cs.paddingBottom) || 0, gap: parseFloat(cs.rowGap) || 8 };
  };
  function geo() {
    const p = pad();
    return colEls.map((col) => {
      const head = col.firstElementChild;   // offsetParent: .lp-board
      return { x: col.offsetLeft + col.clientLeft + p.l, y: head.offsetTop + head.offsetHeight + p.gap,
               w: col.clientWidth - p.l - p.r, bottom: p.b + col.clientTop, gap: p.gap };
    });
  }
  /** Pano yüksekliği: senaryonun en kalabalık kolonu sığsın (telefonda başlıklar iki satıra iner). */
  function fit() {
    const g = geo();
    const h = {};
    for (const [id, el] of Object.entries(cards)) {
      el.style.width = g[0].w + "px";
      const t = el.querySelector(".lp-card-t");
      const typed = t.textContent;
      t.textContent = cardTitle(id);        // yazılırken ölçülse de tam hâliyle ölç
      h[id] = el.offsetHeight;
      t.textContent = typed;
    }
    let need = 0;
    for (const st of STATES) st.forEach((ids, ci) => {
      let y = g[ci].y;
      for (const id of ids) y += h[id === "ph" ? "ode" : id] + g[ci].gap;
      need = Math.max(need, y - g[ci].gap + g[ci].bottom);
    });
    const bs = getComputedStyle(board);
    const min = parseFloat(bs.getPropertyValue("--lp-board-min")) || 340;
    board.style.height = Math.max(min, Math.ceil(need + (parseFloat(bs.paddingBottom) || 0))) + "px";
  }
  const slots = (ids) => ids.filter((id) => id !== S.carry).length;   // yer tutucu, kart bırakılana dek sayılır
  function layout() {
    const g = geo();
    S.cols.forEach((ids, ci) => {
      const c = g[ci];
      let y = c.y;
      for (const id of ids) {
        const el = id === "ph" ? ph : cards[id];
        el.style.width = c.w + "px";
        if (id === "ph") el.style.height = S.phH + "px";
        const h = id === "ph" ? S.phH : el.offsetHeight;
        S.pos[id] = { x: c.x, y, w: c.w, h };
        // Taşınan kart hedefin biraz üstünde süzülür; bırakılınca yerine oturur.
        const lift = id === S.carry ? [-8, -12] : [0, 0];
        el.style.transform = `translate(${c.x + lift[0]}px, ${y + lift[1]}px)`;
        if (id !== "ph") {
          if (id !== S.adding) el.classList.remove("is-gone");
          el.classList.toggle("is-done", ci === 2);
        }
        y += h + c.gap;
      }
      const n = slots(ids);
      const text = ci === 1 ? `${n} / ${WIP}` : String(n);
      if (counts[ci].textContent !== text) {
        counts[ci].textContent = text;
        counts[ci].classList.remove("is-bump"); void counts[ci].offsetWidth; counts[ci].classList.add("is-bump");
      }
      if (ci === 1) counts[ci].classList.toggle("is-full", n >= WIP);
    });
    for (const id of Object.keys(cards)) {
      if (id !== S.lifted && !S.cols.some((c) => c.includes(id))) cards[id].classList.add("is-gone");
    }
    ph.classList.toggle("is-on", S.cols.some((c) => c.includes("ph")));
  }
  /** Geçişsiz yerleşim: ilk çizim, döngü başı ve pencere boyutu değişince. */
  function snap() {
    const els = [...layer.querySelectorAll(".lp-card, .lp-ph, .lp-cursor")];
    els.forEach((el) => { el.style.transition = "none"; });
    fit();
    layout();
    for (const key of Object.keys(cursors)) rest(key, false);
    void layer.offsetWidth;
    els.forEach((el) => { el.style.transition = ""; });
  }

  // ── İmleçler ──
  function point(key, x, y) {
    const c = cursors[key];
    c.el.style.transform = `translate(${Math.round(x)}px, ${Math.round(y)}px)`;
    // Sağ kenarda etiket imlecin soluna geçer, kesilmesin.
    c.el.classList.toggle("is-flip", x + 22 + c.label.offsetWidth > board.clientWidth - 6);
  }
  function rest(key, jitter = true) {
    const [fx, fy] = PEOPLE[key].rest;
    const j = () => (jitter ? (Math.random() - 0.5) * 0.08 : 0);
    point(key, (fx + j()) * board.clientWidth, Math.min(board.clientHeight - 40, (fy + j()) * board.clientHeight));
  }
  const grab = (id) => { const p = S.pos[id]; return [p.x + p.w * 0.72, p.y + Math.min(p.h * 0.62, 34)]; };
  const say = (key, text) => { cursors[key].label.textContent = text; };
  const active = (key, on) => faces[key]?.classList.toggle("is-active", on);

  /**
   * Bir kartın boyu değişince (yazı tipi geç yüklendi, yazılan başlık alt
   * satıra indi) pano yeniden ölçülür; kartlar üst üste binmez.
   * document.fonts.ready burada yetmez: yazı tipi CSS'i daha gelmeden çözülür.
   */
  function watchSizes(redo) {
    if (typeof ResizeObserver !== "function") return;
    let queued = false;
    const ro = new ResizeObserver(() => {
      if (queued) return;
      queued = true;
      requestAnimationFrame(() => { queued = false; if (alive()) redo(); });
    });
    for (const el of Object.values(cards)) ro.observe(el);
    cleanups.push(() => ro.disconnect());
  }

  function flash(id) {
    const el = cards[id];
    el.classList.remove("is-flash"); void el.offsetWidth; el.classList.add("is-flash");
  }
  /** [başlık, ayrıntı]: landing.demo.toast anahtarları. */
  function toast(key, [titleKey, bodyKey]) {
    const title = t(`landing.demo.toast.${titleKey}`, { who: PEOPLE[key].name });
    const body = t(`landing.demo.toast.${bodyKey}`);
    const el = document.createElement("div");
    el.className = "lp-toast";
    el.innerHTML = `${av(key)}<div><b>${esc(title)}</b><span>${esc(body)}</span></div>`;
    toasts.appendChild(el);
    while (toasts.children.length > 2) toasts.firstElementChild.remove();
    setTimeout(() => { el.classList.add("is-out"); setTimeout(() => el.remove(), 350); }, 2600);
  }
  const carrying = (key) => t("landing.demo.moving", { name: PEOPLE[key].name });

  // ── Hareketsiz kare (prefers-reduced-motion): tasarımdaki an ──
  function still() {
    S.cols = STILL.map((c) => [...c]);
    S.lifted = "ode";
    snap();
    const el = cards.ode;
    const g = geo();
    S.phH = el.offsetHeight;
    layout();
    const p = S.pos.ph;
    const x = Math.round((p.x + g[1].x) / 2 + 6), y = Math.round(p.y + 26);
    el.style.width = p.w + "px";
    el.style.setProperty("--who", PEOPLE.ayse.color);
    el.classList.add("is-lift");
    el.classList.remove("is-gone");
    el.style.transform = `translate(${x}px, ${y}px)`;
    say("ayse", carrying("ayse"));
    point("ayse", x + p.w * 0.86, y + Math.min(el.offsetHeight * 0.62, 34));
    rest("mert", false);
  }

  if (REDUCE) {
    ticker.seed([
      { who: "ayse", text: feedText("ayse", SCRIPT[0].feed), at: 0 },
      { who: "mert", text: feedText("mert", "addDark"), at: -12000 },
      { who: "deniz", text: feedText("deniz", "joined"), at: -60000 },
    ]);
    still();
    const ro = typeof ResizeObserver === "function" ? new ResizeObserver(() => alive() && still()) : null;
    ro?.observe(board.parentElement);
    cleanups.push(() => ro?.disconnect());
    watchSizes(still);
    return;
  }

  const gate = makeGate([board, page.querySelector(".lp-ticker")], alive, cleanups);
  const sleep = async (ms) => { await gate.sleep(ms); ticker.tick(ms); };

  // ── Hareketler ──
  async function move({ who, id, to, at, feed, toast: note }) {
    for (const k of Object.keys(cursors)) if (k !== who) rest(k);
    active(who, true);
    point(who, ...grab(id));
    await sleep(950);

    // Kartı tut: yerinde kesikli bir yer tutucu kalır, kart kalkar.
    const el = cards[id];
    const from = S.cols.findIndex((c) => c.includes(id));
    S.phH = S.pos[id].h;
    S.cols[from][S.cols[from].indexOf(id)] = "ph";
    S.lifted = id;
    ph.style.transition = "none";
    layout();
    void ph.offsetWidth;
    ph.style.transition = "";
    el.style.setProperty("--who", PEOPLE[who].color);
    el.classList.add("is-lift");
    say(who, carrying(who));
    point(who, ...grab(id));
    await sleep(320);

    // Taşı: hedef kolon yer açar, kart ve imleç birlikte süzülür.
    S.cols[to].splice(at, 0, id);
    S.carry = id;
    el.classList.add("is-carry");
    cursors[who].el.classList.add("is-carry");
    layout();
    point(who, ...grab(id));
    await sleep(1150);

    // Bırak: yer tutucu kapanır, sayaçlar güncellenir, herkese haber gider.
    S.cols[from].splice(S.cols[from].indexOf("ph"), 1);
    S.carry = null;
    S.lifted = null;
    el.classList.remove("is-lift", "is-carry");
    cursors[who].el.classList.remove("is-carry");
    say(who, PEOPLE[who].name);
    layout();
    point(who, ...grab(id));
    flash(id);
    ticker.push(who, feedText(who, feed));
    toast(who, note);
    await sleep(500);
    active(who, false);
  }

  async function add({ who, id, to, at, feed, toast: note }) {
    for (const k of Object.keys(cursors)) if (k !== who) rest(k);
    active(who, true);
    const g = geo();
    point(who, g[to].x + g[to].w * 0.3, g[to].y + 14);
    await sleep(900);

    // Kart boş açılır, harf harf yazılır (başkasının ekranında da böyle görünür).
    const el = cards[id];
    const title = el.querySelector(".lp-card-t");
    title.textContent = "";
    el.classList.add("is-typing");
    S.adding = id;
    S.cols[to].splice(at, 0, id);
    el.style.transition = "none";
    layout();
    void el.offsetWidth;
    el.style.transition = "";
    el.classList.remove("is-gone");
    el.classList.add("is-pop");
    S.adding = null;
    const p = S.pos[id];
    point(who, p.x + p.w * 0.6, p.y + p.h * 0.42);
    await sleep(700);
    for (const ch of cardTitle(id)) {
      title.textContent += ch;
      await sleep(70 + Math.random() * 70);
    }
    await sleep(380);
    el.classList.remove("is-typing", "is-pop");
    layout();                              // dar ekranda başlık iki satıra inmiş olabilir
    flash(id);
    ticker.push(who, feedText(who, feed));
    toast(who, note);
    await sleep(500);
    active(who, false);
  }

  let first = true;
  async function scene() {
    if (!first) {
      layer.classList.add("is-fade");     // döngü başa dönerken yumuşak geçiş
      await sleep(480);
    }
    first = false;
    S.cols = START.map((c) => [...c]);
    S.carry = S.lifted = S.adding = null;
    for (const [id, el] of Object.entries(cards)) {
      el.classList.remove("is-lift", "is-carry", "is-typing", "is-pop", "is-flash");
      el.querySelector(".lp-card-t").textContent = cardTitle(id);
    }
    for (const key of Object.keys(cursors)) say(key, PEOPLE[key].name);
    snap();
    layer.classList.remove("is-fade");
    await sleep(1300);
    for (const step of SCRIPT) {
      await (step.kind === "add" ? add(step) : move(step));
      await sleep(1500);
    }
    await sleep(1500);
  }

  ticker.seed([
    { who: "mert", text: feedText("mert", "addDark"), at: -12000 },
    { who: "deniz", text: feedText("deniz", "joined"), at: -62000 },
    { who: "ayse", text: feedText("ayse", "moveGiris"), at: -170000 },
  ]);
  snap();
  // Pencere genişliği değişince (ya da yazı tipi yüklenince) kartlar yeniden ölçülür.
  let lastW = board.clientWidth;
  const ro = typeof ResizeObserver === "function" ? new ResizeObserver(() => {
    if (!alive() || board.clientWidth === lastW) return;
    lastW = board.clientWidth;
    snap();
  }) : null;
  ro?.observe(board);
  cleanups.push(() => ro?.disconnect());
  watchSizes(() => { fit(); layout(); });
  run(scene);
}

/**
 * "Örnek pano · canlı akış" şeridi: en yeni olay başa eklenir, eskiler kayar
 * (FLIP: önce/sonra ölçülür, fark transform ile kapatılır), en eskisi söner.
 */
function makeTicker(list) {
  let clock = 0;
  const items = [];
  // "şimdi", "30 sn önce", "2 dk önce": canlandırmanın kendi saatine göre.
  const when = (at) => ago((clock - at) / 1000, { withSeconds: true });
  const chip = (it) => {
    const li = document.createElement("li");
    li.className = "lp-chip";
    li.innerHTML = `${av(it.who)}<span class="lp-chip-tx">${esc(it.text)}</span><span class="lp-chip-when"></span>`;
    it.el = li;
    return li;
  };
  const refresh = () => items.forEach((it) => {
    const w = it.el.querySelector(".lp-chip-when"), t = when(it.at);
    if (w.textContent !== t) w.textContent = t;
  });
  return {
    seed(list0) {
      for (const it of list0) { items.push(it); list.appendChild(chip(it)); }
      refresh();
    },
    tick(ms) { clock += ms; refresh(); },
    push(who, text) {
      const before = new Map(items.map((it) => [it.el, it.el.getBoundingClientRect()]));
      const it = { who, text, at: clock };
      const li = chip(it);
      li.classList.add("is-enter");
      list.prepend(li);
      items.unshift(it);
      refresh();
      for (const [el, a] of before) {
        const b = el.getBoundingClientRect();
        const dx = a.left - b.left, dy = a.top - b.top;
        if (!dx && !dy) continue;
        el.style.transition = "none";
        el.style.transform = `translate(${dx}px, ${dy}px)`;
      }
      void list.offsetWidth;
      for (const el of before.keys()) { el.style.transition = ""; el.style.transform = ""; }
      li.classList.remove("is-enter");
      while (items.length > 3) {
        const old = items.pop();
        old.el.classList.add("is-leave");
        setTimeout(() => old.el.remove(), 420);
      }
    },
  };
}

/* ══════════════════ Geçmiş görseli ══════════════════
   Kaydırıcı üç an arasında gidip gelir; kartlar kolonlar arasında FLIP ile
   kayar, sonradan silinen kolon "şimdi"de söner. */
function startTimeTravel(page, alive, cleanups) {
  const root = page.querySelector(".lp-tt");
  if (REDUCE || !root) return;          // temel işaretleme zaten tasarımdaki an
  const cols = [...root.querySelectorAll(".lp-tt-col")];
  const cards = Object.fromEntries([...root.querySelectorAll("[data-tt]")].map((e) => [e.dataset.tt, e]));
  const txt = root.querySelector(".lp-tt-txt");
  const gate = makeGate([root], alive, cleanups);

  function apply(key) {
    const st = TIMES[key];
    const els = Object.values(cards);
    const first = new Map(els.map((e) => [e, e.getBoundingClientRect()]));
    st.cols.forEach((ids, ci) => ids.forEach((id) => cols[ci].appendChild(cards[id])));
    for (const e of els) {
      const a = first.get(e), b = e.getBoundingClientRect();
      const dx = a.left - b.left, dy = a.top - b.top;
      if (!dx && !dy) continue;
      e.style.transition = "none";
      e.style.transform = `translate(${dx}px, ${dy}px)`;
    }
    void root.offsetWidth;
    for (const e of els) { e.style.transition = ""; e.style.transform = ""; }
    root.style.setProperty("--p", st.p);
    root.dataset.step = key;
    if (st.flash) {
      const f = cards[st.flash];
      f.classList.remove("is-flash"); void f.offsetWidth; f.classList.add("is-flash");
    }
  }

  let i = 0;
  run(async () => {
    await gate.sleep(2600);
    i = (i + 1) % TIME_ORDER.length;
    const key = TIME_ORDER[i];
    txt.classList.add("is-swap");
    apply(key);
    await gate.sleep(220);
    txt.innerHTML = timeText(key);
    txt.classList.remove("is-swap");
  });
}
