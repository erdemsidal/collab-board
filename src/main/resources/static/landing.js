// CollabBoard — tanıtım sayfası ("A · Gece")
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
// -----------------------------------------------------------------------------

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
const MARK = (s) => `<svg width="${s}" height="${s}" viewBox="0 0 16 16" aria-hidden="true"><rect x="2" y="2" width="3" height="12" rx="1.2"/><rect x="6.5" y="2" width="3" height="8" rx="1.2"/><rect x="11" y="2" width="3" height="5" rx="1.2"/></svg>`;
const stroke = (d, size = 16, w = 2) =>
  `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="${w}" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${d}</svg>`;
const ARROW = stroke(`<path d="M5 12h14M13 6l6 6-6 6"/>`, 16, 2.2);
const HISTORY = stroke(`<path d="M3 12a9 9 0 1 0 3-6.7L3 8"/><path d="M3 3v5h5"/><path d="M12 7v5l3 2"/>`);
const CHEVRON = stroke(`<path d="M6 9l6 6 6-6"/>`, 18);
const CHECK = stroke(`<path d="M5 12.5l4.5 4.5L19 7.5"/>`, 15, 2.4);
const MENU = `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path class="l1" d="M4 7h16"/><path class="l2" d="M4 12h16"/><path class="l3" d="M4 17h16"/></svg>`;
const POINTER = (color) =>
  `<svg width="16" height="18" viewBox="0 0 18 20" aria-hidden="true"><path d="M1.5 1.5 16 10.2 9.4 11.7 6.2 18.2Z" fill="${color}" stroke="#0A0C10" stroke-width="1.5" stroke-linejoin="round"/></svg>`;

/* ── İçerik ── */
const NAV = [["lp-ozellikler", "Özellikler"], ["lp-nasil", "Nasıl çalışır"], ["lp-roller", "Roller"], ["lp-hakkinda", "Hakkında"], ["lp-sss", "SSS"]];

const FEATURES = [
  { title: "Anında canlı senkron", color: "#FF7A1A", icon: "M13 2L4 14h7l-1 8 9-12h-7z", text: "Kart ekleme, taşıma, düzenleme, kolon değişikliği: hepsi herkesin ekranına aynı anda düşer. Yenileme tuşu yok." },
  { title: "Canlı imleçler", color: "#B197FC", icon: "M4 3l14 8-6 1.5L9 19z", text: "Ekip arkadaşlarının faresini isimleriyle görürsün. Biri bir kartı tutunca kart herkeste soluklaşır." },
  { title: "Çakışma koruması", color: "#FF9B9B", icon: "M12 3l8 4v5c0 4.5-3.4 8.2-8 9-4.6-.8-8-4.5-8-9V7zM9 12l2 2 4-4", text: "Her kartın bir sürümü var. Bayat bir değişiklik reddedilir, ekranın tazelenir; kimsenin emeği sessizce ezilmez." },
  { title: "Zaman yolculuğu", color: "#3DD68C", icon: "M3 12a9 9 0 1 0 3-6.7L3 8M3 3v5h5M12 7v5l3 2", text: "Panonun geçmişteki herhangi bir anını kaydırıcıyla aç. Silinen kolonlar bile o anki hâliyle görünür." },
  { title: "Akış sağlığı", color: "#4DABF7", icon: "M4 20V10M10 20V4M16 20v-7M22 20H2", text: "Kolon başına ortalama bekleme, çevrim süresi ve darboğaz. İşin nerede tıkandığını ölçüm söyler." },
  { title: "Yaşlanan iş", color: "#FFD27A", icon: "M12 7v5l3 2M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z", text: "Açık kartlar bulundukları kolonda ne kadardır bekliyor? En eskisi en üstte; unutulan iş görünür olur." },
  { title: "WIP limiti", color: "#FF7A1A", icon: "M4 6h16M4 12h10M4 18h6", text: "Bir kolona aynı anda girebilecek kart sayısını sınırla. Dolan kolon yeni iş kabul etmez: başlamak yerine bitir." },
  { title: "Onaylı davet ve bildirim", color: "#B197FC", icon: "M6 8a6 6 0 1 1 12 0c0 7 3 8 3 8H3s3-1 3-8M10 20a2 2 0 0 0 4 0", text: "Kimse rızası olmadan panoya eklenmez. Davet, hangi ekrandaysan anında önüne düşer." },
  { title: "Kesintisiz oturum", color: "#3DD68C", icon: "M7 11V8a5 5 0 0 1 10 0v3M5 11h14v10H5z", text: "Sitede olduğun sürece oturumun sessizce yenilenir. Şifreni değiştirince diğer cihazlar çıkış yapar." },
];
const FLOW = [
  ["Yapılacak", "30%", "#4A5466", "0,8 gün"],
  ["Devam ediyor", "55%", "#4DABF7", "1,9 gün"],
  ["İncelemede", "88%", "#FF9B9B", "3,4 gün"],
  ["Bitti", "12%", "#3DD68C", "—"],
];
const AGING = [
  ["Ödeme sayfası entegrasyonu", "İncelemede", "6 gün", "#FF9B9B"],
  ["API hız sınırı dokümanı", "Devam ediyor", "3 gün", "#FFD27A"],
  ["Onboarding e-postası", "Yapılacak", "1 gün", "#A9B1C0"],
];
const STEPS = [
  ["01", "Hesabını aç", "E-postanı doğrula, giriş yap. Şifreni unutursan 30 dakikalık bir bağlantıyla sıfırlarsın."],
  ["02", "Pano kur, ekibini davet et", "Kolonlarını belirle, ekip arkadaşlarını Editör ya da İzleyici olarak çağır."],
  ["03", "Birlikte çalışın", "Kartları taşıyın, yazın, atayın. Herkes her şeyi aynı anda görür."],
];
const ROLES = [
  ["Panoyu ve değişiklikleri canlı görür", 1, 1, 1],
  ["Kart ve kolon değiştirir", 1, 1, 0],
  ["Üye davet eder, rolleri yönetir", 1, 0, 0],
  ["Geçmişi ve akış ölçümünü görür", 1, 1, 1],
];
const STACK = [
  ["WebSocket · STOMP", "Sürekli açık bağlantı; değişiklik sunucuya sorulmadan gelir, polling yok."],
  ["Redis Pub/Sub", "Birden çok sunucu tek bir canlı yayın gibi davranır; kimse kimseyi kaçırmaz."],
  ["PostgreSQL · olay kaydı", "Her değişiklik kayıtlı; geçmiş ve akış ölçümü bu kayıttan hesaplanır."],
  ["JWT · kesintisiz oturum", "Kısa ömürlü anahtar sessizce yenilenir; yüzü aşkın otomatik test her değişiklikte koşar."],
];
const PRINCIPLES = [
  ["Önce canlılık", "Bir değişiklik olduysa, panoya bakan herkes onu aynı anda görür."],
  ["Veri kaybı yok", "Aynı anda yapılan iki değişiklikten biri sessizce ezilmez."],
  ["Şeffaf geçmiş", "Kim ne yaptı, ne zaman yaptı — her zaman geri dönülebilir."],
  ["Yetki sunucuda", "Arayüzde gizlenen bir düğme güvenlik sayılmaz; kuralı sunucu uygular."],
];
const FAQ = [
  ["Aynı kartı iki kişi aynı anda değiştirirse ne olur?", "İlk kaydedilen geçerli olur. Sonraki değişiklik reddedilir, o kişinin ekranı en güncel hâliyle tazelenir ve bir uyarıyla ne olduğunu görür."],
  ["Davet ettiğim kişinin hesabı yoksa?", "Davet e-posta adresine bağlıdır. Kişiye kayıt bağlantılı bir posta gider; kaydolup giriş yaptığında davet onu bekler."],
  ["İzleyici neler yapabilir?", "Panoyu ve bütün değişiklikleri canlı görür, geçmişe ve akış ölçümüne bakabilir; kart ya da kolon değiştiremez."],
  ["Panonun geçmişini kimler görebilir?", "Yalnızca o panoyu görme yetkisi olanlar. Geçmişe bakarken pano salt okunurdur."],
  ["Şifrem nasıl saklanıyor?", "Geri çevrilemez biçimde (BCrypt) saklanır. Şifreni değiştirdiğinde diğer cihazlardaki oturumların kapanır, bulunduğun cihazda devam edersin."],
];

/* ── Hero panosu: kartlar ve senaryo ── */
const CARDS = {
  onb:    { t: "Onboarding e-postası", tag: ["date", "29 Eki"] },
  ode:    { t: "Ödeme sayfası entegrasyonu", tag: ["soon", "Yarın"] },
  dark:   { t: "Karanlık tema" },
  api:    { t: "API hız sınırı dokümanı", tag: ["late", "2 gün gecikti"] },
  push:   { t: "Push bildirim izni" },
  giris:  { t: "Giriş ekranı tasarımı" },
  fatura: { t: "Fatura PDF'i" },          // canlı yazılarak eklenir
};
const COLS = ["Yapılacak", "Devam ediyor", "Bitti"];
const WIP = 3;                              // ortadaki kolonun limiti: "2 / 3"
const START = [["onb", "ode", "dark"], ["api", "push"], ["giris"]];
/** Hareketsiz kare = tasarımdaki an: Ayşe "Ödeme sayfası"nı taşıyor, yeri boş duruyor. */
const STILL = [["onb", "ph", "dark"], ["api", "push"], ["giris"]];
const SCRIPT = [
  { kind: "move", who: "ayse", id: "ode", to: 1, at: 2,
    feed: `Ayşe "Ödeme sayfası"nı Devam ediyor'a taşıdı`, toast: ["Ayşe kartı taşıdı", "Ödeme sayfası → Devam ediyor"] },
  { kind: "move", who: "mert", id: "push", to: 2, at: 0,
    feed: `Mert "Push bildirim izni"ni Bitti'ye taşıdı`, toast: ["Mert kartı taşıdı", "Push bildirim izni → Bitti"] },
  { kind: "add", who: "mert", id: "fatura", to: 0, at: 0,
    feed: `Mert "Fatura PDF'i" kartını ekledi`, toast: ["Mert kart ekledi", "Fatura PDF'i · Yapılacak"] },
  { kind: "move", who: "ayse", id: "onb", to: 1, at: 2,
    feed: `Ayşe "Onboarding e-postası"nı Devam ediyor'a taşıdı`, toast: ["Ayşe kartı taşıdı", "Onboarding e-postası → Devam ediyor"] },
];
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

/* ── Geçmiş görseli: üç an ── */
const TIMES = {
  past: { p: 0.28, when: "20 Ekim, 10:05", what: `17. hareket · Ayşe "Giriş ekranı"nı İncelemede'ye taşıdı`,
          cols: [["onb"], ["api", "push"], ["giris"]], flash: "giris" },
  mid:  { p: 0.62, when: "21 Ekim, 14:32", what: `38. hareket · Mert "API dokümanı"nı taşıdı`,
          cols: [["onb", "api"], ["push"], ["giris"]], flash: "api" },
  now:  { p: 1, when: "Şimdi", what: `52. hareket · Deniz "İncelemede" kolonunu sildi`,
          cols: [["onb", "api"], ["push"], ["giris"]] },
};
const TIME_ORDER = ["mid", "now", "past"];

/**
 * Tanıtım sayfasını root'a çizer.
 * onLogin / onRegister: giriş ekranını ilgili modda açar (index.html verir).
 * onForgot (isteğe bağlı): "Şifremi unuttum"; verilmezse onLogin çağrılır.
 * Dönen fonksiyon sayfayı söker; çağrılmasa da root başka bir ekranla
 * doldurulunca zamanlayıcılar ve dinleyiciler kendiliğinden durur.
 */
export function renderLanding(root, { onLogin, onRegister, onForgot } = {}) {
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
    btn.setAttribute("aria-label", open ? "Menüyü kapat" : "Menüyü aç");
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
  const navLinks = NAV.map(([id, label]) => `<a href="#${id}" data-scroll="${id}">${label}</a>`).join("");
  const year = new Date().getFullYear();
  return `
<div class="lp">
  <a class="lp-skip" href="#lp-main" data-scroll="lp-main">İçeriğe geç</a>
  <header class="lp-header">
    <div class="lp-wrap lp-header-in">
      <a class="lp-brand" href="#lp-top" data-scroll="lp-top"><span class="lp-mark">${MARK(16)}</span>CollabBoard</a>
      <nav class="lp-nav" aria-label="Sayfa bölümleri">${navLinks}</nav>
      <div class="lp-header-acts">
        <button type="button" class="lp-btn lp-btn-ghost lp-btn-sm" data-act="login">Giriş yap</button>
        <button type="button" class="lp-btn lp-btn-primary lp-btn-sm lp-hdr-reg" data-act="register">Kayıt ol</button>
        <button type="button" class="lp-menu-btn" aria-expanded="false" aria-controls="lp-menu" aria-label="Menüyü aç">${MENU}</button>
      </div>
    </div>
    <div class="lp-menu" id="lp-menu">
      <nav class="lp-wrap lp-menu-in" aria-label="Menü">
        ${navLinks}
        <div class="lp-menu-acts">
          <button type="button" class="lp-btn lp-btn-primary" data-act="register">Kayıt ol</button>
          <button type="button" class="lp-btn lp-btn-ghost" data-act="login">Giriş yap</button>
        </div>
      </nav>
    </div>
  </header>

  <main id="lp-main">
  <section class="lp-wrap lp-hero lp-loop" id="lp-top" aria-labelledby="lp-hero-h">
    <div class="lp-hero-copy">
      <p class="lp-pill lp-rise"><span class="lp-dot lp-pulse"></span>gerçek zamanlı kanban</p>
      <h1 class="lp-rise" id="lp-hero-h" style="--d:.06s">Aynı pano.<br>Aynı an.<br><span class="lp-accent">Bütün ekip.</span></h1>
      <p class="lp-lead lp-rise" style="--d:.12s">Bir kartı taşıdığın an ekibinin ekranında da kayar. Kim panoda, kim neyi tutuyor, kim neyi değiştirdi — sayfayı yenilemeden görürsün.</p>
      <div class="lp-ctas lp-rise" style="--d:.18s">
        <button type="button" class="lp-btn lp-btn-primary lp-btn-lg" data-act="register">Ücretsiz kayıt ol ${ARROW}</button>
        <a class="lp-btn lp-btn-ghost lp-btn-lg" href="#lp-nasil" data-scroll="lp-nasil">Nasıl çalışır?</a>
      </div>
      <ul class="lp-tech lp-rise" style="--d:.24s"><li>websocket · stomp</li><li>sürüm kontrolü</li><li>geri sarılabilir geçmiş</li></ul>
    </div>

    <div class="lp-hero-vis lp-rise" style="--d:.16s">
      <div class="lp-demo" role="img" aria-label="Canlandırma: Ayşe ve Mert aynı panoda kart taşıyor ve yeni kart ekliyor; her hareket, kimin yaptığıyla birlikte anında herkesin ekranına düşüyor.">
        <div class="lp-win" aria-hidden="true">
          <div class="lp-win-bar">
            <span class="lp-win-title">Ürün Yol Haritası</span>
            <span class="lp-role">YÖNETİCİ</span>
            <span class="lp-presence">${av("ayse")}${av("mert")}${av("deniz")}</span>
            <span class="lp-live"><span class="lp-dot lp-pulse"></span>Canlı</span>
          </div>
          <div class="lp-board">
            ${COLS.map((c, i) => `<div class="lp-col"><div class="lp-col-h"><span>${c}</span><span class="lp-count${i === 1 ? " is-wip" : ""}">${i === 1 ? `2 / ${WIP}` : i ? "1" : "3"}</span></div></div>`).join("")}
            <div class="lp-layer"></div>
            <div class="lp-toasts"></div>
          </div>
        </div>
      </div>
    </div>
  </section>

  <section class="lp-wrap lp-ticker-sec" aria-label="Örnek pano akışı">
    <div class="lp-ticker">
      <span class="lp-ticker-l">örnek pano · canlı akış</span>
      <p class="lp-sr">Örnek bir panoda ekip arkadaşlarının hareketleri burada canlı olarak akar.</p>
      <ul class="lp-ticker-list" aria-hidden="true"></ul>
    </div>
  </section>

  <section class="lp-wrap lp-sec" id="lp-ozellikler" aria-labelledby="lp-h-ozellikler">
    <p class="lp-eyebrow lp-reveal">özellikler</p>
    <h2 class="lp-h2 lp-h2-xl lp-reveal" id="lp-h-ozellikler" style="max-width:720px">Aynı anda çalışmanın zor kısımları, çözülmüş olarak.</h2>
    <p class="lp-sub lp-reveal">Canlı güncelleme işin yalnızca yarısı. Aynı kartı iki kişi değiştirdiğinde, iş bir kolonda takıldığında ya da "dün bu pano nasıldı?" diye sorulduğunda da cevap panoda.</p>
    <div class="lp-feats lp-loop">
      ${FEATURES.map((f, i) => `
      <article class="lp-feat lp-reveal" style="--d:${(i % 3) * 0.07}s">
        <span class="lp-fi" style="--c:${f.color};--i:${i}">${stroke(`<path d="${f.icon}"/>`, 18)}</span>
        <h3>${f.title}</h3>
        <p>${f.text}</p>
      </article>`).join("")}
    </div>
  </section>

  <section class="lp-wrap lp-spot" aria-labelledby="lp-h-gecmis">
    <div class="lp-spot-copy lp-reveal">
      <p class="lp-eyebrow" style="--c:#3DD68C">geçmiş</p>
      <h2 class="lp-h2 lp-h2-m" id="lp-h-gecmis">Dün bu pano nasıldı?</h2>
      <p class="lp-body">Her değişiklik bir olay olarak kaydedilir: kim, ne zaman, ne yaptı. Kaydırıcıyı çek, panonun o anki hâli karşına gelsin — silinen kolonlar bile.</p>
      <ul class="lp-dash" style="--c:#3DD68C">
        <li>Herhangi bir ana tek hareketle dön</li>
        <li>Geçmişe bakarken pano salt okunur; yanlışlıkla değiştiremezsin</li>
        <li>"Şimdi"ye tek tıkla geri gel</li>
      </ul>
    </div>
    <div class="lp-spot-vis lp-reveal" style="--d:.1s">
      <div class="lp-panel lp-tt" data-step="mid" style="--p:${TIMES.mid.p}" role="img" aria-label="Geçmiş görünümü: kaydırıcı geriye çekildikçe pano o anki hâliyle görünür; sonradan silinen İncelemede kolonu bile.">
        <div aria-hidden="true">
          <div class="lp-tt-h">
            <span class="lp-tt-ico">${HISTORY}</span>
            <span class="lp-tt-txt">${timeText(TIMES.mid)}</span>
            <span class="lp-tt-now">Şimdiye dön</span>
          </div>
          <div class="lp-slider"><span class="lp-slider-track"></span><span class="lp-slider-fill"></span><span class="lp-slider-run"><span class="lp-slider-thumb"></span></span></div>
          <div class="lp-tt-board">
            <div class="lp-tt-col"><span class="lp-tt-col-h">Yapılacak</span><span class="lp-tt-card" data-tt="onb">Onboarding e-postası</span><span class="lp-tt-card" data-tt="api">API hız sınırı</span></div>
            <div class="lp-tt-col"><span class="lp-tt-col-h">Devam ediyor</span><span class="lp-tt-card" data-tt="push">Push bildirim izni</span></div>
            <div class="lp-tt-col is-del"><span class="lp-tt-col-h">İncelemede <span class="lp-tt-del">· silindi</span></span><span class="lp-tt-card" data-tt="giris">Giriş ekranı</span></div>
          </div>
        </div>
      </div>
    </div>
  </section>

  <section class="lp-wrap lp-spot lp-spot-rev" aria-labelledby="lp-h-akis">
    <div class="lp-spot-vis lp-reveal" style="--d:.1s">
      <div class="lp-panel lp-flow lp-loop" role="img" aria-label="Akış sağlığı paneli: kolon başına ortalama bekleme süresi; en uzun bekleyen İncelemede kolonu darboğaz olarak işaretli, altta en uzun bekleyen kartlar listeleniyor.">
        <div aria-hidden="true">
          <div class="lp-flow-h">Akış sağlığı <span class="lp-bn">Darboğaz: İncelemede</span></div>
          <div class="lp-flow-rows">
            ${FLOW.map(([name, w, c, v], i) => `<div class="lp-flow-row"><span class="lp-flow-nm">${name}</span><span class="lp-flow-bar"><i style="--w:${w};--c:${c};--d:${i * 0.12}s"></i></span><span class="lp-flow-val">${v}</span></div>`).join("")}
          </div>
          <div class="lp-aging">
            <p class="lp-aging-h">Yaşlanan iş</p>
            <div class="lp-aging-rows">
              ${AGING.map(([t, col, d, c]) => `<div class="lp-aging-row"><span class="nm">${t}</span><span class="col">${col}</span><span class="d" style="color:${c}">${d}</span></div>`).join("")}
            </div>
          </div>
        </div>
      </div>
    </div>
    <div class="lp-spot-copy lp-reveal">
      <p class="lp-eyebrow" style="--c:#FF9B9B">akış</p>
      <h2 class="lp-h2 lp-h2-m" id="lp-h-akis">İş nerede tıkanıyor? Tahmin etme, ölç.</h2>
      <p class="lp-body">Her kolonda kartların ortalama ne kadar beklediği, çevrim süresi ve darboğaz aynı olay kaydından hesaplanır. Yaşlanan iş listesi unutulmuş kartı en üste taşır; WIP limiti dolan kolonu durdurur.</p>
    </div>
  </section>

  <section class="lp-wrap lp-spot lp-spot-last" aria-labelledby="lp-h-ekip">
    <div class="lp-spot-copy lp-reveal">
      <p class="lp-eyebrow" style="--c:#B197FC">ekip</p>
      <h2 class="lp-h2 lp-h2-m" id="lp-h-ekip">Kimse habersiz eklenmez.</h2>
      <p class="lp-body">Panoya birini e-postasıyla davet edersin; davet o kişi hangi ekrandaysa anında önüne düşer. Kabul edince üye olur, sen de o an haber alırsın. Hesabı yoksa kayıt bağlantısı gider, davet onu bekler.</p>
    </div>
    <div class="lp-spot-vis lp-team lp-loop lp-reveal" style="--d:.1s" role="img" aria-label="Davet bildirimi: Mert, Ayşe'yi Ürün Yol Haritası panosuna Editör olarak davet ediyor; Ayşe kabul edince Mert'e anında haber gidiyor.">
      <div class="lp-inv" aria-hidden="true">
        ${av("mert", "lp-av-lg")}
        <div class="lp-inv-b">
          <p class="lp-inv-t">Pano daveti</p>
          <p class="lp-inv-p"><strong>Mert</strong> seni <strong>Ürün Yol Haritası</strong> panosuna <strong>Editör</strong> olarak davet etti.</p>
          <div class="lp-inv-acts">
            <span class="lp-inv-btns"><span class="lp-inv-yes">Kabul et</span><span class="lp-inv-no">Reddet</span></span>
            <span class="lp-inv-ok">${CHECK}Kabul edildi · panoya katıldın</span>
          </div>
        </div>
      </div>
      <div class="lp-acc" aria-hidden="true">
        <span class="lp-dot"></span>
        <span><strong>Ayşe</strong> davetini kabul etti · şimdi</span>
      </div>
    </div>
  </section>

  <section class="lp-band" id="lp-nasil" aria-labelledby="lp-h-nasil">
    <div class="lp-wrap lp-band-in">
      <p class="lp-eyebrow lp-reveal">nasıl çalışır</p>
      <h2 class="lp-h2 lp-h2-l lp-reveal" id="lp-h-nasil">Üç adımda birlikte çalışmaya başla.</h2>
      <ol class="lp-steps">
        ${STEPS.map(([n, t, p], i) => `<li class="lp-step lp-reveal" style="--d:${i * 0.08}s"><span class="lp-step-n">${n}</span><h3>${t}</h3><p>${p}</p></li>`).join("")}
      </ol>
    </div>
  </section>

  <section class="lp-wrap lp-sec lp-roles" id="lp-roller" aria-labelledby="lp-h-roller">
    <div class="lp-roles-copy lp-reveal">
      <p class="lp-eyebrow" style="--c:#4DABF7">roller</p>
      <h2 class="lp-h2 lp-h2-s" id="lp-h-roller">Herkes ne kadar yetkiliyse o kadar.</h2>
      <p class="lp-body">Yetkiyi arayüz değil sunucu uygular: gizlenen bir düğme, kapalı bir kapı demek değil. İzleyici her değişikliği canlı görür ama hiçbir şeyi değiştiremez.</p>
    </div>
    <div class="lp-table-wrap lp-reveal" style="--d:.1s">
      <table class="lp-table">
        <caption class="lp-sr">Rollere göre yetkiler</caption>
        <thead><tr><td></td><th scope="col" style="color:#FFB37A">Yönetici</th><th scope="col" style="color:#9EC3FF">Editör</th><th scope="col">İzleyici</th></tr></thead>
        <tbody>
          ${ROLES.map(([what, ...v]) => `<tr><th scope="row">${what}</th>${v.map((y) => `<td class="${y ? "yes" : "no"}"><span aria-hidden="true">${y ? "✓" : "—"}</span><span class="lp-sr">${y ? "Evet" : "Hayır"}</span></td>`).join("")}</tr>`).join("")}
        </tbody>
      </table>
    </div>
  </section>

  <section class="lp-wrap lp-sec lp-stack-sec" aria-labelledby="lp-h-kaput">
    <p class="lp-eyebrow lp-reveal" style="--c:#9AA3B2">kaputun altında</p>
    <h2 class="lp-h2 lp-h2-s lp-reveal" id="lp-h-kaput">Canlı olması için yapılmış bir altyapı.</h2>
    <div class="lp-stack">
      ${STACK.map(([n, t], i) => `<div class="lp-stack-i lp-reveal" style="--d:${i * 0.06}s"><p class="lp-stack-n">${n}</p><p>${t}</p></div>`).join("")}
    </div>
  </section>

  <section class="lp-band lp-band-top" id="lp-hakkinda" aria-labelledby="lp-h-hakkinda">
    <div class="lp-wrap lp-band-in lp-about">
      <div class="lp-about-copy lp-reveal">
        <p class="lp-eyebrow">hakkında</p>
        <h2 class="lp-h2 lp-h2-l" id="lp-h-hakkinda">Neden CollabBoard?</h2>
        <p class="lp-body lp-body-l">CollabBoard, bir ekibin aynı panoda gerçekten aynı anda çalışabilmesi için yazıldı. Çoğu araç bir değişikliği birkaç saniye sonra ya da sayfa yenilenince gösterir; burada her hareket anında herkese ulaşır ve hiçbir değişiklik sessizce kaybolmaz.</p>
      </div>
      <div class="lp-principles">
        ${PRINCIPLES.map(([t, p], i) => `<div class="lp-principle lp-reveal" style="--d:${i * 0.06}s"><h3>${t}</h3><p>${p}</p></div>`).join("")}
      </div>
    </div>
  </section>

  <section class="lp-wrap lp-sec lp-faq-sec" id="lp-sss" aria-labelledby="lp-h-sss">
    <p class="lp-eyebrow lp-center lp-reveal">sık sorulanlar</p>
    <h2 class="lp-h2 lp-h2-s lp-center lp-reveal" id="lp-h-sss">Aklındaki sorular</h2>
    <div class="lp-faq">
      ${FAQ.map(([q, a], i) => `<details class="lp-qa lp-reveal" style="--d:${i * 0.05}s"><summary><span>${q}</span><span class="lp-qa-i">${CHEVRON}</span></summary><p>${a}</p></details>`).join("")}
    </div>
  </section>

  <section class="lp-wrap lp-cta-sec" aria-labelledby="lp-h-cta">
    <div class="lp-cta lp-reveal">
      <div class="lp-cta-copy">
        <h2 class="lp-h2 lp-h2-s" id="lp-h-cta">Panonu aç, ekibini çağır.</h2>
        <p>Bir dakikada hesap aç, ilk panonda ekibinle aynı anda çalış.</p>
      </div>
      <div class="lp-cta-acts">
        <button type="button" class="lp-btn lp-btn-primary lp-btn-lg" data-act="register">Kayıt ol</button>
        <button type="button" class="lp-btn lp-btn-ghost lp-btn-lg" data-act="login">Giriş yap</button>
      </div>
    </div>
  </section>
  </main>

  <footer class="lp-foot">
    <div class="lp-wrap lp-foot-in">
      <div class="lp-foot-brand">
        <p class="lp-foot-logo"><span class="lp-mark lp-mark-sm">${MARK(13)}</span>CollabBoard</p>
        <p class="lp-foot-tag">Gerçek zamanlı, birlikte çalışılan Kanban panosu.</p>
      </div>
      <nav class="lp-foot-nav" aria-label="Ürün">
        <span class="lp-foot-h">ÜRÜN</span>
        <a href="#lp-ozellikler" data-scroll="lp-ozellikler">Özellikler</a><a href="#lp-nasil" data-scroll="lp-nasil">Nasıl çalışır</a><a href="#lp-roller" data-scroll="lp-roller">Roller</a>
      </nav>
      <nav class="lp-foot-nav" aria-label="Proje">
        <span class="lp-foot-h">PROJE</span>
        <a href="#lp-hakkinda" data-scroll="lp-hakkinda">Hakkında</a><a href="#lp-sss" data-scroll="lp-sss">SSS</a>
      </nav>
      <nav class="lp-foot-nav" aria-label="Hesap">
        <span class="lp-foot-h">HESAP</span>
        <button type="button" data-act="login">Giriş yap</button><button type="button" data-act="register">Kayıt ol</button><button type="button" data-act="forgot">Şifremi unuttum</button>
      </nav>
    </div>
    <div class="lp-wrap lp-copy">© ${year} CollabBoard</div>
  </footer>
</div>`;
}

function timeText(t) {
  return `<b>${esc(t.when)}</b> · ${esc(t.what)}`;
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
    el.innerHTML = `<div class="lp-card-in"><span class="lp-card-t">${esc(c.t)}</span>${c.tag ? `<span class="lp-tag ${c.tag[0]}">${esc(c.tag[1])}</span>` : ""}</div>`;
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
      t.textContent = CARDS[id].t;          // yazılırken ölçülse de tam hâliyle ölç
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
  function toast(key, [title, body]) {
    const t = document.createElement("div");
    t.className = "lp-toast";
    t.innerHTML = `${av(key)}<div><b>${esc(title)}</b><span>${esc(body)}</span></div>`;
    toasts.appendChild(t);
    while (toasts.children.length > 2) toasts.firstElementChild.remove();
    setTimeout(() => { t.classList.add("is-out"); setTimeout(() => t.remove(), 350); }, 2600);
  }

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
    say("ayse", "Ayşe taşıyor");
    point("ayse", x + p.w * 0.86, y + Math.min(el.offsetHeight * 0.62, 34));
    rest("mert", false);
  }

  if (REDUCE) {
    ticker.seed([
      { who: "ayse", text: SCRIPT[0].feed, at: 0 },
      { who: "mert", text: `Mert "Karanlık tema" kartını ekledi`, at: -12000 },
      { who: "deniz", text: "Deniz panoya katıldı", at: -60000 },
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
    say(who, `${PEOPLE[who].name} taşıyor`);
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
    ticker.push(who, feed);
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
    for (const ch of CARDS[id].t) {
      title.textContent += ch;
      await sleep(70 + Math.random() * 70);
    }
    await sleep(380);
    el.classList.remove("is-typing", "is-pop");
    layout();                              // dar ekranda başlık iki satıra inmiş olabilir
    flash(id);
    ticker.push(who, feed);
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
      el.querySelector(".lp-card-t").textContent = CARDS[id].t;
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
    { who: "mert", text: `Mert "Karanlık tema" kartını ekledi`, at: -12000 },
    { who: "deniz", text: "Deniz panoya katıldı", at: -62000 },
    { who: "ayse", text: `Ayşe "Giriş ekranı tasarımı"nı Bitti'ye taşıdı`, at: -170000 },
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
  const when = (at) => {
    const s = Math.max(0, Math.round((clock - at) / 1000));
    return s < 3 ? "şimdi" : s < 60 ? `${s} sn önce` : `${Math.floor(s / 60)} dk önce`;
  };
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
    txt.innerHTML = timeText(TIMES[key]);
    txt.classList.remove("is-swap");
  });
}
