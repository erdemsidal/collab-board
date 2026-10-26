// Sturnio — arayüzün dilleri (ADR 0020)
// -----------------------------------------------------------------------------
// Arayüzde görünen her metin burada, dil başına bir sözlükte. Ekran kodu metin
// yazmaz, t("ekran.anahtar", { parametre }) çağırır.
//
// Dil seçimi: kişinin sunucudaki tercihi (giriş yapınca index.html verir) →
// tarayıcıda saklanan seçim → tarayıcının dilleri (desteklenen ilk eşleşme,
// yoksa İngilizce). <html lang> seçimi izler.
//
// Üçüncü bir dil: aşağıya bir sözlük daha (ör. `de`) ve DICTIONARIES'e bir satır.
// Dil düğmesi, tarih ve sayı biçimleri kendiliğinden gelir. İngilizcede olmayan
// anahtar Türkçesine düşer ve konsola bir kez yazılır.
//
// Sözlük metinleri güvenilir kabul edilir (içlerinde <b> olabilir); kişiden gelen
// değerler (ad, pano adı) parametre olarak verilmeden önce çağıran tarafta kaçışlanır.
// -----------------------------------------------------------------------------

/** Ürünün görünen adı: arayüzdeki tek yeri burası. Sunucudaki karşılığı app.brand-name. */
export const BRAND = "Sturnio";

/** Logo: sağa bakan sığırcık (64'lük kutuda; göz delik, fill-rule="evenodd"). Dosyaları: docs/marka/. */
export const BRAND_MARK = "M61 19.5L47.5 17.2C44 12.8 37.5 12.6 33.6 16.4C28.6 21.4 22.6 28.6 14.4 35.6L3.5 43L6.5 49.5L16.8 48.6C25.2 47.2 34.4 43.6 40.4 37.6C44.4 33.6 47 29.4 48.4 24.6ZM43.5 20A1.9 1.9 0 1 0 39.7 20A1.9 1.9 0 1 0 43.5 20Z";

/* ══════════════════ Türkçe ══════════════════ */
const tr = {
  meta: { code: "TR", name: "Türkçe" },

  common: {
    cancel: "Vazgeç", save: "Kaydet", close: "Kapat", delete: "Sil", create: "Oluştur", add: "Ekle",
    remove: "Kaldır", retry: "Tekrar dene", loading: "Yükleniyor…", accept: "Kabul et", decline: "Reddet",
    signIn: "Giriş yap", signUp: "Kayıt ol", signOut: "Çıkış yap", someone: "Biri",
    email: "E-posta", emailPlaceholder: "ornek@mail.com", firstName: "Ad", lastName: "Soyad", password: "Şifre",
    newPassword: "Yeni şifre", newPasswordAgain: "Yeni şifre (tekrar)", minPassword: "En az 8 karakter",
    rename: "Yeniden adlandır", home: "Ana ekran", profile: "Profilim", notifications: "Bildirimler", search: "Ara",
    deletedBoard: "silinmiş bir pano", deletedCard: "silinmiş bir kart", deletedUser: "Silinmiş kullanıcı",
    noValue: "–",
  },

  lang: { label: "Dil", saveFailed: "Dil tercihi kaydedilemedi" },

  time: {
    now: "şimdi", today: "Bugün", tomorrow: "Yarın", yesterday: "Dün", daysLate: "{count} gün gecikti",
  },

  role: {
    badge: { OWNER: "YÖNETİCİ", ADMIN: "YÖNETİCİ", EDITOR: "EDİTÖR", MEMBER: "ÜYE", VIEWER: "İZLEYİCİ", GUEST: "MİSAFİR" },
    name: { OWNER: "Yönetici", EDITOR: "Editör", VIEWER: "İzleyici" },
    /** Cümle içinde: "... {role} olarak davet etti". */
    as: { OWNER: "Yönetici", EDITOR: "Editör", VIEWER: "İzleyici" },
  },

  api: {
    requestFailed: "İstek başarısız ({status})",
    uploadFailed: "Yükleme başarısız ({status})",
    refreshFailed: "Oturum yenilenemedi ({status})",
  },

  theme: {
    toggle: "Temayı değiştir", toDark: "Koyu temaya geç", toLight: "Açık temaya geç",
    dark: "Koyu tema", light: "Açık tema",
  },

  session: {
    closed: "Oturum kapandı", closedElsewhere: "Başka bir sekmede çıkış yapıldı.",
    expired: "Oturumun sona erdi", signInAgain: "Tekrar giriş yap.",
  },

  auth: {
    home: "Ana sayfa",
    registerTitle: "Hesap oluştur", loginTitle: "Tekrar hoş geldin",
    registerSub: "Birkaç saniye sürer.", loginSub: "Panolarına devam etmek için giriş yap.",
    google: "Google ile devam et", orEmail: "ya da e-postayla",
    firstNamePh: "Adın", lastNamePh: "Soyadın", passwordPh: "Şifren",
    forgot: "Şifremi unuttum", resendPrompt: "Bağlantı çalışmadı mı?", resendLink: "Yenisini gönder",
    haveAccount: "Hesabın var mı?", noAccount: "Hesabın yok mu?",
    forgotTitle: "Şifreni mi unuttun?",
    forgotSub: "Hesabının e-posta adresini yaz, sana yeni şifre belirleme bağlantısı gönderelim.",
    sendLink: "Bağlantı gönder", remembered: "Hatırladın mı?",
    checkInbox: "Gelen kutunu kontrol et",
    resetSentSub: "Bu adres kayıtlıysa şifre sıfırlama bağlantısı gönderdik:",
    resetStep1: "Postadaki <b>Şifremi sıfırla</b> düğmesine tıkla",
    resetStep2: "Bağlantı <b>30 dakika</b> geçerli ve tek kullanımlık",
    backToLogin: "Giriş ekranına dön",
    resetTitle: "Yeni şifreni belirle", resetSub: "Şifren değişince tüm cihazlardaki oturumların kapatılır.",
    updatePassword: "Şifremi güncelle",
    verifySentSub: "Doğrulama bağlantısını şu adrese gönderdik:",
    verifyStep1: "Postadaki <b>Hesabımı doğrula</b> düğmesine tıkla",
    verifyStep2: "Doğrulandıktan sonra buradan giriş yap",
    resendMail: "Postayı yeniden gönder", verifiedQ: "Doğruladın mı?",
    err: {
      emailRequired: "E-posta adresini gir.", emailFirst: "Önce e-posta adresini gir.",
      sendFailed: "Gönderilemedi, biraz sonra tekrar dene.",
      passwordShortDot: "Şifre en az 8 karakter olmalı.", passwordShort: "Şifre en az 8 karakter olmalı",
      passwordMismatch: "İki şifre birbirini tutmuyor.", resetFailed: "Şifre güncellenemedi.",
      verifyFailed: "Doğrulama bağlantısı işlenemedi.",
      nameRequired: "Ad ve soyad zorunludur", credentialsRequired: "E-posta ve şifre zorunludur",
      loginFailed: "Giriş başarısız",
    },
    oauth: {
      cancelled: "Google ile giriş iptal edildi.",
      email_unverified: "Google hesabının e-posta adresi doğrulanmamış. E-postanla kayıt olabilirsin.",
      failed: "Google ile giriş yapılamadı. Biraz sonra tekrar dene.",
    },
  },

  topbar: {
    account: "Hesabım", pickWorkspace: "Çalışma alanı seç",
    search: "Panolarda, kartlarda ve komutlarda ara", searchPh: "Panolarda, kartlarda ve komutlarda ara…",
    new: "Yeni", sharedWithMe: "Benimle paylaşılanlar", allSpaces: "Tüm alanlar", allBoards: "Tüm panolar",
    newWorkspace: "Yeni çalışma alanı", newBoard: "Yeni pano", inviteTeam: "Ekibini davet et",
    onlineTitle: "{names} şu an bir panoda", online: "{count} çevrimiçi",
  },

  notif: {
    BOARD_INVITE: "{who} seni {board} panosuna {role} olarak davet etti",
    INVITE_ACCEPTED: "{who} davetini kabul etti · {board}",
    INVITE_DECLINED: "{who} davetini reddetti · {board}",
    INVITE_CANCELLED: "{who} davetini geri çekti · {board}",
    COMMENT_MENTION: "{who} seni bir yorumda andı: {card}",
    CARD_COMMENT: "{who} sana atanan karta yorum yazdı: {card}",
    boardMissing: "Pano bulunamadı", maybeDeleted: "Silinmiş olabilir.", cardDeleted: "Bu kart silinmiş",
    markAllRead: "Tümünü okundu say", loadFailed: "Bildirimler yüklenemedi", checkConnection: "Bağlantını kontrol et.",
    empty: "Bildirimin yok",
    emptyHint: "Biri seni bir panoya davet ettiğinde ya da bir yorumda andığında burada görünür.",
    accepted: "Kabul ettin", goToBoard: "Panoya git →", declined: "Reddettin", withdrawn: "Davet geri çekildi",
    inviteTitle: "Pano daveti", decideLater: "Sonra karar ver",
    joined: "Panoya katıldın", openBoard: "Panoyu aç →", inviteDeclined: "Davet reddedildi",
    answerFailed: "Yanıtlanamadı", inviteAccepted: "Davet kabul edildi", openCard: "Kartı aç",
    mentionedYou: "{who} seni bir yorumda andı", commentOnAssigned: "Sana atanan karta yorum yazıldı",
  },

  profile: {
    title: "Profilim", changePhoto: "Fotoğrafı değiştir", uploadPhoto: "Fotoğraf yükle",
    memberSince: "Üyelik: {since}", workspaces: "{count} çalışma alanı", boards: "{count} pano",
    googleLinked: "Google hesabı bağlı", photoRemoved: "Fotoğraf kaldırıldı", removeFailed: "Kaldırılamadı",
    personal: "Kişisel bilgiler",
    emailLocked: "E-posta adresi değiştirilemez. Girişte ve davetlerde hesabını tanıyan adres bu.",
    setPassword: "Şifre belirle", security: "Güvenlik", currentPassword: "Mevcut şifre",
    noPasswordNote: "Hesabın Google ile açıldı. Şifre belirlersen e-postanla da giriş yapabilirsin.",
    passwordNote: "Şifren değişince diğer cihazlardaki oturumların kapatılır, bu cihazda oturumun açık kalır.",
    changePassword: "Şifreyi değiştir", updated: "Profil güncellendi", saveFailed: "Kaydedilemedi",
    language: "Dil", languageHint: "Arayüz ve sana gelen e-postalar bu dilde.",
    passwordSet: "Şifren belirlendi", passwordSetHint: "Artık e-postanla da giriş yapabilirsin.",
    passwordChanged: "Şifren değişti", passwordChangedHint: "Diğer cihazlardaki oturumların kapatıldı.",
    err: {
      currentRequired: "Mevcut şifreni gir.", newShort: "Yeni şifre en az 8 karakter olmalı.",
      mismatch: "İki yeni şifre birbirini tutmuyor.", same: "Yeni şifre mevcut şifrenle aynı olamaz.",
      changeFailed: "Şifre değiştirilemedi.",
    },
  },

  photo: {
    unsupported: "Desteklenmeyen dosya", pickType: "PNG, JPEG ya da WebP seç.",
    tooLarge: "Dosya çok büyük", max: "En fazla 5 MB.",
    openFailed: "Görsel açılamadı", corrupt: "Dosya bozuk olabilir.",
    title: "Profil fotoğrafı", hint: "Sürükleyerek konumla, kaydırıcıyla yakınlaştır.",
    preview: "Profilde ve üye listesinde böyle görünecek",
    updated: "Fotoğraf güncellendi", uploadFailed: "Yüklenemedi",
  },

  home: {
    loadFailed: "Yüklenemedi", sideNav: "Yan menü", workspaces: "Çalışma alanları",
    newWorkspace: "+ Yeni çalışma alanı", overview: "Özet", boards: "Panolar", team: "Ekip",
    recent: "Son açılanlar", hereNow: "Şu an panoda olan kişi",
    guestTitle: "Bu alana erişimin yok",
    guestHint: "Misafir rolündesin; yalnızca ayrıca davet edildiğin panoları görebilirsin.",
    noBoards: "Henüz pano yok", noBoardsHint: "İlk panonu oluştur ya da bir panoya davet edilmeyi bekle.",
    firstBoard: "İlk panoyu oluştur",
    greet: { morning: "Günaydın", day: "İyi günler", evening: "İyi akşamlar" },
    lateCards: "{count} kartın gecikti", dueToday: "{count} kartın bugün bitiyor", nothingUrgent: "acil bir işin yok",
    overviewFailed: "Özet yüklenemedi.",
    openOnBoard: "{board} panosunda aç", noDate: "Tarih yok",
    assigned: "Bana atanan kartlar", openCount: "{count} açık",
    filter: { all: "Tümü", week: "Bu hafta", late: "Geciken" },
    moreCards: "+{count} kart daha · son tarihi en yakın olanlar gösteriliyor",
    noMatch: "Bu süzgece uyan kart yok", noLate: "Gecikmiş kartın yok.",
    noWeek: "Önümüzdeki 7 günde bitmesi gereken kartın yok.",
    noAssigned: "Sana atanmış açık kart yok",
    noAssignedHint: "Bir kart sana atandığında burada, son tarihine göre sıralı görünür.",
    liveNow: "Şu an canlı",
    liveEmpty: "Şu an panolarında senden başka kimse yok. Biri bir panoyu açtığında burada canlı görünür.",
    aging: "Yaşlanan işler", agingEstimated: "Kolona giriş anı kayıtta yok; süre en az bu kadar",
    agingWaiting: "Bu kolonda bekliyor",
    agingEmpty: "Açık kartın yok ya da hiçbiri bir kolonda uzun süredir beklemiyor.",
    openBoard: "{board} panosunu aç", recentActivity: "Son hareketler",
    activityEmpty: "Panolarında kart eklendiğinde ya da taşındığında burada görünür.",
    moreInvites: "+{count} davet daha",
    newBoardTile: "+ Yeni pano", yourBoards: "Panoların",
    scope: { all: "Tümü", mine: "Benim", shared: "Paylaşılan" },
    sharedWithYouIn: "{ws} · seninle paylaşıldı", sharedWithYou: "Seninle paylaşıldı",
    cards: "{count} kart", members: "{count} üye", peopleHere: "● {count} kişi burada",
    yourTeam: "Ekibin", invite: "Davet et →", online: "Çevrimiçi", offline: "Çevrimdışı",
    onBoard: "{board} panosunda", onlineLower: "çevrimiçi", offlineLower: "çevrimdışı",
    teamEmpty: "Henüz kimseyle pano paylaşmıyorsun. Davet ettiğin kişiler burada görünür.",
    renamed: "Ad güncellendi", updateFailed: "Güncellenemedi",
    deleteWsTitle: "“{name}” silinsin mi?",
    deleteWsBody: "İçindeki {count} pano, kartlar ve geçmiş kalıcı olarak silinir. Bu işlem geri alınamaz.",
    wsDeleted: "Çalışma alanı silindi", wsDeletedBody: "{name} ve içindeki panolar kaldırıldı",
    deleteFailed: "Silinemedi",
  },

  create: {
    wsTitle: "Yeni çalışma alanı", boardTitle: "Yeni pano",
    wsSub: "Ekibin için ortak bir alan oluştur.",
    boardSub: "Bir şablonla başla. Kolonları sonra değiştirebilirsin.",
    wsPh: "ör. Acme Yazılım", boardPh: "ör. Ürün Yol Haritası", name: "Ad", workspace: "Çalışma alanı",
    failed: "Oluşturulamadı",
    template: "Şablon", blankHint: "Kolonları kendin eklersin", wip: "WIP limiti: {limit}",
    templatesFailed: "Şablonlar yüklenemedi. Pano Basit Kanban ile açılır.",
  },

  board: {
    noAccess: "Erişim yok", noAccessBody: "Bu panoya erişim yetkin yok.", loadFailed: "Pano yüklenemedi",
    cardMissing: "Kart bulunamadı", cardMissingBody: "Silinmiş ya da taşınmış olabilir.",
    disconnected: "Bağlantı koptu", reconnecting: "Yeniden bağlanmaya çalışılıyor…",
    reconnected: "Yeniden bağlandı", refreshed: "Pano tazelendi.",
    columnFull: "Kolon dolu", columnFullBody: "Bu kolon WIP limitine ulaştı.",
    invalid: "Geçersiz", invalidBody: "Girdi kabul edilmedi.",
    conflict: "Çakışma", conflictBody: "İşlemin reddedildi.",
    back: "Ana ekrana dön", location: "Konum", wsBoards: "{ws} alanının panoları", switch: "Başka panoya geç",
    view: "Görünüm", tab: { board: "Pano", history: "Geçmiş", flow: "Akış" },
    hereNow: "Bu panoda şu an olanlar", inviteTitle: "Bu panoya davet et", invite: "Davet et",
    panel: "Pano paneli", members: "Üyeler", addInvite: "+ Davet et", recentActivity: "Son hareketler",
    listLoading: "Pano listesi yükleniyor…", allBoards: "Tüm panolar",
    viewerTitle: "İzleyici modundasın",
    viewerBody: "Panoyu görebilir ama değiştiremezsin. Değişiklikler canlı olarak sana da yansır.",
    filterCount: "{shown} / {total} kart · sürükleme kapalı",
  },

  filter: {
    everyone: "Herkes", searchPh: "Bu panoda ara…", search: "Bu panoda ara", mine: "Bana atananlar",
    late: "Geciken", lateCount: "Geciken · {count}", lateTitle: "Son tarihi geçmiş, henüz bitmemiş kartlar",
    person: "Kişi:", personTitle: "Atanan kişiye göre süz", clear: "Temizle",
    togglePanel: "Yan paneli gizle / göster",
  },

  stats: {
    cards: "<b>{count}</b> kart",
    bottleneck: "Darboğaz:", bottleneckTitle: "Kartların en uzun beklediği kolon",
    cycle: "Ort. çevrim:", cycleTitle: "Bir kartın ilk kolondan son kolona ortalama yolculuğu",
  },

  conn: {
    liveCount: "● Canlı · {count} kişi", live: "● Canlı", lost: "Bağlantı kesildi", connecting: "Bağlanıyor…",
    pillLive: "CANLI", pillOff: "KAPALI", pillConnecting: "BAĞLANIYOR",
  },

  column: {
    actions: "Kolon işlemleri",
    wipFull: "Bu kolon WIP limitine ulaştı. Yeni kart almadan önce buradaki bir işi tamamla.",
    wipOver: "Limitin üzerinde. Kolon boşalana kadar yeni kart kabul etmeyecek.",
    noMatch: "Süzgece uyan kart yok", empty: "Henüz kart yok", addCard: "Kart ekle", addColumn: "Kolon ekle",
    wipLimit: "WIP limiti…", delete: "Kolonu sil",
    deleteTitle: "{name} kolonu silinsin mi?",
    deleteCards: "İçindeki <b>{count} kart</b> da kalıcı olarak silinecek.",
    isEmpty: "Kolon boş.", deleteNote: "Bu işlem geri alınamaz, ama panonun geçmişinde kayıtlı kalır.",
    namePh: "Kolon adı…",
  },

  wip: {
    label: "WIP limiti: {limit}", clickToChange: " (değiştirmek için tıkla)",
    noneEditable: "WIP limiti yok, ayarlamak için tıkla", none: "WIP limiti yok",
    title: "{name} için WIP limiti",
    sub: "Bu kolonda aynı anda en fazla kaç kart bulunabilir? Limit dolduğunda kolon yeni kart kabul etmez; böylece yeni iş başlatmak yerine mevcut işi bitirirsin.",
    limit: "Limit", placeholder: "Boş = sınırsız", remove: "Sınırı kaldır",
    invalid: "Geçersiz limit", invalidBody: "En az 1 olmalı, ya da sınırı kaldır.",
  },

  card: {
    hasDescription: "Açıklaması var", comments: "{count} yorum", due: "Son tarih: {date}",
    composerPh: "Kart başlığı…", composerHint: "Enter ile ekle",
  },

  detail: {
    unassigned: "Atanmamış", description: "Açıklama",
    descriptionPh: "Bu iş tam olarak ne? Nasıl bitmiş sayılır?",
    assignee: "Atanan", due: "Son tarih", column: "Kolon", position: "Konum", version: "Sürüm",
    deleteCard: "Kartı sil", titleRequired: "Başlık boş olamaz",
  },

  /** Kartın kısa anahtarı (STR-42), dal adı ve paylaşılan bağlantı (ADR 0021). */
  cardKey: {
    title: "Kartın anahtarı. Kart taşınsa da değişmez.",
    copy: "Kopyala", copyLabel: "Anahtarı, dal adını ya da bağlantıyı kopyala",
    copyKey: "Anahtarı kopyala", copyBranch: "Dal adını kopyala", copyLink: "Bağlantıyı kopyala",
    keyCopied: "Anahtar kopyalandı", branchCopied: "Dal adı kopyalandı", linkCopied: "Bağlantı kopyalandı",
    copyFailed: "Kopyalanamadı", copyFailedBody: "Tarayıcı kopyalamaya izin vermedi.",
    notFoundBody: "{key} diye bir kart yok ya da o panoya erişimin yok.",
  },

  comments: {
    title: "Yorumlar", placeholder: "Yorum yaz…", write: "Yorum yaz", send: "Gönder",
    hint: "@ ile birini an · {mod}+Enter ile gönder",
    loading: "Yorumlar yükleniyor…", cardDeleted: "Bu kart silinmiş.", loadFailed: "Yorumlar yüklenemedi.",
    empty: "Henüz yorum yok.", edited: "(düzenlendi)", edit: "Düzenle",
    confirmDelete: "Yorum silinsin mi?", deleteFailed: "Yorum silinemedi",
    editLabel: "Yorumu düzenle", saveHint: "{mod}+Enter ile kaydet",
    required: "Yorum boş olamaz.", saveFailed: "Yorum kaydedilemedi.", sendFailed: "Yorum gönderilemedi.",
    mentionList: "Anılacak kişi",
  },

  members: {
    here: "Şu an panoda", notHere: "Panoda değil", you: " (sen)", remove: "Çıkar",
    removed: "Üye çıkarıldı", removeFailed: "Çıkarılamadı",
    pending: "Bekleyen davetler · {count}", noAccount: "Hesabı yok · kayıt olunca görecek",
    pendingBadge: "BEKLİYOR", withdraw: "Daveti geri çek", withdrawn: "Davet geri çekildi",
    withdrawFailed: "Geri çekilemedi",
    removedYou: "Panodan çıkarıldın", roleChanged: "Rolün değişti", roleChangedBody: "{board} panosunda artık {role}",
  },

  history: {
    loadFailed: "Geçmiş alınamadı", empty: "Geçmiş boş",
    emptyBody: "Bu panoda henüz geri sarılabilecek bir hareket yok.",
    pill: "GEÇMİŞ", backToNow: "Bugüne dön", jumpFailed: "O ana gidilemedi",
  },

  flow: {
    title: "Akış", sub: "Kartların kolonlarda ne kadar beklediği ve işin nerede tıkandığı.",
    cycle: "Ortalama çevrim süresi", cycleHint: "ilk kolondan son kolona",
    bottleneck: "Darboğaz", none: "Yok", bottleneckHint: "kartlar burada ort. {duration} bekliyor",
    noBottleneck: "belirgin bir tıkanma yok", open: "Açık kart", openHint: "son kolonda olmayanlar",
    dwell: "Kolonlarda ortalama bekleme", cards: "{count} kart", cardsWip: "{count} / {wip} kart",
    redNote: "Kırmızı kolon, kartların en uzun beklediği yer.",
    aging: "Yaşlanan işler", agingEmpty: "Bekleyen açık kart yok.",
  },

  /** Geçmiş satırları: sunucu türü ve parçaları (params) verir, cümle burada kurulur. */
  activity: {
    empty: "Henüz hareket yok", emptyHint: "Bir kart ekleyip taşıdığında burada görünür.",
    count: "{count} hareket", hide: "Gizle", showAll: "Tümünü gör",
    ADD_CARD: "'{card}' kartını {column} kolonuna ekledi",
    MOVE_CARD: "'{card}' kartını {column} kolonuna taşıdı",
    EDIT_CARD: "'{card}' kartının detaylarını güncelledi",
    EDIT_CARD_TITLE: "'{from}' kartını '{card}' olarak düzenledi",
    DELETE_CARD: "'{card}' kartını sildi",
    ADD_COLUMN: "{column} kolonunu ekledi",
    RENAME_COLUMN: "{from} kolonunu {column} olarak adlandırdı",
    DELETE_COLUMN: "{column} kolonunu sildi",
    MOVE_COLUMN: "{column} kolonunu {position}. sıraya taşıdı",
    SET_WIP_LIMIT: "{column} kolonunun WIP limitini {limit} yaptı",
    SET_WIP_LIMIT_OFF: "{column} kolonunun WIP limitini kaldırdı",
    ADD_COMMENT: "'{card}' kartına yorum yazdı",
    MEMBER_JOINED: "panoya {role} olarak katıldı",
  },

  palette: {
    label: "Ara ve komut çalıştır", placeholder: "Pano, kart ya da komut ara…",
    move: "gez", open: "aç", close: "kapat",
    goHome: "Ana ekrana git", newBoard: "Yeni pano oluştur", newWorkspace: "Yeni çalışma alanı",
    invite: "Ekibini davet et", newCard: "Yeni kart ekle", history: "Geçmişte gez", flow: "Akış ölçümünü aç",
    recent: "Son açılanlar", boards: "Panolar", cards: "Kartlar", commands: "Komutlar",
    searching: "Aranıyor…", noResults: "“{q}” için sonuç yok", searchingCards: "Kartlarda aranıyor…",
  },

  invite: {
    needBoard: "Önce bir pano gerekli",
    needBoardBody: "Davetler pano üzerinden gönderilir ve yalnızca yöneticisi olduğun panolara davet edebilirsin. Bir pano oluştur, sonra ekibini oraya çağır.",
    createBoard: "Yeni pano oluştur", title: "Ekibini davet et",
    toBoard: "<b>{board}</b> panosuna e-postayla davet gönder.",
    toPicked: "Seçtiğin panoya e-postayla davet gönder.",
    howItWorks: "Kişi kabul edince üye listesine canlı olarak düşer; hesabı yoksa kayıt bağlantısı gider.",
    board: "Pano", role: "Rol",
    roleEditor: "Editör: kart ekler, taşır, düzenler", roleViewer: "İzleyici: yalnızca görür",
    roleOwner: "Yönetici: üyeleri de yönetir",
    send: "Davet gönder", sent: "Davet gönderildi", noAccount: " · hesabı yok, e-posta gönderildi",
    failed: "Davet gönderilemedi",
  },

  /* ── Tanıtım sayfası (landing.js) ── */
  landing: {
    skip: "İçeriğe geç", sections: "Sayfa bölümleri", menu: "Menü", openMenu: "Menüyü aç", closeMenu: "Menüyü kapat",
    nav: { features: "Özellikler", how: "Nasıl çalışır", roles: "Roller", about: "Hakkında", faq: "SSS" },
    hero: {
      title1: "Ekibinle aynı panoda,", title2: "aynı anda.",
      lead: "Bir kartı taşıdığında ekip arkadaşlarının ekranında da yer değiştirir. Kimin panoda olduğunu ve neyi değiştirdiğini sayfayı yenilemeden görürsün.",
      how: "Nasıl çalışır?",
    },
    demo: {
      label: "Canlandırma: {ayse} ve {mert} aynı panoda kart taşıyor ve yeni kart ekliyor; her hareket, kimin yaptığıyla birlikte anında herkesin ekranına düşüyor.",
      board: "Ürün Yol Haritası", role: "YÖNETİCİ", live: "Canlı", moving: "{name} taşıyor",
      cols: { todo: "Yapılacak", doing: "Devam ediyor", done: "Bitti", review: "İncelemede" },
      cards: {
        onb: "Onboarding e-postası", ode: "Ödeme sayfası entegrasyonu", dark: "Karanlık tema",
        api: "API hız sınırı dokümanı", push: "Push bildirim izni", giris: "Giriş ekranı tasarımı", fatura: "Fatura PDF'i",
      },
      tags: { date: "29 Eki", soon: "Yarın", late: "2 gün gecikti" },
      feed: {
        moveOde: `{who} "Ödeme sayfası"nı Devam ediyor'a taşıdı`,
        movePush: `{who} "Push bildirim izni"ni Bitti'ye taşıdı`,
        addFatura: `{who} "Fatura PDF'i" kartını ekledi`,
        moveOnb: `{who} "Onboarding e-postası"nı Devam ediyor'a taşıdı`,
        addDark: `{who} "Karanlık tema" kartını ekledi`,
        joined: "{who} panoya katıldı",
        moveGiris: `{who} "Giriş ekranı tasarımı"nı Bitti'ye taşıdı`,
      },
      toast: {
        moved: "{who} kartı taşıdı", added: "{who} kart ekledi",
        moveOde: "Ödeme sayfası → Devam ediyor", movePush: "Push bildirim izni → Bitti",
        addFatura: "Fatura PDF'i · Yapılacak", moveOnb: "Onboarding e-postası → Devam ediyor",
      },
    },
    ticker: {
      label: "Örnek pano akışı", title: "Örnek panoda son hareketler",
      sr: "Örnek bir panoda ekip arkadaşlarının hareketleri burada canlı olarak akar.",
    },
    featuresTitle: "Neler yapabilirsin?",
    features: {
      live: { title: "Anında canlı senkron", text: "Eklenen, taşınan ya da düzenlenen kart herkesin ekranında aynı anda değişir." },
      cursors: { title: "Canlı imleçler", text: "Ekip arkadaşlarının faresini isimleriyle görürsün. Biri bir kartı tutunca kart herkeste soluklaşır." },
      conflict: { title: "Çakışma koruması", text: "İki kişi aynı kartı aynı anda değiştirirse ilk kayıt geçerli olur, diğerine haber verilir. Yazdığın kaybolmaz." },
      history: { title: "Zaman yolculuğu", text: "Panonun geçmişteki herhangi bir anını kaydırıcıyla aç. Silinen kolonlar bile o anki hâliyle görünür." },
      flow: { title: "Akış sağlığı", text: "Kartların hangi kolonda ne kadar beklediğini ve işin nerede tıkandığını gösterir." },
      wip: { title: "WIP limiti", text: "Bir kolondaki kart sayısını sınırla. Kolon dolunca yeni kart almaz." },
    },
    past: {
      title: "Dün bu pano nasıldı?",
      body: "Her değişiklik bir olay olarak kaydedilir: kim, ne zaman, ne yaptı. Kaydırıcıyı geri çektiğinde pano o anki hâliyle görünür, silinen kolonlar dahil.",
      point1: "Herhangi bir ana tek hareketle dön",
      point2: "Geçmişe bakarken pano salt okunur; yanlışlıkla değiştiremezsin",
      point3: `"Şimdi"ye tek tıkla geri gel`,
      label: "Geçmiş görünümü: kaydırıcı geriye çekildikçe pano o anki hâliyle görünür; sonradan silinen İncelemede kolonu bile.",
      backToNow: "Şimdiye dön", deleted: "· silindi",
      cards: { onb: "Onboarding e-postası", api: "API hız sınırı", push: "Push bildirim izni", giris: "Giriş ekranı" },
      times: {
        pastWhen: "20 Ekim, 10:05", pastWhat: `17. hareket · {ayse} "Giriş ekranı"nı İncelemede'ye taşıdı`,
        midWhen: "21 Ekim, 14:32", midWhat: `38. hareket · {mert} "API dokümanı"nı taşıdı`,
        nowWhen: "Şimdi", nowWhat: `52. hareket · {deniz} "İncelemede" kolonunu sildi`,
      },
    },
    flow: {
      title: "İş nerede tıkanıyor?",
      body: "Her kolonda kartların ortalama ne kadar beklediğini ve en uzun bekleyen kartları gösterir. Unutulmuş bir iş listenin başına çıkar.",
      label: "Akış sağlığı paneli: kolon başına ortalama bekleme süresi; en uzun bekleyen İncelemede kolonu darboğaz olarak işaretli, altta en uzun bekleyen kartlar listeleniyor.",
      head: "Akış sağlığı", bottleneck: "Darboğaz: {column}", aging: "Yaşlanan iş",
    },
    team: {
      title: "Kimse habersiz eklenmez.",
      body: "Panoya birini e-postasıyla davet edersin; davet o kişi hangi ekrandaysa anında önüne düşer. Kabul edince üye olur, sen de o an haber alırsın. Hesabı yoksa kayıt bağlantısı gider, davet onu bekler.",
      label: "Davet bildirimi: {mert}, {ayse}'yi Ürün Yol Haritası panosuna Editör olarak davet ediyor; {ayse} kabul edince {mert}'e anında haber gidiyor.",
      inviteTitle: "Pano daveti",
      inviteBody: "<strong>{mert}</strong> seni <strong>Ürün Yol Haritası</strong> panosuna <strong>Editör</strong> olarak davet etti.",
      accepted: "Kabul edildi · panoya katıldın",
      acceptedBy: "<strong>{ayse}</strong> davetini kabul etti · şimdi",
    },
    steps: {
      title: "Üç adımda başla.",
      s1: { title: "Hesabını aç", text: "E-postanla kayıt ol, gelen bağlantıyla hesabını onayla." },
      s2: { title: "Pano kur, ekibini davet et", text: "Kolonlarını belirle, ekip arkadaşlarını Editör ya da İzleyici olarak çağır." },
      s3: { title: "Birlikte çalışın", text: "Kartları taşıyın, yazın, atayın. Herkes her şeyi aynı anda görür." },
    },
    roles: {
      title: "Kim ne yapabilir?",
      body: "Yönetici panoyu ve üyeleri yönetir, Editör kartlarla çalışır, İzleyici yalnızca bakar. Üçü de her değişikliği canlı görür.",
      caption: "Rollere göre yetkiler", yes: "Evet", no: "Hayır",
      rows: {
        live: "Panoyu ve değişiklikleri canlı görür", edit: "Kart ve kolon değiştirir",
        manage: "Üye davet eder, rolleri yönetir", history: "Geçmişi ve akış ölçümünü görür",
      },
    },
    about: {
      title: "Neden {brand}?",
      body: "{brand}, bir ekibin aynı panoda aynı anda çalışabilmesi için yapıldı. Her hareket herkese anında ulaşır, kimsenin değişikliği fark edilmeden ezilmez ve panonun geçmişi her zaman açılabilir.",
      p1: { title: "Önce canlılık", text: "Bir değişiklik olduysa, panoya bakan herkes onu aynı anda görür." },
      p2: { title: "Veri kaybı yok", text: "Aynı anda yapılan iki değişiklikten biri sessizce ezilmez." },
      p3: { title: "Şeffaf geçmiş", text: "Kimin neyi ne zaman yaptığı kayıtlı, istediğin ana dönebilirsin." },
      p4: { title: "Kurulum yok", text: "Tarayıcıda açılır, indirip kurman gereken bir şey yok." },
    },
    faq: {
      title: "Aklındaki sorular",
      q1: { q: "Aynı kartı iki kişi aynı anda değiştirirse ne olur?", a: "İlk kaydedilen geçerli olur. Sonraki değişiklik reddedilir, o kişinin ekranı en güncel hâliyle tazelenir ve bir uyarıyla ne olduğunu görür." },
      q2: { q: "Davet ettiğim kişinin hesabı yoksa?", a: "Davet e-posta adresine bağlıdır. Kişiye kayıt bağlantılı bir posta gider; kaydolup giriş yaptığında davet onu bekler." },
      q3: { q: "İzleyici neler yapabilir?", a: "Panoyu ve bütün değişiklikleri canlı görür, geçmişe ve akış ölçümüne bakabilir; kart ya da kolon değiştiremez." },
      q4: { q: "Panonun geçmişini kimler görebilir?", a: "Yalnızca o panoyu görme yetkisi olanlar. Geçmişe bakarken pano salt okunurdur." },
      q5: { q: "Şifrem nasıl saklanıyor?", a: "Geri çevrilemeyecek biçimde saklanır, kimse okuyamaz. Şifreni değiştirdiğinde diğer cihazlardaki oturumların kapanır." },
    },
    cta: { title: "Panonu aç, ekibini çağır.", body: "Bir dakikada hesap aç, ilk panonda ekibinle aynı anda çalış." },
    foot: {
      tagline: "Gerçek zamanlı, birlikte çalışılan Kanban panosu.",
      product: "Ürün", productH: "ÜRÜN", project: "Proje", projectH: "PROJE", account: "Hesap", accountH: "HESAP",
    },
  },
};

/* ══════════════════ English ══════════════════ */
const en = {
  meta: { code: "EN", name: "English" },

  common: {
    cancel: "Cancel", save: "Save", close: "Close", delete: "Delete", create: "Create", add: "Add",
    remove: "Remove", retry: "Try again", loading: "Loading…", accept: "Accept", decline: "Decline",
    signIn: "Sign in", signUp: "Sign up", signOut: "Sign out", someone: "Someone",
    email: "Email", emailPlaceholder: "name@example.com", firstName: "First name", lastName: "Last name",
    password: "Password", newPassword: "New password", newPasswordAgain: "New password (again)",
    minPassword: "At least 8 characters",
    rename: "Rename", home: "Home", profile: "My profile", notifications: "Notifications", search: "Search",
    deletedBoard: "a deleted board", deletedCard: "a deleted card", deletedUser: "Deleted user",
    noValue: "–",
  },

  lang: { label: "Language", saveFailed: "Couldn't save your language" },

  time: {
    now: "just now", today: "Today", tomorrow: "Tomorrow", yesterday: "Yesterday",
    daysLate: { one: "{count} day late", other: "{count} days late" },
  },

  role: {
    badge: { OWNER: "ADMIN", ADMIN: "ADMIN", EDITOR: "EDITOR", MEMBER: "MEMBER", VIEWER: "VIEWER", GUEST: "GUEST" },
    name: { OWNER: "Admin", EDITOR: "Editor", VIEWER: "Viewer" },
    as: { OWNER: "an admin", EDITOR: "an editor", VIEWER: "a viewer" },
  },

  api: {
    requestFailed: "Request failed ({status})",
    uploadFailed: "Upload failed ({status})",
    refreshFailed: "Couldn't renew your session ({status})",
  },

  theme: {
    toggle: "Change theme", toDark: "Switch to dark theme", toLight: "Switch to light theme",
    dark: "Dark theme", light: "Light theme",
  },

  session: {
    closed: "Signed out", closedElsewhere: "You signed out in another tab.",
    expired: "Your session has ended", signInAgain: "Please sign in again.",
  },

  auth: {
    home: "Home",
    registerTitle: "Create an account", loginTitle: "Welcome back",
    registerSub: "It only takes a few seconds.", loginSub: "Sign in to get back to your boards.",
    google: "Continue with Google", orEmail: "or with email",
    firstNamePh: "First name", lastNamePh: "Last name", passwordPh: "Your password",
    forgot: "Forgot password?", resendPrompt: "Link didn't work?", resendLink: "Send a new one",
    haveAccount: "Already have an account?", noAccount: "Don't have an account?",
    forgotTitle: "Forgot your password?",
    forgotSub: "Enter your account's email address and we'll send you a link to set a new password.",
    sendLink: "Send link", remembered: "Remembered it?",
    checkInbox: "Check your inbox",
    resetSentSub: "If this address is registered, we sent a password reset link to:",
    resetStep1: "Click <b>Reset my password</b> in the email",
    resetStep2: "The link works for <b>30 minutes</b> and only once",
    backToLogin: "Back to sign in",
    resetTitle: "Set a new password", resetSub: "Once your password changes, you'll be signed out on all devices.",
    updatePassword: "Update my password",
    verifySentSub: "We sent a verification link to:",
    verifyStep1: "Click <b>Verify my account</b> in the email",
    verifyStep2: "Once you're verified, sign in here",
    resendMail: "Send the email again", verifiedQ: "Verified?",
    err: {
      emailRequired: "Enter your email address.", emailFirst: "Enter your email address first.",
      sendFailed: "Couldn't send it. Try again in a moment.",
      passwordShortDot: "Your password must be at least 8 characters.",
      passwordShort: "Your password must be at least 8 characters",
      passwordMismatch: "The two passwords don't match.", resetFailed: "Couldn't update your password.",
      verifyFailed: "Couldn't process the verification link.",
      nameRequired: "First and last name are required", credentialsRequired: "Email and password are required",
      loginFailed: "Sign-in failed",
    },
    oauth: {
      cancelled: "Google sign-in was cancelled.",
      email_unverified: "Your Google account's email address isn't verified. You can sign up with your email instead.",
      failed: "Couldn't sign in with Google. Try again in a moment.",
    },
  },

  topbar: {
    account: "My account", pickWorkspace: "Choose a workspace",
    search: "Search boards, cards and commands", searchPh: "Search boards, cards and commands…",
    new: "New", sharedWithMe: "Shared with me", allSpaces: "All workspaces", allBoards: "All boards",
    newWorkspace: "New workspace", newBoard: "New board", inviteTeam: "Invite your team",
    onlineTitle: "{names} on a board right now", online: "{count} online",
  },

  notif: {
    BOARD_INVITE: "{who} invited you to {board} as {role}",
    INVITE_ACCEPTED: "{who} accepted your invitation · {board}",
    INVITE_DECLINED: "{who} declined your invitation · {board}",
    INVITE_CANCELLED: "{who} withdrew the invitation · {board}",
    COMMENT_MENTION: "{who} mentioned you in a comment: {card}",
    CARD_COMMENT: "{who} commented on a card assigned to you: {card}",
    boardMissing: "Board not found", maybeDeleted: "It may have been deleted.", cardDeleted: "This card was deleted",
    markAllRead: "Mark all as read", loadFailed: "Couldn't load notifications", checkConnection: "Check your connection.",
    empty: "No notifications",
    emptyHint: "When someone invites you to a board or mentions you in a comment, it shows up here.",
    accepted: "You accepted", goToBoard: "Go to board →", declined: "You declined", withdrawn: "Invitation withdrawn",
    inviteTitle: "Board invitation", decideLater: "Decide later",
    joined: "You joined the board", openBoard: "Open board →", inviteDeclined: "Invitation declined",
    answerFailed: "Couldn't respond", inviteAccepted: "Invitation accepted", openCard: "Open card",
    mentionedYou: "{who} mentioned you in a comment", commentOnAssigned: "New comment on a card assigned to you",
  },

  profile: {
    title: "My profile", changePhoto: "Change photo", uploadPhoto: "Upload photo",
    memberSince: "Member since {since}",
    workspaces: { one: "{count} workspace", other: "{count} workspaces" },
    boards: { one: "{count} board", other: "{count} boards" },
    googleLinked: "Google account linked", photoRemoved: "Photo removed", removeFailed: "Couldn't remove it",
    personal: "Personal details",
    emailLocked: "Your email address can't be changed. It's how your account is recognized at sign-in and in invitations.",
    setPassword: "Set a password", security: "Security", currentPassword: "Current password",
    noPasswordNote: "Your account was created with Google. If you set a password, you can also sign in with your email.",
    passwordNote: "When your password changes, you're signed out on your other devices but stay signed in on this one.",
    changePassword: "Change password", updated: "Profile updated", saveFailed: "Couldn't save",
    language: "Language", languageHint: "The app and the emails we send you use this language.",
    passwordSet: "Password set", passwordSetHint: "You can now sign in with your email too.",
    passwordChanged: "Password changed", passwordChangedHint: "You've been signed out on your other devices.",
    err: {
      currentRequired: "Enter your current password.", newShort: "The new password must be at least 8 characters.",
      mismatch: "The two new passwords don't match.", same: "The new password can't be the same as your current one.",
      changeFailed: "Couldn't change your password.",
    },
  },

  photo: {
    unsupported: "Unsupported file", pickType: "Choose a PNG, JPEG or WebP image.",
    tooLarge: "File too large", max: "5 MB at most.",
    openFailed: "Couldn't open the image", corrupt: "The file may be damaged.",
    title: "Profile photo", hint: "Drag to position it and use the slider to zoom.",
    preview: "This is how it will look on your profile and in member lists",
    updated: "Photo updated", uploadFailed: "Couldn't upload",
  },

  home: {
    loadFailed: "Couldn't load", sideNav: "Side menu", workspaces: "Workspaces",
    newWorkspace: "+ New workspace", overview: "Overview", boards: "Boards", team: "Team",
    recent: "Recently opened", hereNow: "People on this board right now",
    guestTitle: "You don't have access to this workspace",
    guestHint: "You're a guest here, so you only see boards you were invited to directly.",
    noBoards: "No boards yet", noBoardsHint: "Create your first board, or wait to be invited to one.",
    firstBoard: "Create your first board",
    greet: { morning: "Good morning", day: "Good afternoon", evening: "Good evening" },
    lateCards: { one: "{count} of your cards is overdue", other: "{count} of your cards are overdue" },
    dueToday: { one: "{count} card is due today", other: "{count} cards are due today" },
    nothingUrgent: "nothing urgent",
    overviewFailed: "Couldn't load the overview.",
    openOnBoard: "Open in {board}", noDate: "No date",
    assigned: "Assigned to me", openCount: "{count} open",
    filter: { all: "All", week: "This week", late: "Overdue" },
    moreCards: "+{count} more · showing the ones due soonest",
    noMatch: "No cards match this filter", noLate: "You have no overdue cards.",
    noWeek: "Nothing of yours is due in the next 7 days.",
    noAssigned: "No open cards assigned to you",
    noAssignedHint: "When a card is assigned to you, it shows up here, sorted by due date.",
    liveNow: "Live now",
    liveEmpty: "Nobody else is on your boards right now. When someone opens one, you'll see it here live.",
    aging: "Aging work", agingEstimated: "We don't know when it entered this column, so it has waited at least this long",
    agingWaiting: "Waiting in this column",
    agingEmpty: "You have no open cards, or none has been sitting in a column for long.",
    openBoard: "Open {board}", recentActivity: "Recent activity",
    activityEmpty: "When cards are added or moved on your boards, you'll see it here.",
    moreInvites: { one: "+{count} more invitation", other: "+{count} more invitations" },
    newBoardTile: "+ New board", yourBoards: "Your boards",
    scope: { all: "All", mine: "Mine", shared: "Shared" },
    sharedWithYouIn: "{ws} · shared with you", sharedWithYou: "Shared with you",
    cards: { one: "{count} card", other: "{count} cards" },
    members: { one: "{count} member", other: "{count} members" },
    peopleHere: "● {count} here",
    yourTeam: "Your team", invite: "Invite →", online: "Online", offline: "Offline",
    onBoard: "on {board}", onlineLower: "online", offlineLower: "offline",
    teamEmpty: "You're not sharing a board with anyone yet. People you invite will show up here.",
    renamed: "Name updated", updateFailed: "Couldn't update",
    deleteWsTitle: "Delete “{name}”?",
    deleteWsBody: {
      one: "Its {count} board, the cards and the history will be deleted permanently. This can't be undone.",
      other: "Its {count} boards, the cards and the history will be deleted permanently. This can't be undone.",
    },
    wsDeleted: "Workspace deleted", wsDeletedBody: "{name} and its boards were removed",
    deleteFailed: "Couldn't delete",
  },

  create: {
    wsTitle: "New workspace", boardTitle: "New board",
    wsSub: "Create a shared space for your team.",
    boardSub: "Start from a template. You can change the columns later.",
    wsPh: "e.g. Acme Software", boardPh: "e.g. Product Roadmap", name: "Name", workspace: "Workspace",
    failed: "Couldn't create it",
    template: "Template", blankHint: "Add your own columns", wip: "WIP limit: {limit}",
    templatesFailed: "Couldn't load the templates. The board will open with Simple Kanban.",
  },

  board: {
    noAccess: "No access", noAccessBody: "You don't have access to this board.", loadFailed: "Couldn't load the board",
    cardMissing: "Card not found", cardMissingBody: "It may have been deleted or moved.",
    disconnected: "Connection lost", reconnecting: "Trying to reconnect…",
    reconnected: "Reconnected", refreshed: "The board is up to date.",
    columnFull: "Column is full", columnFullBody: "This column has reached its WIP limit.",
    invalid: "Not valid", invalidBody: "That input wasn't accepted.",
    conflict: "Conflict", conflictBody: "Your change was rejected.",
    back: "Back to home", location: "Location", wsBoards: "Boards in {ws}", switch: "Switch board",
    view: "View", tab: { board: "Board", history: "History", flow: "Flow" },
    hereNow: "People on this board now", inviteTitle: "Invite to this board", invite: "Invite",
    panel: "Board panel", members: "Members", addInvite: "+ Invite", recentActivity: "Recent activity",
    listLoading: "Loading boards…", allBoards: "All boards",
    viewerTitle: "You're in view-only mode",
    viewerBody: "You can see the board but not change it. Changes still reach you live.",
    filterCount: "{shown} / {total} cards · dragging is off",
  },

  filter: {
    everyone: "Everyone", searchPh: "Search this board…", search: "Search this board", mine: "Assigned to me",
    late: "Overdue", lateCount: "Overdue · {count}", lateTitle: "Cards past their due date that aren't done yet",
    person: "Person:", personTitle: "Filter by assignee", clear: "Clear",
    togglePanel: "Hide / show the side panel",
  },

  stats: {
    cards: { one: "<b>{count}</b> card", other: "<b>{count}</b> cards" },
    bottleneck: "Bottleneck:", bottleneckTitle: "The column where cards wait longest",
    cycle: "Avg. cycle:", cycleTitle: "Average time for a card to get from the first column to the last",
  },

  conn: {
    liveCount: "● Live · {count} here", live: "● Live", lost: "Disconnected", connecting: "Connecting…",
    pillLive: "LIVE", pillOff: "OFFLINE", pillConnecting: "CONNECTING",
  },

  column: {
    actions: "Column actions",
    wipFull: "This column has reached its WIP limit. Finish something here before taking on a new card.",
    wipOver: "Over the limit. It won't take new cards until there's room.",
    noMatch: "No cards match the filter", empty: "No cards yet", addCard: "Add card", addColumn: "Add column",
    wipLimit: "WIP limit…", delete: "Delete column",
    deleteTitle: "Delete the {name} column?",
    deleteCards: {
      one: "Its <b>{count} card</b> will be deleted permanently too.",
      other: "Its <b>{count} cards</b> will be deleted permanently too.",
    },
    isEmpty: "The column is empty.", deleteNote: "This can't be undone, but it stays in the board's history.",
    namePh: "Column name…",
  },

  wip: {
    label: "WIP limit: {limit}", clickToChange: " (click to change)",
    noneEditable: "No WIP limit. Click to set one", none: "No WIP limit",
    title: "WIP limit for {name}",
    sub: "How many cards can this column hold at once? When it's full, it won't take new cards, so you finish the work in progress instead of starting something new.",
    limit: "Limit", placeholder: "Empty = no limit", remove: "Remove limit",
    invalid: "Invalid limit", invalidBody: "It must be at least 1, or remove the limit.",
  },

  card: {
    hasDescription: "Has a description", comments: { one: "{count} comment", other: "{count} comments" },
    due: "Due: {date}", composerPh: "Card title…", composerHint: "Press Enter to add",
  },

  detail: {
    unassigned: "Unassigned", description: "Description",
    descriptionPh: "What exactly is this? When does it count as done?",
    assignee: "Assignee", due: "Due date", column: "Column", position: "Position", version: "Version",
    deleteCard: "Delete card", titleRequired: "The title can't be empty",
  },

  cardKey: {
    title: "The card's key. It stays the same when the card moves.",
    copy: "Copy", copyLabel: "Copy the key, branch name or link",
    copyKey: "Copy key", copyBranch: "Copy branch name", copyLink: "Copy link",
    keyCopied: "Key copied", branchCopied: "Branch name copied", linkCopied: "Link copied",
    copyFailed: "Couldn't copy", copyFailedBody: "The browser didn't allow copying.",
    notFoundBody: "There's no card {key}, or you don't have access to its board.",
  },

  comments: {
    title: "Comments", placeholder: "Write a comment…", write: "Write a comment", send: "Send",
    hint: "Type @ to mention someone · {mod}+Enter to send",
    loading: "Loading comments…", cardDeleted: "This card was deleted.", loadFailed: "Couldn't load comments.",
    empty: "No comments yet.", edited: "(edited)", edit: "Edit",
    confirmDelete: "Delete this comment?", deleteFailed: "Couldn't delete the comment",
    editLabel: "Edit comment", saveHint: "{mod}+Enter to save",
    required: "A comment can't be empty.", saveFailed: "Couldn't save the comment.", sendFailed: "Couldn't send the comment.",
    mentionList: "Person to mention",
  },

  members: {
    here: "On the board now", notHere: "Not on the board", you: " (you)", remove: "Remove",
    removed: "Member removed", removeFailed: "Couldn't remove",
    pending: "Pending invitations · {count}", noAccount: "No account yet · will see it after signing up",
    pendingBadge: "PENDING", withdraw: "Withdraw invitation", withdrawn: "Invitation withdrawn",
    withdrawFailed: "Couldn't withdraw",
    removedYou: "You were removed from the board", roleChanged: "Your role changed",
    roleChangedBody: "You're now {role} on {board}",
  },

  history: {
    loadFailed: "Couldn't load the history", empty: "No history yet",
    emptyBody: "There's nothing on this board to rewind to yet.",
    pill: "HISTORY", backToNow: "Back to now", jumpFailed: "Couldn't go to that moment",
  },

  flow: {
    title: "Flow", sub: "How long cards wait in each column and where work gets stuck.",
    cycle: "Average cycle time", cycleHint: "from the first column to the last",
    bottleneck: "Bottleneck", none: "None", bottleneckHint: "cards wait {duration} here on average",
    noBottleneck: "nothing is clearly stuck", open: "Open cards", openHint: "not in the last column",
    dwell: "Average wait per column", cards: { one: "{count} card", other: "{count} cards" },
    cardsWip: "{count} / {wip} cards",
    redNote: "The red column is where cards wait longest.",
    aging: "Aging work", agingEmpty: "No open cards waiting.",
  },

  activity: {
    empty: "No activity yet", emptyHint: "Add or move a card and it shows up here.",
    count: { one: "{count} change", other: "{count} changes" }, hide: "Hide", showAll: "Show all",
    ADD_CARD: "added '{card}' to {column}",
    MOVE_CARD: "moved '{card}' to {column}",
    EDIT_CARD: "updated the details of '{card}'",
    EDIT_CARD_TITLE: "renamed the card '{from}' to '{card}'",
    DELETE_CARD: "deleted '{card}'",
    ADD_COLUMN: "added the {column} column",
    RENAME_COLUMN: "renamed the {from} column to {column}",
    DELETE_COLUMN: "deleted the {column} column",
    MOVE_COLUMN: "moved the {column} column to position {position}",
    SET_WIP_LIMIT: "set the WIP limit of {column} to {limit}",
    SET_WIP_LIMIT_OFF: "removed the WIP limit of {column}",
    ADD_COMMENT: "commented on '{card}'",
    MEMBER_JOINED: "joined the board as {role}",
  },

  palette: {
    label: "Search and run commands", placeholder: "Search boards, cards or commands…",
    move: "move", open: "open", close: "close",
    goHome: "Go to home", newBoard: "Create a new board", newWorkspace: "New workspace",
    invite: "Invite your team", newCard: "Add a new card", history: "Browse history", flow: "Open flow metrics",
    recent: "Recently opened", boards: "Boards", cards: "Cards", commands: "Commands",
    searching: "Searching…", noResults: "No results for “{q}”", searchingCards: "Searching cards…",
  },

  invite: {
    needBoard: "You need a board first",
    needBoardBody: "Invitations go through a board, and you can only invite people to boards you admin. Create a board, then invite your team to it.",
    createBoard: "Create a new board", title: "Invite your team",
    toBoard: "Send an email invitation to <b>{board}</b>.",
    toPicked: "Send an email invitation to the board you choose.",
    howItWorks: "Once they accept, they appear in the member list live. If they don't have an account, they get a sign-up link.",
    board: "Board", role: "Role",
    roleEditor: "Editor: adds, moves and edits cards", roleViewer: "Viewer: can only look",
    roleOwner: "Admin: also manages members",
    send: "Send invitation", sent: "Invitation sent", noAccount: " · no account yet, so we emailed them",
    failed: "Couldn't send the invitation",
  },

  landing: {
    skip: "Skip to content", sections: "Page sections", menu: "Menu", openMenu: "Open menu", closeMenu: "Close menu",
    nav: { features: "Features", how: "How it works", roles: "Roles", about: "About", faq: "FAQ" },
    hero: {
      title1: "Your team on one board,", title2: "at the same time.",
      lead: "When you move a card, it moves on your teammates' screens too. You see who's on the board and what they change, without refreshing the page.",
      how: "How does it work?",
    },
    demo: {
      label: "Animation: {ayse} and {mert} move cards and add a new one on the same board. Every move shows up on everyone's screen instantly, along with who made it.",
      board: "Product Roadmap", role: "ADMIN", live: "Live", moving: "{name} is moving",
      cols: { todo: "To do", doing: "In progress", done: "Done", review: "In review" },
      cards: {
        onb: "Onboarding email", ode: "Payment page integration", dark: "Dark theme",
        api: "API rate limit docs", push: "Push notification permission", giris: "Login screen design", fatura: "Invoice PDF",
      },
      tags: { date: "Oct 29", soon: "Tomorrow", late: "2 days late" },
      feed: {
        moveOde: `{who} moved "Payment page" to In progress`,
        movePush: `{who} moved "Push notification permission" to Done`,
        addFatura: `{who} added the "Invoice PDF" card`,
        moveOnb: `{who} moved "Onboarding email" to In progress`,
        addDark: `{who} added the "Dark theme" card`,
        joined: "{who} joined the board",
        moveGiris: `{who} moved "Login screen design" to Done`,
      },
      toast: {
        moved: "{who} moved a card", added: "{who} added a card",
        moveOde: "Payment page → In progress", movePush: "Push notification permission → Done",
        addFatura: "Invoice PDF · To do", moveOnb: "Onboarding email → In progress",
      },
    },
    ticker: {
      label: "Sample board activity", title: "Latest moves on a sample board",
      sr: "Teammates' moves on a sample board stream here live.",
    },
    featuresTitle: "What can you do?",
    features: {
      live: { title: "Instant live sync", text: "Add, move or edit a card and it changes on everyone's screen at the same time." },
      cursors: { title: "Live cursors", text: "See your teammates' cursors with their names. When someone picks up a card, it fades for everyone else." },
      conflict: { title: "Conflict protection", text: "If two people change the same card at once, the first save wins and the other person is told. Nothing you write disappears without notice." },
      history: { title: "Time travel", text: "Open any past moment of the board with a slider. Even deleted columns show up as they were." },
      flow: { title: "Flow health", text: "Shows how long cards wait in each column and where work gets stuck." },
      wip: { title: "WIP limits", text: "Cap the number of cards in a column. Once it's full, it takes no new cards." },
    },
    past: {
      title: "What did this board look like yesterday?",
      body: "Every change is recorded as an event: who did what, and when. Drag the slider back and the board appears as it was then, deleted columns included.",
      point1: "Jump to any moment in one move",
      point2: "The board is read-only while you look back, so you can't change it by accident",
      point3: `Get back to "Now" in one click`,
      label: "History view: as the slider moves back, the board appears as it was at that moment, even the In review column that was deleted later.",
      backToNow: "Back to now", deleted: "· deleted",
      cards: { onb: "Onboarding email", api: "API rate limit", push: "Push notification permission", giris: "Login screen" },
      times: {
        pastWhen: "Oct 20, 10:05", pastWhat: `Change 17 · {ayse} moved "Login screen" to In review`,
        midWhen: "Oct 21, 14:32", midWhat: `Change 38 · {mert} moved "API docs"`,
        nowWhen: "Now", nowWhat: `Change 52 · {deniz} deleted the "In review" column`,
      },
    },
    flow: {
      title: "Where does work get stuck?",
      body: "See how long cards wait in each column on average and which cards have waited longest. Forgotten work rises to the top.",
      label: "Flow health panel: average wait per column. In review, where cards wait longest, is marked as the bottleneck, and the cards that have waited longest are listed below.",
      head: "Flow health", bottleneck: "Bottleneck: {column}", aging: "Aging work",
    },
    team: {
      title: "Nobody gets added without knowing.",
      body: "You invite someone to a board by email, and the invitation pops up right away on whatever screen they're on. Once they accept, they're a member and you hear about it immediately. If they don't have an account, they get a sign-up link and the invitation waits for them.",
      label: "Invitation: {mert} invites {ayse} to the Product Roadmap board as an editor. When {ayse} accepts, {mert} hears about it right away.",
      inviteTitle: "Board invitation",
      inviteBody: "<strong>{mert}</strong> invited you to <strong>Product Roadmap</strong> as <strong>an editor</strong>.",
      accepted: "Accepted · you joined the board",
      acceptedBy: "<strong>{ayse}</strong> accepted your invitation · just now",
    },
    steps: {
      title: "Get started in three steps.",
      s1: { title: "Create your account", text: "Sign up with your email and confirm your account with the link we send you." },
      s2: { title: "Set up a board, invite your team", text: "Choose your columns and invite teammates as editors or viewers." },
      s3: { title: "Work together", text: "Move, write and assign cards. Everyone sees everything at the same time." },
    },
    roles: {
      title: "Who can do what?",
      body: "Admins manage the board and its members, editors work on cards, and viewers just look. All three see every change live.",
      caption: "Permissions by role", yes: "Yes", no: "No",
      rows: {
        live: "Sees the board and its changes live", edit: "Changes cards and columns",
        manage: "Invites members and manages roles", history: "Sees the history and flow metrics",
      },
    },
    about: {
      title: "Why {brand}?",
      body: "{brand} was built so a team can work on the same board at the same time. Every move reaches everyone instantly, nobody's change gets overwritten unnoticed, and the board's history is always there to open.",
      p1: { title: "Live first", text: "If something changed, everyone looking at the board sees it at the same moment." },
      p2: { title: "No lost work", text: "When two changes happen at once, neither one is silently overwritten." },
      p3: { title: "Open history", text: "Who did what and when is on record, and you can go back to any moment." },
      p4: { title: "Nothing to install", text: "It runs in your browser. There's nothing to download or install." },
    },
    faq: {
      title: "Questions you might have",
      q1: { q: "What happens if two people change the same card at once?", a: "The first save wins. The later change is rejected, that person's screen refreshes to the latest version, and a notice tells them what happened." },
      q2: { q: "What if the person I invite doesn't have an account?", a: "Invitations are tied to an email address. They get an email with a sign-up link, and the invitation is waiting for them when they sign in." },
      q3: { q: "What can a viewer do?", a: "Viewers see the board and every change live and can look at the history and flow metrics, but they can't change cards or columns." },
      q4: { q: "Who can see a board's history?", a: "Only people who can see that board. While you look at the past, the board is read-only." },
      q5: { q: "How is my password stored?", a: "In a form that can't be reversed, so nobody can read it. When you change your password, you're signed out on your other devices." },
    },
    cta: { title: "Open a board, bring your team.", body: "Create an account in a minute and work on your first board with your team, live." },
    foot: {
      tagline: "A real-time Kanban board for working together.",
      product: "Product", productH: "PRODUCT", project: "Project", projectH: "PROJECT", account: "Account", accountH: "ACCOUNT",
    },
  },
};

/* ══════════════════ Dil seçimi ══════════════════ */

export const DICTIONARIES = { tr, en };
export const LOCALES = Object.keys(DICTIONARIES);
export const DEFAULT_LOCALE = "tr";
/** Tarayıcının istediği dillerin hiçbiri yoksa. */
const FALLBACK_LOCALE = "en";
const STORE_KEY = "cb_lang";
/** Giriş yapmadan seçilen dil: girişten sonra hesaba yazılır (Google dönüşü sayfayı yeniden yüklediği için sekmede saklanır). */
const PICK_KEY = "cb_lang_pick";

const store = {
  get: (s, k) => { try { return s.getItem(k); } catch (_) { return null; } },
  set: (s, k, v) => { try { s.setItem(k, v); } catch (_) { /* gizli pencere: yalnızca bu oturum */ } },
  del: (s, k) => { try { s.removeItem(k); } catch (_) {} },
};
const hasStorage = typeof localStorage !== "undefined";

function detect() {
  const saved = hasStorage ? store.get(localStorage, STORE_KEY) : null;
  if (LOCALES.includes(saved)) return saved;
  const nav = typeof navigator !== "undefined" ? (navigator.languages?.length ? navigator.languages : [navigator.language]) : [];
  for (const tag of nav) {
    const base = String(tag || "").toLowerCase().split("-")[0];
    if (LOCALES.includes(base)) return base;
  }
  return FALLBACK_LOCALE;
}

let current = detect();
const listeners = new Set();

function apply() {
  if (typeof document === "undefined") return;
  document.documentElement.lang = current;
  document.title = BRAND;
}
apply();

export const locale = () => current;

/**
 * Dili değiştirir. pick: giriş yapılmamışken kişinin kendisi seçti; girişten
 * sonra hesabın tercihi olarak kaydedilir (index.html, takePickedLocale).
 * Değiştiyse true; dinleyiciler (API başlığı, ekranı yeniden çizen kod) çağrılır.
 */
export function setLocale(next, { pick = false } = {}) {
  if (!LOCALES.includes(next)) return false;
  if (hasStorage) {
    store.set(localStorage, STORE_KEY, next);
    if (pick) store.set(sessionStorage, PICK_KEY, next);
  }
  if (next === current) return false;
  current = next;
  apply();
  listeners.forEach((fn) => fn(current));
  return true;
}

export function onLocaleChange(fn) {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

/** Giriş yapmadan seçilmiş dil (varsa) ve unutulur. */
export function takePickedLocale() {
  if (!hasStorage) return null;
  const v = store.get(sessionStorage, PICK_KEY);
  store.del(sessionStorage, PICK_KEY);
  return LOCALES.includes(v) ? v : null;
}

/* ══════════════════ Çeviri ══════════════════ */

const lookup = (dict, key) => key.split(".").reduce((o, k) => (o == null ? undefined : o[k]), dict);
const isPlural = (v) => v !== null && typeof v === "object" && typeof v.other === "string";
const isLeaf = (v) => typeof v === "string" || isPlural(v);
const warned = new Set();

function resolve(key) {
  const own = lookup(DICTIONARIES[current], key);
  if (isLeaf(own)) return own;
  const base = lookup(DICTIONARIES[DEFAULT_LOCALE], key);
  if (!warned.has(current + ":" + key)) {
    warned.add(current + ":" + key);
    console.warn(isLeaf(base)
      ? `[i18n] "${key}" ${current} sözlüğünde yok, ${DEFAULT_LOCALE} kullanılıyor`
      : `[i18n] "${key}" hiçbir sözlükte yok`);
  }
  return isLeaf(base) ? base : null;
}

/** Sözlükte böyle bir metin var mı (geçmiş satırının türü tanınıyor mu)? */
export const has = (key) => isLeaf(lookup(DICTIONARIES[current], key)) || isLeaf(lookup(DICTIONARIES[DEFAULT_LOCALE], key));

/**
 * Metin: t("home.cards", { count: 3 }) → "3 kart" / "3 cards".
 * {ad} yer tutucuları params'tan doldurulur ({brand} her zaman hazır). Değer
 * {one, other} biçimindeyse params.count'a göre Intl.PluralRules seçer.
 */
export function t(key, params = {}) {
  let v = resolve(key);
  if (v === null) return key;
  if (isPlural(v)) v = v[plurals().select(Number(params.count))] ?? v.other;
  return v.replace(/\{(\w+)\}/g, (m, name) => (name === "brand" ? BRAND : name in params ? String(params[name]) : m));
}

/* ══════════════════ Tarih, sayı, süre ══════════════════ */

const cache = new Map();
function intl(kind, opts = {}) {
  const k = kind + current + JSON.stringify(opts);
  if (!cache.has(k)) cache.set(k, new Intl[kind](current, opts));
  return cache.get(k);
}
const plurals = () => intl("PluralRules");

/** Kısaltma noktaları atılır: "2 dk. önce" → "2 dk önce", "3 hr." → "3 hr". Sıkışık listelerde gürültü. */
const tidy = (s) => s.replace(/(\p{L})\.(?=\s|$)/gu, "$1");

export const fmtDate = (value, opts) => intl("DateTimeFormat", opts).format(value instanceof Date ? value : new Date(value));
export const fmtNumber = (n, opts) => intl("NumberFormat", opts).format(n);

/** Tarih ve saat, saniyesiyle: geçmiş çubuğundaki an. */
export const fmtDateTime = (value) => fmtDate(value, {
  year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", second: "2-digit",
});

/**
 * "2 dk önce" / "2 min ago". seconds: kaç saniye önce. withSeconds: tanıtım
 * şeridindeki gibi "30 sn önce" de yazılsın; yoksa bir dakikanın altı "şimdi".
 */
export function ago(seconds, { withSeconds = false } = {}) {
  const s = Math.max(0, seconds);
  if (!isFinite(s)) return "";
  if (s < (withSeconds ? 3 : 60)) return t("time.now");
  const rtf = intl("RelativeTimeFormat", { style: "short", numeric: "always" });
  const [n, unit] = s < 60 ? [Math.round(s), "second"] : s < 3600 ? [Math.floor(s / 60), "minute"]
    : s < 86400 ? [Math.floor(s / 3600), "hour"] : [Math.floor(s / 86400), "day"];
  return tidy(rtf.format(-n, unit));
}

/** Bir an ne kadar önceydi: "şimdi", "5 dk önce", "3 gün önce". */
export function relTime(value) {
  const at = typeof value === "number" ? value : Date.parse(value);
  return ago((Date.now() - at) / 1000);
}

/** Bir ölçü birimiyle sayı: unit("day", 0.8, 1) → "0,8 gün" / "0.8 days". */
export function unit(name, value, digits = 0) {
  return tidy(intl("NumberFormat", {
    style: "unit", unit: name, unitDisplay: "short", minimumFractionDigits: digits, maximumFractionDigits: digits,
  }).format(value));
}

/** Saniyeyi okunur süreye çevirir: 45 sn / 12 dk / 3,5 sa / 2,0 gün (İngilizcede 45 sec / 12 min / 3.5 hr / 2.0 days). */
export function duration(sec) {
  if (sec == null) return t("common.noValue");
  if (sec < 60) return unit("second", Math.round(sec));
  if (sec < 3600) return unit("minute", Math.round(sec / 60));
  if (sec < 86400) return unit("hour", sec / 3600, 1);
  return unit("day", sec / 86400, 1);
}

/* ══════════════════ Dil düğmesi ══════════════════ */

/**
 * TR / EN düğmesi. Her dil kendi adıyla görünür (title ve lang ile), yeni bir
 * sözlük eklenince düğmesi kendiliğinden gelir. Tıklamayı çağıran ekran dinler
 * ([data-lang]).
 */
export function langSwitch(cls = "lang-sw") {
  return `<span class="${cls}" role="group" aria-label="${t("lang.label")}">${LOCALES.map((l) =>
    `<button type="button" data-lang="${l}" lang="${l}" title="${DICTIONARIES[l].meta.name}" aria-pressed="${l === current}">${DICTIONARIES[l].meta.code}</button>`).join("")}</span>`;
}
