#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════
# Sturnio yerel geliştirme yığını — Git Bash sürümü
#
# PowerShell'deki scripts/dev.ps1 ile aynı işi yapar, aynı adları ve kapıları
# üretir; ikisi aynı kopyayı yönetebilir. Ayrıntı: docs/GELISTIRME-ORTAMI.md
#
#   ./scripts/dev.sh up
#   ./scripts/dev.sh up --name ajan-deneme --port 8094
#   ./scripts/dev.sh clean --name ajan-deneme --volumes
# ═══════════════════════════════════════════════════════════════════
set -u

DEFAULT_NAME=sturnio
LEGACY_PREFIX=collab-board
BASE_APP_PORT=8080
BASE_DB_PORT=5433
BASE_MAIL_PORT=8025
BASE_REDIS_PORT=6380
BASE_SMTP_PORT=1025
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE_FILE="$ROOT/docker-compose.yml"
HOST_JAVA_FILE="$ROOT/docker/host-java.yml"

red()    { printf '\033[31m%s\033[0m\n' "$*"; }
green()  { printf '\033[32m%s\033[0m\n' "$*"; }
yellow() { printf '\033[33m%s\033[0m\n' "$*"; }
cyan()   { printf '\033[36m%s\033[0m\n' "$*"; }
fail()   { echo; red "HATA: $*"; exit 1; }

usage() {
  cyan 'Sturnio geliştirme ortamı'
  cat <<'EOF'

Kullanım:  ./scripts/dev.sh <komut> [--name <kopya>] [--port <kapı>]

  up        Derle ve başlat (ilk sefer birkaç dakika sürer)
            --real-mail   postalar gerçekten gönderilsin (.env ayarları)
            --infra-only  Java'yı kendi makinende çalıştıracaksan
  down      Durdur, konteynerleri kaldır; veri kalır
  restart   Yeniden başlat (kod değiştiyse up kullan)
  logs      Günlükler (--service app|postgres|redis|mailpit|all, --no-follow)
  status    Durum ve bütün kopyalar
  clean     Konteyner, ağ ve imajı kaldır (--volumes: veriyi de, onay sorar)
  mail      Mailpit'i aç (--no-browser: yalnızca adresi yaz)
  db        Veritabanı bağlantı bilgileri
  config    Başlatmadan ad, kapı ve veri birimini göster

Diğer seçenekler: --db-port, --mail-port, --force (clean --volumes onaysız;
ana kopyada kullanılamaz). PowerShell yazımı da geçer: -Name, -Port, -Volumes.

Ayrıntı: docs/GELISTIRME-ORTAMI.md
EOF
}

# ── Argümanlar ─────────────────────────────────────────────────────
COMMAND=help
NAME=$DEFAULT_NAME
PORT=0; DB_PORT_ARG=0; MAIL_PORT_ARG=0
REAL_MAIL=0; INFRA_ONLY=0; VOLUMES=0; FORCE=0; NO_FOLLOW=0; NO_BROWSER=0
SERVICE=app

if [ $# -gt 0 ]; then COMMAND=$1; shift; fi
while [ $# -gt 0 ]; do
  case "$1" in
    -n|--name|-Name)          NAME=${2:-}; shift ;;
    -p|--port|-Port)          PORT=${2:-0}; shift ;;
    --db-port|-DbPort)        DB_PORT_ARG=${2:-0}; shift ;;
    --mail-port|-MailPort)    MAIL_PORT_ARG=${2:-0}; shift ;;
    --real-mail|-RealMail)    REAL_MAIL=1 ;;
    --infra-only|-InfraOnly)  INFRA_ONLY=1 ;;
    --volumes|-Volumes)       VOLUMES=1 ;;
    --force|-Force)           FORCE=1 ;;
    --service|-Service)       SERVICE=${2:-app}; shift ;;
    --no-follow|-NoFollow)    NO_FOLLOW=1 ;;
    --no-browser|-NoBrowser)  NO_BROWSER=1 ;;
    -h|--help)                COMMAND=help ;;
    *) fail "Bilinmeyen seçenek: $1  (yardım: ./scripts/dev.sh help)" ;;
  esac
  shift
done

case "$COMMAND" in
  up|down|restart|logs|status|clean|mail|db|config) ;;
  help|-h|--help) usage; exit 0 ;;
  *) fail "Bilinmeyen komut: $COMMAND  (yardım: ./scripts/dev.sh help)" ;;
esac
case "$SERVICE" in app|postgres|redis|mailpit|all) ;; *) fail "--service şunlardan biri olmalı: app, postgres, redis, mailpit, all" ;; esac
for v in "$PORT" "$DB_PORT_ARG" "$MAIL_PORT_ARG"; do
  case "$v" in ''|*[!0-9]*) fail "Kapı bir sayı olmalı: '$v'" ;; esac
done

if ! printf '%s' "$NAME" | grep -Eq '^[a-z0-9][a-z0-9_-]*$'; then
  fail "Kopya adı '$NAME' olamaz. Küçük harf, rakam, '-' ve '_' kullan; harf ya da rakamla başlasın (ör. ajan-deneme)."
fi
[ "$NAME" = "$LEGACY_PREFIX" ] && fail "'$LEGACY_PREFIX' eski kurulumun adı; karışmasın diye kullanılamaz. Ana kopya için --name verme (varsayılan: $DEFAULT_NAME)."

name_arg() { [ "$NAME" = "$DEFAULT_NAME" ] || printf ' --name %s' "$NAME"; }

# ── Docker ─────────────────────────────────────────────────────────
if ! docker version --format '{{.Server.Version}}' >/dev/null 2>&1; then
  fail "Docker'a ulaşılamadı. Docker Desktop açık mı? Açıp durum yeşile dönünce tekrar dene."
fi

compose() {
  if [ "${WITH_HOST_JAVA:-0}" = 1 ]; then
    docker compose --project-directory "$ROOT" -p "$NAME" -f "$COMPOSE_FILE" -f "$HOST_JAVA_FILE" "$@"
  else
    docker compose --project-directory "$ROOT" -p "$NAME" -f "$COMPOSE_FILE" "$@"
  fi
}
# Bu kopyanın konteynerleri: ad|servis|durum|uygulama-kapısı|db-kapısı|posta-kapısı|posta-modu
copy_containers() {
  docker ps -a --filter "label=com.docker.compose.project=$NAME" \
    --format '{{.Names}}|{{.Label "com.docker.compose.service"}}|{{.State}}|{{.Label "com.sturnio.app-port"}}|{{.Label "com.sturnio.db-port"}}|{{.Label "com.sturnio.mail-port"}}|{{.Label "com.sturnio.mail-mode"}}|{{.Status}}'
}
EXISTING="$(copy_containers)"
field_of_service() {  # $1 servis, $2 alan no
  printf '%s\n' "$EXISTING" | awk -F'|' -v s="$1" -v f="$2" '$2==s {print $f; exit}'
}
first_label() {  # $1 alan no (4-6)
  printf '%s\n' "$EXISTING" | awk -F'|' -v f="$1" '$f!="" {print $f; exit}'
}
require_copy() {
  [ -n "$EXISTING" ] || fail "'$NAME' adlı bir kopya yok. Başlatmak için: ./scripts/dev.sh up$(name_arg)"
}

# Addan sabit kapı (8081-8129) — dev.ps1 ile aynı hesap.
derived_port() {
  local s=$1 h=0 i c
  for ((i = 0; i < ${#s}; i++)); do
    c=$(printf '%d' "'${s:i:1}")
    h=$(( (h * 31 + c) % 49 ))
  done
  echo $((8081 + h))
}

# ── Kopyanın ayarları ──────────────────────────────────────────────
EXPLICIT_PORT=0
if [ "$PORT" -gt 0 ]; then EXPLICIT_PORT=1; else PORT=$(first_label 4); PORT=${PORT:-0}; fi
if [ "$PORT" -le 0 ]; then
  if [ "$NAME" = "$DEFAULT_NAME" ]; then PORT=$BASE_APP_PORT; else PORT=$(derived_port "$NAME"); fi
fi
OFFSET=$((PORT - BASE_APP_PORT))
DB_PORT=$DB_PORT_ARG
if [ "$DB_PORT" -le 0 ] && [ $EXPLICIT_PORT = 0 ]; then DB_PORT=$(first_label 5); DB_PORT=${DB_PORT:-0}; fi
[ "$DB_PORT" -gt 0 ] || DB_PORT=$((BASE_DB_PORT + OFFSET))
MAIL_PORT=$MAIL_PORT_ARG
if [ "$MAIL_PORT" -le 0 ] && [ $EXPLICIT_PORT = 0 ]; then MAIL_PORT=$(first_label 6); MAIL_PORT=${MAIL_PORT:-0}; fi
[ "$MAIL_PORT" -gt 0 ] || MAIL_PORT=$((BASE_MAIL_PORT + OFFSET))
REDIS_PORT=$((BASE_REDIS_PORT + OFFSET))
SMTP_PORT=$((BASE_SMTP_PORT + OFFSET))

for p in $PORT $DB_PORT $MAIL_PORT; do
  { [ "$p" -ge 1024 ] && [ "$p" -le 65535 ]; } || fail "Kapı $p kullanılamaz (1024-65535 arası olmalı). --port ile başka bir değer ver."
done
if [ "$PORT" = "$DB_PORT" ] || [ "$PORT" = "$MAIL_PORT" ] || [ "$DB_PORT" = "$MAIL_PORT" ]; then
  fail "Uygulama, veritabanı ve Mailpit kapıları birbirinden farklı olmalı ($PORT, $DB_PORT, $MAIL_PORT)."
fi

ALL_VOLUMES="$(docker volume ls -q)"
has_volume() { printf '%s\n' "$ALL_VOLUMES" | grep -qx "$1"; }
USES_LEGACY=0
if [ "$NAME" = "$DEFAULT_NAME" ] && has_volume "${LEGACY_PREFIX}_postgres_data"; then USES_LEGACY=1; fi
if [ $USES_LEGACY = 1 ]; then VOLUME_PREFIX=$LEGACY_PREFIX; VOLUME_EXTERNAL=true
else VOLUME_PREFIX=$NAME; VOLUME_EXTERNAL=false; fi
DB_VOLUME="${VOLUME_PREFIX}_postgres_data"
REDIS_VOLUME="${VOLUME_PREFIX}_redis_data"

export STURNIO_APP_PORT=$PORT STURNIO_DB_PORT=$DB_PORT STURNIO_MAIL_UI_PORT=$MAIL_PORT
export STURNIO_VOLUME_PREFIX=$VOLUME_PREFIX STURNIO_VOLUME_EXTERNAL=$VOLUME_EXTERNAL
export STURNIO_REDIS_PORT=$REDIS_PORT STURNIO_MAIL_SMTP_PORT=$SMTP_PORT
[ $REAL_MAIL = 1 ] && export STURNIO_MAIL=gercek

# ── Denetimler ─────────────────────────────────────────────────────
port_user() {  # kapıyı başkası tutuyorsa açıklama yazar
  local p=$1 line proj other=""
  while IFS='|' read -r cname proj; do
    [ -z "$cname" ] && continue
    [ "$proj" = "$NAME" ] && return 0
    other="Docker konteyneri '$cname' (proje: ${proj:-yok})"
  done <<EOF
$(docker ps --filter "publish=$p" --format '{{.Names}}|{{.Label "com.docker.compose.project"}}')
EOF
  if [ -n "$other" ]; then echo "$other"; return 0; fi
  if command -v netstat >/dev/null 2>&1 && netstat -an 2>/dev/null | grep -Eq "[:.]$p[[:space:]].*LISTEN"; then
    echo "başka bir program"
  fi
}
assert_ports_free() {  # argümanlar: "ad:kapı" ...
  local item label p who problems=""
  for item in "$@"; do
    label=${item%%:*}; p=${item##*:}
    who=$(port_user "$p")
    [ -n "$who" ] && problems="$problems  $label kapısı $p: $who kullanıyor"$'\n'
  done
  if [ -n "$problems" ]; then
    echo; red 'Bu kapılar dolu:'; printf '%s' "$problems"
    if [ "$NAME" = "$DEFAULT_NAME" ] && [ -n "$(docker ps -a -q --filter "label=com.docker.compose.project=$LEGACY_PREFIX")" ]; then
      echo; yellow 'Eski kurulum (collabboard-*) hâlâ çalışıyor olabilir. Geçiş adımları:'
      yellow '  docs/GELISTIRME-ORTAMI.md  →  "Eski kurulumdan geçiş"'
    fi
    fail "Başka bir kapıyla dene, ör.: ./scripts/dev.sh up$(name_arg) --port 8090"
  fi
}
assert_volume_not_shared() {
  local vol=$1 cname proj
  while IFS='|' read -r cname proj; do
    [ -z "$cname" ] && continue
    if [ "$proj" != "$NAME" ]; then
      echo; red "'$vol' veri birimini şu an '$cname' konteyneri kullanıyor (proje: $proj)."
      red 'İki veritabanı aynı veriyi aynı anda açarsa veri bozulur; bu yüzden başlatılmadı.'
      if [ "$proj" = "$LEGACY_PREFIX" ]; then
        echo; yellow 'Eski kurulumu kapat (veri SİLİNMEZ), sonra tekrar dene:'
        yellow "  docker compose -p $LEGACY_PREFIX down"
      fi
      fail 'Önce o konteyneri durdur.'
    fi
  done <<EOF
$(docker ps --filter "volume=$vol" --format '{{.Names}}|{{.Label "com.docker.compose.project"}}')
EOF
}
wait_app_healthy() {
  local url="http://localhost:$PORT/actuator/health" i state
  printf 'Uygulama açılıyor '
  for ((i = 0; i < 120; i++)); do
    if curl -fsS --max-time 3 "$url" 2>/dev/null | grep -q '"UP"'; then green ' hazır.'; return 0; fi
    state=$(docker ps -a --filter "label=com.docker.compose.project=$NAME" --filter label=com.docker.compose.service=app --format '{{.State}}')
    case "$state" in exited|dead|restarting)
      echo; red 'Uygulama açılırken durdu. Son satırlar:'; compose logs --tail 40 app; return 1 ;;
    esac
    printf '.'; sleep 2
  done
  echo; return 1
}
open_url() {
  if command -v start >/dev/null 2>&1; then start "" "$1"
  elif command -v xdg-open >/dev/null 2>&1; then xdg-open "$1" >/dev/null 2>&1
  elif command -v open >/dev/null 2>&1; then open "$1"
  fi
}

# ── Komutlar ───────────────────────────────────────────────────────
case "$COMMAND" in
  up)
    if [ $INFRA_ONLY = 1 ]; then
      assert_ports_free "Veritabanı:$DB_PORT" "Mailpit:$MAIL_PORT" "Redis:$REDIS_PORT" "Mailpit SMTP:$SMTP_PORT"
    else
      assert_ports_free "Uygulama:$PORT" "Veritabanı:$DB_PORT" "Mailpit:$MAIL_PORT"
    fi
    assert_volume_not_shared "$DB_VOLUME"
    assert_volume_not_shared "$REDIS_VOLUME"
    if [ $USES_LEGACY = 1 ] && ! has_volume "$REDIS_VOLUME"; then docker volume create "$REDIS_VOLUME" >/dev/null; fi

    if [ $INFRA_ONLY = 1 ]; then
      [ -n "$(field_of_service app 1)" ] && compose stop app
      cyan "Altyapı başlatılıyor (kopya: $NAME)..."
      WITH_HOST_JAVA=1 compose up -d --wait postgres redis mailpit || fail 'Altyapı başlatılamadı; yukarıdaki mesaja bak.'
      profiles='dev,mailpit'; [ $REAL_MAIL = 1 ] && profiles='dev'
      envs="DB_PORT=$DB_PORT REDIS_PORT=$REDIS_PORT MAILPIT_SMTP_PORT=$SMTP_PORT"
      [ "$PORT" != "$BASE_APP_PORT" ] && envs="$envs PORT=$PORT APP_BASE_URL=http://localhost:$PORT CORS_ALLOWED_ORIGINS=http://localhost:$PORT"
      echo; green "Altyapı hazır — kopya: $NAME"
      echo "  Veritabanı  localhost:$DB_PORT    Redis  127.0.0.1:$REDIS_PORT"
      echo "  Mailpit     http://localhost:$MAIL_PORT  (SMTP 127.0.0.1:$SMTP_PORT)"
      echo; cyan "Şimdi Java'yı bu klasörde çalıştır:"
      echo "  $envs ./mvnw spring-boot:run -Dspring-boot.run.profiles=$profiles"
      echo; echo "Uygulama: http://localhost:$PORT   Bitince: Ctrl+C, sonra ./scripts/dev.sh down$(name_arg)"
      exit 0
    fi

    cyan "Sturnio derleniyor ve başlatılıyor (kopya: $NAME)..."
    echo 'İlk sefer birkaç dakika sürebilir; sonrakiler yalnızca değişeni derler.'
    compose up -d --build --remove-orphans || fail 'Başlatılamadı; yukarıdaki mesaja bak.'
    ok=0; wait_app_healthy || ok=1
    EXISTING="$(copy_containers)"
    mode=$(field_of_service app 7); mode=${mode:-mailpit}
    echo
    if [ $ok = 0 ]; then green "Sturnio hazır — kopya: $NAME"; else yellow "Sturnio başlatıldı ama henüz hazır değil — kopya: $NAME"; fi
    echo "  Uygulama         http://localhost:$PORT"
    if [ "$mode" = gercek ]; then
      yellow '  Posta            GERÇEK gönderim (.env ayarları). Postalar gerçek adreslere gider.'
      echo "  Mailpit          http://localhost:$MAIL_PORT  (bu modda boş kalır)"
    else
      echo "  Posta (Mailpit)  http://localhost:$MAIL_PORT  — kayıt ve doğrulama postaları burada"
    fi
    echo "  Veritabanı       localhost:$DB_PORT  (ayrıntı: ./scripts/dev.sh db$(name_arg))"
    if [ $USES_LEGACY = 1 ]; then echo "  Veri             $DB_VOLUME  (eski kurulumdaki verin)"
    else echo "  Veri             $DB_VOLUME"; fi
    echo
    echo "  Günlükler        ./scripts/dev.sh logs$(name_arg)"
    echo "  Durdurmak        ./scripts/dev.sh down$(name_arg)     (veri kalır)"
    echo "  Tamamen kaldır   ./scripts/dev.sh clean$(name_arg)"
    if [ $ok != 0 ]; then echo; yellow "Uygulama henüz 'hazır' demedi. Ne olduğunu görmek için: ./scripts/dev.sh logs$(name_arg)"; exit 1; fi
    ;;

  down)
    require_copy
    compose down --remove-orphans || fail 'Durdurulamadı; yukarıdaki mesaja bak.'
    echo; green "'$NAME' durduruldu. Verin duruyor; tekrar açmak için: ./scripts/dev.sh up$(name_arg)"
    ;;

  restart)
    require_copy
    compose restart || fail 'Yeniden başlatılamadı.'
    [ -n "$(field_of_service app 1)" ] && wait_app_healthy
    green "'$NAME' yeniden başladı: http://localhost:$PORT"
    ;;

  logs)
    require_copy
    args=(logs --tail 200)
    if [ $NO_FOLLOW = 0 ]; then args+=(-f); echo 'Çıkmak için Ctrl+C (yığın çalışmaya devam eder).'; fi
    [ "$SERVICE" != all ] && args+=("$SERVICE")
    compose "${args[@]}"
    ;;

  status)
    cyan "Kopya: $NAME"
    if [ -z "$EXISTING" ]; then
      echo "  Çalışmıyor. Başlatmak için: ./scripts/dev.sh up$(name_arg)"
    else
      printf '%s\n' "$EXISTING" | sort | awk -F'|' '{printf "  %-28s %s (%s)\n", $1, $3, $8}'
      echo
      echo "  Uygulama   http://localhost:$PORT"
      echo "  Mailpit    http://localhost:$MAIL_PORT"
      echo "  Postgres   localhost:$DB_PORT   (veri: $DB_VOLUME)"
    fi
    echo; cyan 'Bu bilgisayardaki Sturnio kopyaları:'
    all=$(docker ps -a --filter label=com.sturnio.instance --format '{{.Label "com.sturnio.instance"}}|{{.State}}|{{.Label "com.sturnio.app-port"}}')
    if [ -z "$all" ]; then echo '  (hiç yok)'
    else
      printf '%s\n' "$all" | awk -F'|' '{t[$1]++; if ($2=="running") r[$1]++; p[$1]=$3}
        END {for (k in t) printf "  %-22s %d/%d çalışıyor   http://localhost:%s\n", k, r[k], t[k], p[k]}' | sort
    fi
    legacy=$(docker ps -a --filter "label=com.docker.compose.project=$LEGACY_PREFIX" --format '{{.Names}}' | tr '\n' ' ')
    if [ -n "$legacy" ]; then
      echo; yellow "Eski kurulum hâlâ duruyor: $legacy"
      yellow '  Geçiş: docs/GELISTIRME-ORTAMI.md → "Eski kurulumdan geçiş"'
    fi
    ;;

  clean)
    to_remove=""
    if [ $VOLUMES = 1 ]; then
      for v in "$DB_VOLUME" "$REDIS_VOLUME"; do has_volume "$v" && to_remove="$to_remove $v"; done
    fi
    # 'down' sonrası konteyner kalmaz ama imaj ve ağ kalabilir.
    leftovers="$(docker image ls -q "$NAME-app")$(docker network ls -q --filter "label=com.docker.compose.project=$NAME")"
    has_stack=0; { [ -n "$EXISTING" ] || [ -n "$leftovers" ]; } && has_stack=1
    if [ $has_stack = 0 ] && [ -z "$to_remove" ]; then green "'$NAME' adlı kopyadan kaldırılacak bir şey yok."; exit 0; fi
    if [ -n "$to_remove" ]; then
      echo; yellow 'Şu veri birimleri KALICI olarak silinecek:'
      for v in $to_remove; do yellow "  $v"; done
      if [ "$NAME" = "$DEFAULT_NAME" ]; then
        echo; red 'Bu ANA kopyanın verisi: panoların, kullanıcıların, her şey. Geri alınamaz.'
        [ $FORCE = 1 ] && fail '--force ana kopyada kullanılamaz; onayı elle ver.'
      fi
      if [ $FORCE = 0 ]; then
        answer=""
        if ! read -r -p "Onaylamak için kopyanın adını yaz ($NAME): " answer; then
          echo; yellow 'Onay sorulamadı (bu pencere soru soramıyor).'
          [ "$NAME" != "$DEFAULT_NAME" ] && yellow 'Ajan kopyasıysa --force ile onaysız silinebilir.'
        fi
        if [ "$answer" != "$NAME" ]; then green 'Vazgeçildi; hiçbir şey silinmedi.'; exit 0; fi
      fi
    fi
    if [ $has_stack = 1 ]; then
      # --rmi local: yalnızca bu kopya için derlenen uygulama imajı silinir.
      compose down --remove-orphans --rmi local || fail 'Kaldırılamadı; yukarıdaki mesaja bak.'
    fi
    for v in $to_remove; do
      docker volume rm "$v" >/dev/null || fail "'$v' silinemedi."
      echo "  silindi: $v"
    done
    echo
    if [ $VOLUMES = 1 ]; then green "'$NAME' verisiyle birlikte kaldırıldı."
    else green "'$NAME' kaldırıldı. Veri birimi duruyor: $DB_VOLUME (silmek için: --volumes)"; fi
    ;;

  mail)
    require_copy
    [ "$(field_of_service mailpit 3)" = running ] || fail "Mailpit çalışmıyor. Başlatmak için: ./scripts/dev.sh up$(name_arg)"
    [ "$(field_of_service app 7)" = gercek ] && yellow "Not: bu kopya GERÇEK posta modunda; postalar Mailpit'e değil gerçek adreslere gidiyor."
    url="http://localhost:$MAIL_PORT"
    echo "Mailpit: $url"
    [ $NO_BROWSER = 0 ] && open_url "$url"
    ;;

  db)
    require_copy
    pg=$(field_of_service postgres 1)
    [ -n "$pg" ] || fail 'Bu kopyada Postgres konteyneri yok.'
    pg_env() { docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$pg" | sed -n "s/^$1=//p"; }
    db_name=$(pg_env POSTGRES_DB); db_user=$(pg_env POSTGRES_USER)
    pass_text='.env dosyandaki DB_PASSWORD'; [ "$(pg_env POSTGRES_PASSWORD)" = postgres ] && pass_text=postgres
    cyan "Veritabanı — kopya: $NAME"
    echo '  Sunucu (Host)   localhost'
    echo "  Kapı (Port)     $DB_PORT"
    echo "  Veritabanı      $db_name"
    echo "  Kullanıcı       $db_user"
    echo "  Parola          $pass_text"
    echo "  JDBC adresi     jdbc:postgresql://localhost:$DB_PORT/$db_name"
    echo "  Veri birimi     $DB_VOLUME"
    echo; echo 'Komut satırından bağlanmak için:'
    echo "  docker exec -it $pg psql -U $db_user -d $db_name"
    ;;

  config)
    # Yalnızca sır olmayan satırlar: proje ve birim adları, kapılar, etiketler.
    cyan "Kopya (Compose projesi): $NAME"
    compose config | grep -E '^name:|^    name:|published:|com\.sturnio\.|external:' | awk '!/com\.sturnio\./ || !seen[$0]++'
    ;;
esac

exit 0
