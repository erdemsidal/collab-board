// CollabBoard — tanıtım sayfası
// -----------------------------------------------------------------------------
// Oturumu olmayan ziyaretçinin ilk ekranı. index.html'den ayrı bir modül:
// ana betik küçük kalır, pano ekranı üzerinde çalışanlarla aynı dosyada
// çakışılmaz, oturumu açık kullanıcı bu dosyayı hiç indirmez.
//
// Sunucuya hiç dokunmaz. Hero'daki pano bir canlandırmadır (ekip ve kartlar
// uydurma), ama gösterdiği davranışların hepsi uygulamada gerçekten var:
// canlı taşıma, canlı yazma, çevrimiçi göstergesi, bildirim.
// Sayfada sosyal kanıt yok (müşteri logosu, yorum, kullanıcı sayısı): ürün
// yalnızca yaptığı şeyle anlatılıyor.
// -----------------------------------------------------------------------------

const REDUCE = typeof matchMedia === "function" && matchMedia("(prefers-reduced-motion: reduce)").matches;

/* ── Küçük yardımcılar ── */
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const av = (p, cls = "") => `<span class="lp-av ${cls}" style="background:${p.color}">${esc(p.initials)}</span>`;

/** Satır içi SVG ikonlar — uygulamadaki gibi 16'lık kutu, currentColor çizgi. */
const svg = (body, size = 16) =>
  `<svg width="${size}" height="${size}" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${body}</svg>`;
const IC = {
  arrow: svg(`<path d="M3 8h9.5M8.8 4.2L12.6 8l-3.8 3.8"/>`, 15),
  sync: svg(`<path d="M13 6.4A5.1 5.1 0 0 0 3.6 4.7M3 9.6a5.1 5.1 0 0 0 9.4 1.7"/><path d="M3.4 2.4v2.5h2.5M12.6 13.6v-2.5h-2.5"/>`),
  shield: svg(`<path d="M8 1.8l5 1.9v3.8c0 3.1-2.1 5.6-5 6.7-2.9-1.1-5-3.6-5-6.7V3.7l5-1.9z"/><path d="M5.8 8.1l1.6 1.6 3-3.1"/>`),
  rewind: svg(`<path d="M2.7 8a5.3 5.3 0 1 0 1.6-3.8"/><path d="M2.5 2.6v2.6h2.6"/><path d="M8 5.3V8l2 1.3"/>`),
  flow: svg(`<path d="M2 13.6h12"/><path d="M4.2 11.4V8.2M8 11.4V4.2M11.8 11.4V6.6"/>`),
  limit: svg(`<path d="M4 13.4V7.6M8 13.4V7.6M12 13.4V7.6"/><path d="M2 4.6h12" stroke-dasharray="1.8 1.8"/>`),
  invite: svg(`<rect x="2" y="3.4" width="12" height="9.2" rx="1.6"/><path d="M2.6 4.4L8 8.4l5.4-4"/>`),
  user: svg(`<circle cx="8" cy="5.5" r="2.8"/><path d="M2.8 14c.6-2.6 2.7-4 5.2-4s4.6 1.4 5.2 4"/>`),
  bell: svg(`<path d="M4 11.5V7a4 4 0 118 0v4.5l1.2 1.2H2.8L4 11.5zM6.5 13.5a1.5 1.5 0 003 0"/>`),
  check: svg(`<path d="M3 8.5l3.2 3L13 4.5"/>`, 11),
  rewindSm: svg(`<path d="M2.7 8a5.3 5.3 0 1 0 1.6-3.8"/><path d="M2.5 2.6v2.6h2.6"/>`, 11),
};
/** Uygulamanın logosu; renkler CSS'ten (.b baloncuk, .w çubuklar) gelir. */
const LOGO = `<svg class="lp-logo" viewBox="32 40 176 168" aria-hidden="true"><rect class="b" x="40" y="48" width="160" height="120" rx="28"/><path class="b" d="M84 166 L64 200 L116 166 Z"/><rect class="w" x="68" y="80" width="24" height="56" rx="10"/><rect x="108" y="72" width="24" height="64" rx="10" fill="#f5871f"/><rect class="w" x="148" y="80" width="24" height="56" rx="10" opacity=".65"/></svg>`;

/* ── Canlandırmanın ekibi ve kartları (uydurma) ── */
const PEOPLE = {
  ayse:  { name: "Ayşe",  initials: "AY", color: "#8b5cf6", rest: [0.15, 0.9] },
  mert:  { name: "Mert",  initials: "MK", color: "#4a7fc1", rest: [0.49, 0.93] },
  deniz: { name: "Deniz", initials: "DA", color: "#35b37e", rest: [0.83, 0.86] },
};
const COLUMNS = ["Yapılacaklar", "Devam Ediyor", "Tamam"];
const WIP = 3;   // ortadaki kolonun limiti — rozet "2 / 3" gibi dolar
const CARDS = {
  a: { title: "Ödeme sayfası", who: "ayse", due: ["Yarın", "soon"] },
  b: { title: "Onboarding e-postası", who: "deniz" },
  c: { title: "API hız sınırı", who: "mert", due: ["2 gün gecikti", "over"] },
  d: { title: "Giriş ekranı tasarımı", who: "ayse", due: ["12 Eki", ""] },
  e: { title: "Fatura PDF'i", who: "mert" },          // canlı yazılarak eklenir
};
const START = [["a", "b"], ["c"], ["d"]];
/** Hareket istemeyen kullanıcıya gösterilen tek kare: sahnenin ortasından bir an. */
const STILL = [["b", "e"], ["a", "c"], ["d"]];

/**
 * Tanıtım sayfasını root'a çizer.
 * onLogin / onRegister: giriş ekranını ilgili modda açar (index.html verir).
 * Dönen fonksiyon sayfayı söker; çağrılmasa da root başka bir ekranla
 * doldurulunca zamanlayıcılar ve dinleyiciler kendiliğinden durur.
 */
export function renderLanding(root, { onLogin, onRegister } = {}) {
  root.innerHTML = template();
  const page = root.querySelector(".lp");
  const cleanups = [];
  const alive = () => page.isConnected;
  const stop = () => { cleanups.splice(0).forEach((fn) => fn()); };

  // Düğmeler: giriş ekranına geçmeden önce sayfa başına dön (giriş ekranı kaydırılmış açılmasın)
  page.addEventListener("click", (e) => {
    const go = e.target.closest("[data-go]");
    if (!go) return;
    e.preventDefault();
    const where = go.dataset.go;
    if (where === "login" || where === "register") {
      stop();
      window.scrollTo(0, 0);
      (where === "login" ? onLogin : onRegister)?.();
      return;
    }
    document.getElementById(where)?.scrollIntoView({ behavior: REDUCE ? "auto" : "smooth", block: "start" });
  });

  // Üst çubuk hero'nun üstünde saydam; aşağı inince beyaz zemine geçer.
  const nav = page.querySelector(".lp-nav");
  const onScroll = () => {
    if (!alive()) return stop();
    nav.classList.toggle("solid", window.scrollY > 24);
  };
  window.addEventListener("scroll", onScroll, { passive: true });
  cleanups.push(() => window.removeEventListener("scroll", onScroll));
  onScroll();

  watchSections(page, cleanups);
  const demo = startDemo(page.querySelector(".lp-demo"), alive);
  cleanups.push(demo.stop);
  return stop;
}

/**
 * Bölümler görünür olunca belirir (.lp-in, bir kez); özellik görselleri
 * yalnızca ekrandayken oynar (.lp-run, girip çıktıkça). Ekran dışındaki
 * döngüler boşuna işlemci harcamasın.
 */
function watchSections(page, cleanups) {
  const reveals = page.querySelectorAll(".lp-reveal");
  if (REDUCE || typeof IntersectionObserver !== "function") {
    reveals.forEach((el) => el.classList.add("lp-in", "lp-run"));
    return;
  }
  const io = new IntersectionObserver((entries) => {
    for (const en of entries) {
      if (en.isIntersecting) en.target.classList.add("lp-in");
      en.target.classList.toggle("lp-run", en.isIntersecting);
    }
  }, { rootMargin: "0px 0px -48px 0px", threshold: 0 });
  reveals.forEach((el) => io.observe(el));
  cleanups.push(() => io.disconnect());
}

/* ══════════════════ Sayfa iskeleti ══════════════════ */
function template() {
  const P = PEOPLE;
  return `
<div class="lp">
  <header class="lp-nav"><div class="lp-wrap lp-nav-in">
    <button class="lp-brand" data-go="lp-top" aria-label="CollabBoard — sayfa başı">${LOGO}CollabBoard</button>
    <nav class="lp-links" aria-label="Sayfa içi">
      <a data-go="ozellikler">Özellikler</a>
      <a data-go="nasil">Nasıl çalışır</a>
    </nav>
    <div class="spacer"></div>
    <button class="lp-login" data-go="login">Giriş yap</button>
    <button class="btn btn-primary lp-start" data-go="register">Ücretsiz başla</button>
  </div></header>

  <main>
  <section class="lp-hero" id="lp-top">
    <div class="lp-wrap lp-hero-grid">
      <div class="lp-hero-copy">
        <span class="lp-pill lp-rise"><i></i>Gerçek zamanlı Kanban panosu</span>
        <h1 class="lp-rise" style="--d:.06s"><span>Ekibinle aynı panoda,</span> <span><em>aynı anda</em> çalış.</span></h1>
        <p class="lp-lead lp-rise" style="--d:.12s">Bir kişi kartı taşıdığında herkesin ekranında kayar. Yenilemek yok,
          tahmin etmek yok — kim çevrimiçi, kim ne yaptı, anında görünür.</p>
        <div class="lp-ctas lp-rise" style="--d:.18s">
          <button class="btn btn-primary lp-btn-lg" data-go="register">Ücretsiz başla ${IC.arrow}</button>
          <button class="btn lp-btn-lg lp-btn-dark" data-go="login">Giriş yap</button>
        </div>
        <ul class="lp-points lp-rise" style="--d:.24s">
          <li><i style="background:#35b37e"></i>Canlı senkron</li>
          <li><i style="background:#6ba4e8"></i>Çakışma koruması</li>
          <li><i style="background:#f5871f"></i>Geri sarılabilir geçmiş</li>
        </ul>
      </div>

      <div class="lp-demo lp-rise" style="--d:.2s" role="img"
           aria-label="Canlandırma: üç ekip arkadaşı aynı panoda kartları taşıyor ve yeni kart yazıyor; her hareket anında herkesin ekranına düşüyor.">
        <div class="lp-demo-bar" aria-hidden="true">
          <b>Ürün Yol Haritası</b><span class="badge OWNER">YÖNETİCİ</span>
          <div class="lp-presence">${av(P.ayse)}${av(P.mert)}${av(P.deniz)}</div>
          <span class="lp-live"><i></i>Canlı</span>
        </div>
        <div class="lp-board" aria-hidden="true">
          ${COLUMNS.map((c, i) => `<div class="lp-col"><div class="lp-col-head"><span>${c}</span><span class="chip" data-count="${i}">0</span></div></div>`).join("")}
          <div class="lp-layer"></div>
          <div class="lp-toasts"></div>
        </div>
      </div>
    </div>
  </section>

  <section class="lp-section" id="ozellikler">
    <div class="lp-wrap">
      <div class="lp-head lp-reveal">
        <div class="lp-eyebrow">Özellikler</div>
        <h2>Aynı anda çalışmanın zor kısımları, çözülmüş olarak.</h2>
        <p>Canlı güncelleme işin yalnızca yarısı. Aynı kartı iki kişi değiştirdiğinde, iş bir kolonda takıldığında
          ya da “dün bu pano nasıldı?” diye sorulduğunda da cevap panoda.</p>
      </div>

      <div class="lp-grid">
        <article class="lp-feat wide lp-reveal">
          <div class="lp-viz lp-v-sync" aria-hidden="true">
            ${screen(P.ayse, "Ayşe'nin ekranı", "")}
            <div class="lp-wire-box"><div class="lp-wire"><i></i></div><span class="lp-wire-l">WebSocket</span></div>
            ${screen(P.mert, "Mert'in ekranı", "lp-late")}
          </div>
          <div class="lp-feat-b">
            <h3><span class="lp-ico ok">${IC.sync}</span>Anında canlı senkron</h3>
            <p>Kart eklemek, taşımak, düzenlemek — her değişiklik panodaki herkese aynı anda ulaşır. Üstte kimin
              çevrimiçi olduğu, yanda kimin ne yaptığı görünür. Birden çok sunucu çalışsa bile yayın Redis üzerinden
              hepsine dağılır.</p>
          </div>
        </article>

        <article class="lp-feat lp-reveal" style="--d:.08s">
          <div class="lp-viz lp-v-conf" aria-hidden="true">
            <div class="lp-cf-card">Ödeme sayfası<span class="lp-ver"><b class="v4">v4</b><b class="v5">v5</b></span></div>
            <div class="lp-cf-op ok">${av(P.ayse)}<span class="tx"><b>Ayşe</b> v4 üzerine düzenledi</span><em>Kabul</em></div>
            <div class="lp-cf-op no">${av(P.mert)}<span class="tx"><b>Mert</b> v4 üzerine düzenledi</span><em>Reddedildi</em></div>
            <div class="lp-cf-toast"><i></i><div><b>Çakışma</b> · Bu kartı senden önce biri değiştirdi. Ekran güncellendi.</div></div>
          </div>
          <div class="lp-feat-b">
            <h3><span class="lp-ico blue">${IC.shield}</span>Çakışma koruması</h3>
            <p>İki kişi aynı kartı aynı anda değiştirirse sonraki sessizce ezmez. Her işlem gördüğü sürümü bildirir;
              eski sürüme dayanan işlem reddedilir ve ekran tazelenir.</p>
          </div>
        </article>

        <article class="lp-feat lp-reveal">
          <div class="lp-viz lp-v-time" aria-hidden="true">
            <div class="lp-tb-board">
              <div class="lp-tb-col"><div class="lp-mcard">Arama filtresi</div><div class="lp-mcard lp-tb-move">Ödeme sayfası</div></div>
              <div class="lp-tb-col"><div class="lp-mcard">API hız sınırı</div></div>
              <div class="lp-tb-col"><div class="lp-mcard">Giriş ekranı</div></div>
            </div>
            <div class="lp-tb-bar"><span class="lp-hist">${IC.rewindSm}GEÇMİŞ</span><div class="lp-track"><span class="lp-thumb-run"><i></i></span></div></div>
          </div>
          <div class="lp-feat-b">
            <h3><span class="lp-ico acc">${IC.rewind}</span>Zaman yolculuğu</h3>
            <p>Panonun herhangi bir andaki hâlini kaydırıcıyla geri sar. Her hareket olay kaydına yazılır; geçmiş o
              kayıttan yeniden kurulur ve salt okunur gösterilir.</p>
          </div>
        </article>

        <article class="lp-feat wide lp-reveal" style="--d:.08s">
          <div class="lp-viz lp-v-flow" aria-hidden="true">
            <div class="lp-panel">
              <div class="lp-panel-h">Akış sağlığı</div>
              <div class="lp-frow"><span class="nm">Yapılacaklar</span><span class="chip">4</span><span class="val">1,2 gün</span></div>
              <div class="lp-frow bn"><span class="nm">Devam Ediyor</span><span class="lp-tag">DARBOĞAZ</span><span class="chip">3</span><span class="val">3,8 gün</span></div>
              <div class="lp-frow"><span class="nm">İncelemede</span><span class="chip">1</span><span class="val">0,6 gün</span></div>
              <div class="lp-frow"><span class="nm">Tamam</span><span class="chip">9</span><span class="val">—</span></div>
            </div>
            <div class="lp-panel">
              <div class="lp-panel-h">Yaşlanan iş<span class="chip">3</span></div>
              <div class="lp-arow hot"><span class="nm">Ödeme entegrasyonu</span><span class="cl">Devam Ediyor</span><span class="bar" style="width:46px"></span><span class="val">8,5 gün</span></div>
              <div class="lp-arow warm"><span class="nm">Arama filtresi</span><span class="cl">İncelemede</span><span class="bar" style="width:23px;--d:.12s"></span><span class="val">4,1 gün</span></div>
              <div class="lp-arow"><span class="nm">Logo güncellemesi</span><span class="cl">Yapılacaklar</span><span class="bar" style="width:7px;--d:.24s"></span><span class="val">1,2 gün</span></div>
            </div>
          </div>
          <div class="lp-feat-b">
            <h3><span class="lp-ico err">${IC.flow}</span>Akış sağlığı: darboğaz ve yaşlanan iş</h3>
            <p>Kartların her kolonda ne kadar beklediği olay kaydından ölçülür. Ortalama beklemenin en uzun olduğu kolon
              darboğaz olarak işaretlenir; yaşlanan iş listesi hangi kartın takıldığını söyler.</p>
          </div>
        </article>

        <article class="lp-feat lp-reveal">
          <div class="lp-viz lp-v-wip" aria-hidden="true">
            <div class="lp-wcol">
              <div class="lp-wcol-h">Devam Ediyor<span class="chip">3 / 3</span></div>
              <div class="lp-mcard">Ödeme sayfası</div><div class="lp-mcard">API hız sınırı</div><div class="lp-mcard">Bildirim ayarları</div>
            </div>
            <div class="lp-wside"><div class="lp-wnew">+ Yeni iş</div><div class="lp-wnote">Limit dolu — önce bir işi bitir.</div></div>
          </div>
          <div class="lp-feat-b">
            <h3><span class="lp-ico amber">${IC.limit}</span>WIP limiti</h3>
            <p>Bir kolonda aynı anda kaç iş olabileceğini sınırla. Limit dolunca yeni kart giremez; ekip yeni iş
              başlatmak yerine elindekini bitirir.</p>
          </div>
        </article>

        <article class="lp-feat lp-reveal" style="--d:.08s">
          <div class="lp-viz lp-v-inv" aria-hidden="true">
            <div class="lp-bell">${IC.bell}<b>1</b></div>
            <div class="lp-istack">
              <div class="lp-itoast ask"><div class="row">${av(P.ayse)}<div><b>Ayşe Yılmaz</b> seni <b>Pazarlama</b> panosuna <b>Editör</b> olarak davet etti</div></div>
                <div class="lp-iacts"><span class="ok">${IC.check}Kabul et</span><span class="no">Reddet</span></div></div>
              <div class="lp-itoast yes"><b>Panoya katıldın</b><div>Pazarlama · <span class="lp-yes-l">Aç →</span></div></div>
            </div>
          </div>
          <div class="lp-feat-b">
            <h3><span class="lp-ico blue">${IC.invite}</span>Onaylı davet, canlı bildirim</h3>
            <p>Kimse habersizce bir panoya eklenmez: davet edilen kabul edince üye olur. Davet, kişi hangi ekrandaysa
              anında önüne düşer; Yönetici, Editör ya da İzleyici rolüyle.</p>
          </div>
        </article>

        <article class="lp-feat lp-reveal" style="--d:.16s">
          <div class="lp-viz lp-v-prof" aria-hidden="true">
            <div class="lp-crop">${av(P.ayse)}</div>
            <div class="lp-prof-i"><b>Ayşe Yılmaz</b><small>ayse@ornek.com</small>
              <div class="lp-zoom"><i></i></div>
              <div class="lp-prev">${av(P.ayse)}${av(P.ayse)}</div>
            </div>
          </div>
          <div class="lp-feat-b">
            <h3><span class="lp-ico acc">${IC.user}</span>Profil</h3>
            <p>Adını güncelle, şifreni değiştir, fotoğrafını kırpıp yükle. Fotoğrafın üye listesinde, hareketlerde ve
              bildirimlerde görünür.</p>
          </div>
        </article>
      </div>
    </div>
  </section>

  <section class="lp-section lp-how" id="nasil">
    <div class="lp-wrap">
      <div class="lp-head lp-reveal">
        <div class="lp-eyebrow">Nasıl çalışır</div>
        <h2>Üç adımda ekibinle aynı panodasın.</h2>
      </div>
      <ol class="lp-steps">
        <li class="lp-step lp-reveal"><span class="lp-num">1</span><h3>Hesabını oluştur</h3>
          <p>Ad, e-posta ve şifre yeter. Postadaki bağlantıyla adresini doğrula.</p></li>
        <li class="lp-step lp-reveal" style="--d:.1s"><span class="lp-num">2</span><h3>Panonu aç, ekibini davet et</h3>
          <p>Kolonlar hazır gelir. Ekip arkadaşlarını rolüyle davet et; onaylayınca panoya katılırlar.</p></li>
        <li class="lp-step lp-reveal" style="--d:.2s"><span class="lp-num">3</span><h3>Aynı anda çalışın</h3>
          <p>Kartları sürükle, düzenle, birine ata. Her değişiklik herkesin ekranında anında belirir.</p></li>
      </ol>
    </div>
  </section>

  <section class="lp-section">
    <div class="lp-wrap">
      <div class="lp-final lp-reveal">
        <div>
          <h2>Panonu aç, ekibini çağır.</h2>
          <p>Kayıt birkaç saniye sürer; kolonlar hazır gelir.</p>
          <div class="lp-ctas">
            <button class="btn btn-primary lp-btn-lg" data-go="register">Ücretsiz başla ${IC.arrow}</button>
            <button class="btn lp-btn-lg lp-btn-dark" data-go="login">Giriş yap</button>
          </div>
        </div>
        <div class="spacer"></div>
        <div class="board3d-wrap" aria-hidden="true"><div class="board3d">
          <div class="c"><div class="h"></div><div class="k" style="background:linear-gradient(150deg,#f5871f,#d9700f);opacity:1"></div><div class="k"></div></div>
          <div class="c"><div class="h"></div><div class="k" style="background:linear-gradient(150deg,#6ba4e8,#3f7cc4);opacity:1"></div><div class="k" style="opacity:.55"></div></div>
          <div class="c"><div class="h"></div><div class="k" style="background:linear-gradient(150deg,#35b37e,#229464);opacity:1"></div><div class="k" style="opacity:.8"></div></div>
        </div></div>
      </div>
    </div>
  </section>
  </main>

  <footer class="lp-foot"><div class="lp-wrap lp-foot-in">
    <span class="lp-brand">${LOGO}CollabBoard</span>
    <span>Gerçek zamanlı Kanban · Spring Boot, WebSocket (STOMP), Redis ve PostgreSQL üzerinde.</span>
    <nav aria-label="Hesap"><a data-go="login">Giriş yap</a><a data-go="register">Kayıt ol</a></nav>
  </div></footer>
</div>`;
}

/** Senkron görselindeki küçük ekran: iki kolon, taşınan kart ve yer değiştiren kart. */
function screen(person, label, cls) {
  return `<div class="lp-screen ${cls}">
    <div class="lp-screen-h">${av(person)}${label}</div>
    <div class="lp-mini">
      <div class="lp-mcol"><i class="lp-mhead"></i><div class="lp-mcard hot lp-sync-move">Ödeme sayfası</div><div class="lp-mcard lp-sync-follow">Arama filtresi</div></div>
      <div class="lp-mcol"><i class="lp-mhead"></i><div class="lp-mcard">API hız sınırı</div></div>
    </div>
  </div>`;
}

/* ══════════════════ Hero canlandırması ══════════════════
   Kartlar kolonların üstündeki tek bir katmanda, mutlak konumlu durur; yerleri
   yalnızca transform ile değişir. Bir kart taşınınca kolonlar yeniden
   hesaplanır, kartlar yeni yerlerine kayar — tarayıcı sayfayı yeniden dizmez.
   Sahne, ekran dışındayken ya da sekme arka plandayken bekler. */

const STOP = Symbol("dur");

function startDemo(demo, alive) {
  const board = demo.querySelector(".lp-board");
  const layer = demo.querySelector(".lp-layer");
  const toasts = demo.querySelector(".lp-toasts");
  const counts = [...demo.querySelectorAll("[data-count]")];
  const colEls = [...demo.querySelectorAll(".lp-col")];

  const cards = {};
  for (const [id, c] of Object.entries(CARDS)) {
    const el = document.createElement("div");
    el.className = "lp-card gone";
    const p = PEOPLE[c.who];
    el.innerHTML = `<div class="lp-card-in"><div class="lp-card-t">${esc(c.title)}</div>
      <div class="lp-card-m">${c.due ? `<span class="lp-due ${c.due[1]}">${esc(c.due[0])}</span>` : ""}${av(p)}</div></div>`;
    layer.appendChild(el);
    cards[id] = el;
  }
  const cursors = {};
  for (const [key, p] of Object.entries(PEOPLE)) {
    const el = document.createElement("div");
    el.className = "lp-cursor";
    el.innerHTML = `<svg width="18" height="20" viewBox="0 0 18 20"><path d="M2 1.5l13 7.2-5.8 1.6-3 5.6z" fill="${p.color}" stroke="#fff" stroke-width="1.5" stroke-linejoin="round"/></svg>
      <span style="background:${p.color}">${p.name}</span>`;
    layer.appendChild(el);
    cursors[key] = el;
  }

  const S = { cols: START.map((c) => [...c]), pos: {} };
  let paused = false, stopped = false, waiters = [];

  // ── Duraklatılabilir zaman ──
  const resume = () => { waiters.splice(0).forEach((r) => r()); };
  const sleep = async (ms) => {
    await new Promise((r) => setTimeout(r, ms));
    while (paused && !stopped) await new Promise((r) => waiters.push(r));
    if (stopped || !alive()) { stopped = true; throw STOP; }
  };

  // ── Yerleşim ──
  function geometry() {
    const W = board.clientWidth, H = board.clientHeight;
    const pad = parseFloat(getComputedStyle(board).paddingLeft) || 12;
    const gap = parseFloat(getComputedStyle(board).columnGap) || 10;
    const cw = (W - 2 * pad - 2 * gap) / 3;
    const head = colEls[0].querySelector(".lp-col-head").offsetHeight + 1;
    return { W, H, pad, gap, cw, head };
  }
  function layout() {
    const g = geometry();
    S.cols.forEach((ids, ci) => {
      const x = g.pad + ci * (g.cw + g.gap) + 7;
      let y = g.pad + g.head;
      ids.forEach((id) => {
        const el = cards[id];
        el.style.width = (g.cw - 14) + "px";
        S.pos[id] = { x, y, w: g.cw - 14, h: el.offsetHeight };
        el.style.transform = `translate(${x}px, ${y}px)`;
        el.classList.remove("gone");
        y += el.offsetHeight + 7;
      });
      counts[ci].textContent = ci === 1 ? `${ids.length} / ${WIP}` : String(ids.length);
      counts[ci].classList.toggle("full", ci === 1 && ids.length >= WIP);
    });
    for (const id of Object.keys(cards)) if (!S.cols.some((c) => c.includes(id))) cards[id].classList.add("gone");
  }
  /** Geçişsiz yerleşim: ilk çizim ve pencere boyutu değişince. */
  function snap() {
    layer.querySelectorAll(".lp-card, .lp-cursor").forEach((el) => { el.style.transition = "none"; });
    layout();
    for (const key of Object.keys(PEOPLE)) rest(key, false);
    void layer.offsetWidth;
    layer.querySelectorAll(".lp-card, .lp-cursor").forEach((el) => { el.style.transition = ""; });
  }
  const point = (key, x, y) => { cursors[key].style.transform = `translate(${Math.round(x)}px, ${Math.round(y)}px)`; };
  /** İmleci dinlenme yerine (biraz oynatarak) götür — boşta duran imleç de canlı görünsün. */
  function rest(key, jitter = true) {
    const g = geometry(), [fx, fy] = PEOPLE[key].rest;
    const j = jitter ? () => (Math.random() - 0.5) * 0.06 : () => 0;
    point(key, Math.min(g.W - 70, (fx + j()) * g.W), Math.min(g.H - 44, (fy + j() / 2) * g.H));
  }
  const at = (id) => { const p = S.pos[id]; return [p.x + p.w * 0.68, p.y + p.h * 0.55]; };

  function toast(key, title, body) {
    const t = document.createElement("div");
    t.className = "lp-toast";
    t.style.borderLeftColor = PEOPLE[key].color;
    t.innerHTML = `${av(PEOPLE[key])}<div><b>${esc(title)}</b><span>${esc(body)}</span></div>`;
    toasts.appendChild(t);
    while (toasts.children.length > 2) toasts.firstElementChild.remove();
    setTimeout(() => { t.classList.add("out"); setTimeout(() => t.remove(), 320); }, 2600);
  }
  function flash(id) {
    const el = cards[id];
    el.classList.remove("flash"); void el.offsetWidth; el.classList.add("flash");
  }

  // ── Hareketler ──
  async function drag(key, id, toCol, toIdx, note) {
    for (const other of Object.keys(PEOPLE)) if (other !== key) rest(other);
    point(key, ...at(id));
    await sleep(850);
    const el = cards[id];
    el.classList.add("lift");
    await sleep(220);
    const from = S.cols.findIndex((c) => c.includes(id));
    S.cols[from].splice(S.cols[from].indexOf(id), 1);
    S.cols[toCol].splice(toIdx, 0, id);
    el.classList.add("drag");
    cursors[key].classList.add("drag");
    layout();
    point(key, ...at(id));
    await sleep(950);
    el.classList.remove("lift", "drag");
    cursors[key].classList.remove("drag");
    flash(id);
    toast(key, `${PEOPLE[key].name} kartı taşıdı`, note);
  }

  async function type(key, id, col, text) {
    for (const other of Object.keys(PEOPLE)) if (other !== key) rest(other);
    const el = cards[id];
    const title = el.querySelector(".lp-card-t");
    title.textContent = "";
    el.classList.add("typing");
    S.cols[col].push(id);
    layout();
    const p = S.pos[id];
    point(key, p.x + 18, p.y + p.h - 6);
    await sleep(900);
    for (const ch of text) {
      title.textContent += ch;
      await sleep(65 + Math.random() * 70);
    }
    await sleep(420);
    el.classList.remove("typing");
    flash(id);
    toast(key, `${PEOPLE[key].name} kart ekledi`, `${text} · ${COLUMNS[col]}`);
  }

  async function scene() {
    S.cols = START.map((c) => [...c]);
    cards.e.querySelector(".lp-card-t").textContent = CARDS.e.title;
    snap();
    layer.classList.remove("fade");
    await sleep(1100);
    await drag("ayse", "a", 1, 0, "Ödeme sayfası → Devam Ediyor");
    await sleep(1300);
    await type("mert", "e", 0, CARDS.e.title);
    await sleep(1300);
    await drag("deniz", "c", 2, 0, "API hız sınırı → Tamam");
    await sleep(1200);
    await drag("ayse", "b", 1, 1, "Onboarding e-postası → Devam Ediyor");
    await sleep(2600);
    layer.classList.add("fade");   // döngü başa dönerken yumuşak geçiş
    await sleep(500);
  }

  // ── Başlat ──
  if (REDUCE) {
    // Tek kare: sahnenin ortasından bir an, imleçler yerinde, bir bildirim açık.
    S.cols = STILL.map((c) => [...c]);
    snap();
    point("ayse", ...at("a"));
    point("mert", ...at("e"));
    point("deniz", ...at("d"));
    const t = document.createElement("div");
    t.className = "lp-toast";
    t.style.borderLeftColor = PEOPLE.ayse.color;
    t.innerHTML = `${av(PEOPLE.ayse)}<div><b>Ayşe kartı taşıdı</b><span>Ödeme sayfası → Devam Ediyor</span></div>`;
    toasts.appendChild(t);
    return { stop() {} };
  }

  snap();
  const ro = typeof ResizeObserver === "function" ? new ResizeObserver(() => alive() && snap()) : null;
  ro?.observe(board);

  // Ekranda değilse ya da sekme gizliyse sahne bekler.
  let visible = true;
  const sync = () => {
    const was = paused;
    paused = !visible || document.visibilityState === "hidden";
    if (was && !paused) resume();
  };
  const io = typeof IntersectionObserver === "function"
    ? new IntersectionObserver(([en]) => { visible = en.isIntersecting; sync(); }) : null;
  io?.observe(demo);
  document.addEventListener("visibilitychange", sync);

  (async () => {
    try { for (;;) await scene(); }
    catch (e) { if (e !== STOP) console.error(e); }
    finally { cleanup(); }
  })();

  function cleanup() {
    ro?.disconnect(); io?.disconnect();
    document.removeEventListener("visibilitychange", sync);
  }
  return { stop() { stopped = true; resume(); cleanup(); } };
}
