// Sturnio — ziyaretçi analitiği: Umami, çerezsiz (ADR 0024)
// -----------------------------------------------------------------------------
// Sunucu ANALYTICS_UMAMI_WEBSITE_ID verilmişse sayfaya Umami'nin betiğini
// ekler; verilmemişse hiçbir şey yüklenmez, Umami'ye tek istek gitmez.
//
// Betiğin ayarları:
//   data-exclude-search  adresin ?... kısmı gönderilmez. Bağlantılarda kart ve
//                        pano numarası, doğrulama ve sıfırlama jetonları var.
//   data-exclude-hash    #... kısmı da gönderilmez.
//   data-do-not-track    tarayıcısında "izlenmek istemiyorum" açık olan sayılmaz.
//
// Olaylar (track): yalnızca olayın adı ve kişiyi tanıtmayan bir ayrıntı
// ("register" / "login" gibi). E-posta, ad, kimlik numarası ASLA gönderilmez.
// -----------------------------------------------------------------------------

let state = "off";      // off | loading | on
let started = null;
const queue = [];       // betik yüklenirken gelen olaylar (en fazla 20)

/**
 * Ayarı sunucudan okur ve açıksa betiği ekler. Birden çok çağrılsa da bir
 * kez çalışır. Hata ya da kapalı ayar sayfayı etkilemez.
 */
export function initAnalytics() {
  if (!started) {
    started = fetch("/api/public/config", { headers: { Accept: "application/json" } })
      .then((r) => (r.ok ? r.json() : null))
      .then((cfg) => {
        const a = cfg && cfg.analytics;
        if (!a || !a.umamiWebsiteId || !a.scriptUrl) return false;
        const s = document.createElement("script");
        s.defer = true;
        s.src = a.scriptUrl;
        s.dataset.websiteId = a.umamiWebsiteId;
        s.dataset.excludeSearch = "true";
        s.dataset.excludeHash = "true";
        s.dataset.doNotTrack = "true";
        s.onload = () => { state = "on"; queue.splice(0).forEach(([n, d]) => send(n, d)); };
        s.onerror = () => { state = "off"; queue.length = 0; };   // engelleyici eklenti ya da ağ
        state = "loading";
        document.head.appendChild(s);
        return true;
      })
      .catch(() => false);
  }
  return started;
}

/**
 * Özel olay. Analitik kapalıysa hiçbir şey yapmaz; betik henüz yükleniyorsa
 * olay sıraya alınır, yüklenince gider.
 *
 * @param {string} name "signup", "signup-google", "cta-start"
 * @param {Object} [data] kişiyi tanıtmayan küçük bir ayrıntı, ör. { place: "hero" }
 */
export function track(name, data) {
  if (state === "on") send(name, data);
  else if (state === "loading" && queue.length < 20) queue.push([name, data]);
}

function send(name, data) {
  try {
    const u = window.umami;
    if (u && typeof u.track === "function") u.track(name, data);
  } catch (_) { /* analitik hiçbir zaman sayfayı bozmaz */ }
}
