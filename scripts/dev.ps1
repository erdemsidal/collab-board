<#
.SYNOPSIS
    Sturnio yerel geliştirme yığınını yönetir: uygulama, Postgres, Redis, Mailpit.

.DESCRIPTION
    Aynı yığının birkaç kopyası yan yana çalışabilir. Her kopyanın adı (-Name),
    kapıları, ağı ve verisi ayrıdır. Ayrıntılı anlatım: docs/GELISTIRME-ORTAMI.md

    Komutlar:
      up       Derle ve başlat; hazır olunca adresleri yazar
      down     Durdur ve konteynerleri kaldır (veri ve imaj kalır)
      restart  Konteynerleri yeniden başlat (kod değiştiyse 'up' kullan)
      logs     Günlükleri göster (varsayılan: uygulama, canlı akış)
      status   Bu kopyanın durumu ve makinedeki bütün Sturnio kopyaları
      clean    Konteynerleri, ağı ve derlenen imajı kaldır; -Volumes ile veriyi de
      mail     Mailpit'i (yakalanan postalar) tarayıcıda aç
      db       Veritabanı bağlantı bilgilerini yaz (DBeaver için)
      config   Başlatmadan, kopyanın hangi ad, kapı ve veriyle açılacağını göster
      help     Bu yardım

.EXAMPLE
    .\scripts\dev.ps1 up
    Ana kopyayı (sturnio) 8080'de başlatır.

.EXAMPLE
    .\scripts\dev.ps1 up -Name ajan-deneme -Port 8094
    İkinci bir kopyayı 8094'te başlatır; diğer kapılar buna göre kayar.

.EXAMPLE
    .\scripts\dev.ps1 clean -Name ajan-deneme -Volumes
    Kopyayı verisiyle birlikte kaldırır (onay sorar).
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('up', 'down', 'restart', 'logs', 'status', 'clean', 'mail', 'db', 'config', 'help')]
    [string]$Command = 'help',

    # Kopyanın adı = Compose proje adı. Konteynerler "<ad>-app-1" gibi adlanır.
    [string]$Name = 'sturnio',

    # Uygulamanın kapısı. Verilmezse: kopya zaten varsa onun kapısı, ana kopyada
    # 8080, başka adlarda addan hesaplanan 8081-8129 arası bir kapı.
    [ValidateRange(0, 65535)][int]$Port = 0,

    # Elle vermek gerekmez: uygulama kapısıyla aynı miktarda kayar (5433 + fark).
    [ValidateRange(0, 65535)][int]$DbPort = 0,

    # Elle vermek gerekmez: 8025 + fark.
    [ValidateRange(0, 65535)][int]$MailPort = 0,

    # up: postalar Mailpit yerine .env'deki gerçek ayarlarla (Brevo/Gmail) gider.
    [switch]$RealMail,

    # up: uygulama konteynerini açmaz; Java'yı kendi makinende çalıştırmak için
    # Postgres, Redis ve Mailpit'i açar ve çalıştırma komutunu yazar.
    [switch]$InfraOnly,

    # clean: veri birimlerini (veritabanı, Redis) de siler. Onay sorar.
    [switch]$Volumes,

    # clean -Volumes: onay sormadan sil. Ana kopyada (sturnio) KULLANILAMAZ.
    [switch]$Force,

    # logs: hangi servis
    [ValidateSet('app', 'postgres', 'redis', 'mailpit', 'all')]
    [string]$Service = 'app',

    # logs: canlı izleme yerine son satırları yazıp çık
    [switch]$NoFollow,

    # mail: tarayıcıyı açma, yalnızca adresi yaz
    [switch]$NoBrowser
)

$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'

# ── Sabitler ───────────────────────────────────────────────────────
$DefaultName   = 'sturnio'
$LegacyPrefix  = 'collab-board'   # eski kurulumun proje adı ve veri birimi öneki
$BaseAppPort   = 8080
$BaseDbPort    = 5433
$BaseMailPort  = 8025
$BaseRedisPort = 6380             # yalnızca -InfraOnly
$BaseSmtpPort  = 1025             # yalnızca -InfraOnly
$Root          = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$ComposeFile   = Join-Path $Root 'docker-compose.yml'
$HostJavaFile  = Join-Path $Root 'docker\host-java.yml'

# ── Yazdırma ───────────────────────────────────────────────────────
function Say([string]$Text = '', [string]$Color = 'Gray') { Write-Host $Text -ForegroundColor $Color }
function Fail([string]$Text) {
    Write-Host ''
    Write-Host "HATA: $Text" -ForegroundColor Red
    exit 1
}
# Komut örneklerine eklenecek "-Name x" (ana kopyada boş)
function Get-NameArg { if ($Name -eq $DefaultName) { return '' } return " -Name $Name" }

function Show-Help {
    Say 'Sturnio geliştirme ortamı' 'Cyan'
    Say ''
    Say 'Kullanım:  .\scripts\dev.ps1 <komut> [-Name <kopya>] [-Port <kapı>]'
    Say ''
    Say '  up        Derle ve başlat (ilk sefer birkaç dakika sürer)'
    Say '            -RealMail   postalar gerçekten gönderilsin (.env ayarları)'
    Say '            -InfraOnly  Java''yı kendi makinende çalıştıracaksan'
    Say '  down      Durdur, konteynerleri kaldır; veri kalır'
    Say '  restart   Yeniden başlat (kod değiştiyse up kullan)'
    Say '  logs      Günlükler (-Service app|postgres|redis|mailpit|all, -NoFollow)'
    Say '  status    Durum ve bütün kopyalar'
    Say '  clean     Konteyner, ağ ve imajı kaldır (-Volumes: veriyi de, onay sorar)'
    Say '  mail      Mailpit''i aç (-NoBrowser: yalnızca adresi yaz)'
    Say '  db        Veritabanı bağlantı bilgileri'
    Say '  config    Başlatmadan ad, kapı ve veri birimini göster'
    Say ''
    Say 'Örnekler:'
    Say '  .\scripts\dev.ps1 up'
    Say '  .\scripts\dev.ps1 up -Name ajan-deneme -Port 8094'
    Say '  .\scripts\dev.ps1 clean -Name ajan-deneme -Volumes'
    Say ''
    Say 'Ayrıntı: docs/GELISTIRME-ORTAMI.md'
}

if ($Command -eq 'help') { Show-Help; exit 0 }

# ── Ad denetimi ────────────────────────────────────────────────────
if ($Name -cnotmatch '^[a-z0-9][a-z0-9_-]*$') {
    Fail "Kopya adı '$Name' olamaz. Küçük harf, rakam, '-' ve '_' kullan; harf ya da rakamla başlasın (ör. ajan-deneme)."
}
if ($Name -eq $LegacyPrefix) {
    Fail "'$LegacyPrefix' eski kurulumun adı; karışmasın diye kullanılamaz. Ana kopya için -Name verme (varsayılan: $DefaultName)."
}

# ── Docker ayakta mı? ──────────────────────────────────────────────
$serverVersion = & docker version --format '{{.Server.Version}}' 2>$null
if ($LASTEXITCODE -ne 0 -or -not $serverVersion) {
    Fail "Docker'a ulaşılamadı. Docker Desktop açık mı? Açıp sol alttaki durum yeşile dönünce tekrar dene."
}

# ── Docker yardımcıları ────────────────────────────────────────────
# docker inspect çıktısını nesnelere çevirir. Ortam değişkenleri (sırlar) de
# içinde olur; yalnızca bellekte kalır, hiçbir yere yazdırılmaz.
function Get-Inspect($Ids) {
    $list = @($Ids | Where-Object { $_ })
    if ($list.Count -eq 0) { return }
    $json = (& docker inspect @list) -join "`n"
    if ($LASTEXITCODE -ne 0 -or -not $json) { return }
    foreach ($o in (ConvertFrom-Json -InputObject $json)) { $o }
}
function Get-ProjectContainers([string]$Project) {
    $ids = & docker ps -a -q --filter "label=com.docker.compose.project=$Project"
    Get-Inspect $ids
}
function Get-ContainerName($C) { return $C.Name.TrimStart('/') }
function Get-Label($C, [string]$Key) {
    if (-not $C.Config.Labels) { return $null }
    return $C.Config.Labels.$Key
}
function Get-ServiceContainer([string]$Svc) {
    foreach ($c in $script:Existing) {
        if ((Get-Label $c 'com.docker.compose.service') -eq $Svc) { return $c }
    }
    return $null
}
function Get-ContainerEnv($C, [string]$Key) {
    foreach ($line in @($C.Config.Env)) {
        if ($line.StartsWith("$Key=")) { return $line.Substring($Key.Length + 1) }
    }
    return $null
}
function Get-StateText($C) {
    $s = $C.State.Status
    $h = $null
    if ($C.State.Health) { $h = $C.State.Health.Status }
    $tr = @{ running = 'çalışıyor'; exited = 'durmuş'; created = 'oluşturuldu'; restarting = 'yeniden başlıyor'; paused = 'duraklatıldı'; dead = 'bozuk' }
    $text = $s
    if ($tr.ContainsKey($s)) { $text = $tr[$s] }
    if ($s -eq 'running' -and $h) {
        $htr = @{ healthy = 'sağlıklı'; starting = 'açılıyor'; unhealthy = 'SAĞLIKSIZ' }
        if ($htr.ContainsKey($h)) { $text += ", $($htr[$h])" } else { $text += ", $h" }
    }
    return $text
}

# Addan sabit bir kapı türetir (8081-8129). dev.sh de aynı hesabı yapar.
function Get-DerivedPort([string]$Text) {
    $h = 0
    foreach ($ch in $Text.ToCharArray()) { $h = ($h * 31 + [int][char]$ch) % 49 }
    return 8081 + $h
}

# ── Kopyanın ayarlarını çöz ────────────────────────────────────────
$script:Existing = @(Get-ProjectContainers $Name)

function Get-LabelPort([string]$Key) {
    foreach ($c in $script:Existing) {
        $v = Get-Label $c $Key
        if ($v) { return [int]$v }
    }
    return 0
}

$explicitPort = ($Port -gt 0)
if (-not $explicitPort) { $Port = Get-LabelPort 'com.sturnio.app-port' }
if ($Port -le 0) {
    if ($Name -eq $DefaultName) { $Port = $BaseAppPort } else { $Port = Get-DerivedPort $Name }
}
$offset = $Port - $BaseAppPort
if ($DbPort -le 0) {
    if (-not $explicitPort) { $DbPort = Get-LabelPort 'com.sturnio.db-port' }
    if ($DbPort -le 0) { $DbPort = $BaseDbPort + $offset }
}
if ($MailPort -le 0) {
    if (-not $explicitPort) { $MailPort = Get-LabelPort 'com.sturnio.mail-port' }
    if ($MailPort -le 0) { $MailPort = $BaseMailPort + $offset }
}
$RedisPort = $BaseRedisPort + $offset
$SmtpPort  = $BaseSmtpPort + $offset

foreach ($p in @($Port, $DbPort, $MailPort)) {
    if ($p -lt 1024 -or $p -gt 65535) {
        Fail "Kapı $p kullanılamaz (1024-65535 arası olmalı). -Port ile başka bir değer ver."
    }
}
if (($Port -eq $DbPort) -or ($Port -eq $MailPort) -or ($DbPort -eq $MailPort)) {
    Fail "Uygulama, veritabanı ve Mailpit kapıları birbirinden farklı olmalı ($Port, $DbPort, $MailPort)."
}

# Veri birimleri. Ana kopya, eski kurulumun verisi varsa onu kullanır.
$allVolumes = @(& docker volume ls -q)
$UsesLegacyData = ($Name -eq $DefaultName) -and ($allVolumes -contains "${LegacyPrefix}_postgres_data")
if ($UsesLegacyData) {
    $VolumePrefix = $LegacyPrefix
    $VolumeExternal = 'true'
} else {
    $VolumePrefix = $Name
    $VolumeExternal = 'false'
}
$DbVolume    = "${VolumePrefix}_postgres_data"
$RedisVolume = "${VolumePrefix}_redis_data"

# Compose'a giden değişkenler. Yalnızca bu betik çalışırken geçerli; sonunda
# eski hâline döner (PowerShell betikleri aynı oturumda çalışır, sızmasın).
$StackEnv = [ordered]@{
    STURNIO_APP_PORT        = "$Port"
    STURNIO_DB_PORT         = "$DbPort"
    STURNIO_MAIL_UI_PORT    = "$MailPort"
    STURNIO_VOLUME_PREFIX   = $VolumePrefix
    STURNIO_VOLUME_EXTERNAL = $VolumeExternal
    STURNIO_REDIS_PORT      = "$RedisPort"
    STURNIO_MAIL_SMTP_PORT  = "$SmtpPort"
}
if ($RealMail) { $StackEnv['STURNIO_MAIL'] = 'gercek' }

function Invoke-Compose([string[]]$ComposeArgs, [switch]$WithHostJava) {
    $all = @('compose', '--project-directory', $Root, '-p', $Name, '-f', $ComposeFile)
    if ($WithHostJava) { $all += @('-f', $HostJavaFile) }
    & docker @($all + $ComposeArgs)
}

# ── Denetimler ─────────────────────────────────────────────────────
function Get-OwnHostPorts {
    $ports = @()
    foreach ($c in $script:Existing) {
        if ($c.State.Status -ne 'running') { continue }
        $np = $c.NetworkSettings.Ports
        if (-not $np) { continue }
        foreach ($prop in $np.PSObject.Properties) {
            foreach ($b in @($prop.Value)) {
                if ($b -and $b.HostPort) { $ports += [int]$b.HostPort }
            }
        }
    }
    return $ports
}

# Kapıyı başkası tutuyorsa kim olduğunu söyler; boşsa (ya da bu kopyanınsa) $null.
function Find-PortUser([int]$P) {
    $ids = @(& docker ps -q --filter "publish=$P")
    foreach ($c in @(Get-Inspect $ids)) {
        $proj = Get-Label $c 'com.docker.compose.project'
        if ($proj -eq $Name) { continue }
        $desc = "Docker konteyneri '$(Get-ContainerName $c)'"
        if ($proj) { $desc += " (proje: $proj)" }
        return $desc
    }
    if ($ids.Count -gt 0) { return $null }
    try {
        $conn = Get-NetTCPConnection -State Listen -LocalPort $P -ErrorAction Stop | Select-Object -First 1
        if ($conn) {
            $proc = Get-Process -Id $conn.OwningProcess -ErrorAction SilentlyContinue
            if ($proc) { return "'$($proc.ProcessName)' programı (PID $($conn.OwningProcess))" }
            return "PID $($conn.OwningProcess)"
        }
    } catch { }
    return $null
}

function Assert-PortsFree([hashtable]$Wanted) {
    $own = @(Get-OwnHostPorts)
    $problems = @()
    foreach ($label in $Wanted.Keys) {
        $p = [int]$Wanted[$label]
        if ($own -contains $p) { continue }
        $user = Find-PortUser $p
        if ($user) { $problems += "  $label kapısı ${p}: $user kullanıyor" }
    }
    if ($problems.Count -gt 0) {
        Say ''
        Say 'Bu kapılar dolu:' 'Red'
        foreach ($line in $problems) { Say $line 'Red' }
        if ($Name -eq $DefaultName -and (@(Get-ProjectContainers $LegacyPrefix).Count -gt 0)) {
            Say ''
            Say 'Eski kurulum (collabboard-*) hâlâ çalışıyor olabilir. Geçiş adımları:' 'Yellow'
            Say '  docs/GELISTIRME-ORTAMI.md  →  "Eski kurulumdan geçiş"' 'Yellow'
        }
        Fail "Başka bir kapıyla dene, ör.: .\scripts\dev.ps1 up$(Get-NameArg) -Port 8090"
    }
}

# Aynı veri birimini kullanan BAŞKA bir konteyner çalışıyorsa durdur: iki
# Postgres aynı veri klasörüne yazarsa veritabanı bozulur.
function Assert-VolumeNotShared([string]$Vol) {
    $ids = @(& docker ps -q --filter "volume=$Vol")
    foreach ($c in @(Get-Inspect $ids)) {
        $proj = Get-Label $c 'com.docker.compose.project'
        if ($proj -ne $Name) {
            Say ''
            Say "'$Vol' veri birimini şu an '$(Get-ContainerName $c)' konteyneri kullanıyor (proje: $proj)." 'Red'
            Say 'İki veritabanı aynı veriyi aynı anda açarsa veri bozulur; bu yüzden başlatılmadı.' 'Red'
            if ($proj -eq $LegacyPrefix) {
                Say ''
                Say 'Eski kurulumu kapat (veri SİLİNMEZ), sonra tekrar dene:' 'Yellow'
                Say "  docker compose -p $LegacyPrefix down" 'Yellow'
                Say '  Ayrıntı: docs/GELISTIRME-ORTAMI.md → "Eski kurulumdan geçiş"' 'Yellow'
            }
            Fail 'Önce o konteyneri durdur.'
        }
    }
}

function Wait-AppHealthy([int]$P, [int]$TimeoutSec = 240) {
    $url = "http://localhost:$P/actuator/health"
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    Write-Host -NoNewline 'Uygulama açılıyor '
    while ((Get-Date) -lt $deadline) {
        try {
            # Accept: actuator'ın kendi içerik türünü PowerShell 5.1 metin saymaz,
            # gövdeyi bayt dizisi olarak verir; düz JSON isteyip yine de çözüyoruz.
            $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 3 -Headers @{ Accept = 'application/json' }
            $body = $r.Content
            if ($body -is [byte[]]) { $body = [System.Text.Encoding]::UTF8.GetString($body) }
            if ($r.StatusCode -eq 200 -and $body -match '"UP"') {
                Write-Host ' hazır.' -ForegroundColor Green
                return $true
            }
        } catch { }
        $app = @(Get-Inspect (& docker ps -a -q --filter "label=com.docker.compose.project=$Name" --filter 'label=com.docker.compose.service=app'))
        if ($app.Count -gt 0 -and ($app[0].State.Status -in @('exited', 'dead', 'restarting'))) {
            Write-Host ''
            Say 'Uygulama açılırken durdu. Son satırlar:' 'Red'
            Invoke-Compose @('logs', '--tail', '40', 'app') | Out-Host
            return $false
        }
        Write-Host -NoNewline '.'
        Start-Sleep -Seconds 2
    }
    Write-Host ''
    return $false
}

# ── Özetler ────────────────────────────────────────────────────────
function Show-Summary([string]$MailMode, [bool]$Ready) {
    $n = Get-NameArg
    Say ''
    if ($Ready) { Say "Sturnio hazır — kopya: $Name" 'Green' } else { Say "Sturnio başlatıldı ama henüz hazır değil — kopya: $Name" 'Yellow' }
    Say "  Uygulama         http://localhost:$Port"
    if ($MailMode -eq 'gercek') {
        Say '  Posta            GERÇEK gönderim (.env ayarları). Postalar gerçek adreslere gider.' 'Yellow'
        Say "  Mailpit          http://localhost:$MailPort  (bu modda boş kalır)"
    } else {
        Say "  Posta (Mailpit)  http://localhost:$MailPort  — kayıt ve doğrulama postaları burada"
    }
    Say "  Veritabanı       localhost:$DbPort  (DBeaver; ayrıntı: .\scripts\dev.ps1 db$n)"
    if ($UsesLegacyData) {
        Say "  Veri             $DbVolume  (eski kurulumdaki verin)"
    } else {
        Say "  Veri             $DbVolume"
    }
    Say ''
    Say "  Günlükler        .\scripts\dev.ps1 logs$n"
    Say "  Durdurmak        .\scripts\dev.ps1 down$n     (veri kalır)"
    Say "  Tamamen kaldır   .\scripts\dev.ps1 clean$n"
}

function Show-AllCopies {
    $ids = & docker ps -a -q --filter 'label=com.sturnio.instance'
    $all = @(Get-Inspect $ids)
    Say ''
    Say 'Bu bilgisayardaki Sturnio kopyaları:' 'Cyan'
    if ($all.Count -eq 0) {
        Say '  (hiç yok)'
    } else {
        $groups = $all | Group-Object { Get-Label $_ 'com.sturnio.instance' } | Sort-Object Name
        foreach ($g in $groups) {
            $running = @($g.Group | Where-Object { $_.State.Status -eq 'running' }).Count
            $appPort = Get-Label $g.Group[0] 'com.sturnio.app-port'
            Say ('  {0,-22} {1}/{2} çalışıyor   http://localhost:{3}' -f $g.Name, $running, $g.Count, $appPort)
        }
    }
    $legacy = @(Get-ProjectContainers $LegacyPrefix)
    if ($legacy.Count -gt 0) {
        $names = ($legacy | ForEach-Object { Get-ContainerName $_ }) -join ', '
        Say ''
        Say "Eski kurulum hâlâ duruyor: $names" 'Yellow'
        Say '  Geçiş: docs/GELISTIRME-ORTAMI.md → "Eski kurulumdan geçiş"' 'Yellow'
    }
}

function Assert-CopyExists {
    if ($script:Existing.Count -eq 0) {
        Fail "'$Name' adlı bir kopya yok. Başlatmak için: .\scripts\dev.ps1 up$(Get-NameArg)"
    }
}

# ── Komutlar ───────────────────────────────────────────────────────
$saved = @{}
foreach ($k in $StackEnv.Keys) {
    $saved[$k] = [Environment]::GetEnvironmentVariable($k, 'Process')
    [Environment]::SetEnvironmentVariable($k, $StackEnv[$k], 'Process')
}
try {
    switch ($Command) {

        'up' {
            $wanted = @{ 'Veritabanı' = $DbPort; 'Mailpit' = $MailPort }
            if ($InfraOnly) {
                $wanted['Redis'] = $RedisPort
                $wanted['Mailpit SMTP'] = $SmtpPort
            } else {
                $wanted['Uygulama'] = $Port
            }
            Assert-PortsFree $wanted
            Assert-VolumeNotShared $DbVolume
            Assert-VolumeNotShared $RedisVolume
            if ($UsesLegacyData -and -not ($allVolumes -contains $RedisVolume)) {
                & docker volume create $RedisVolume | Out-Null
            }

            if ($InfraOnly) {
                if (Get-ServiceContainer 'app') { Invoke-Compose @('stop', 'app') }
                Say "Altyapı başlatılıyor (kopya: $Name)..." 'Cyan'
                Invoke-Compose @('up', '-d', '--wait', 'postgres', 'redis', 'mailpit') -WithHostJava
                if ($LASTEXITCODE -ne 0) { Fail 'Altyapı başlatılamadı; yukarıdaki mesaja bak.' }
                $profiles = 'dev,mailpit'
                if ($RealMail) { $profiles = 'dev' }
                Say ''
                Say "Altyapı hazır — kopya: $Name" 'Green'
                Say "  Veritabanı  localhost:$DbPort    Redis  127.0.0.1:$RedisPort"
                Say "  Mailpit     http://localhost:$MailPort  (SMTP 127.0.0.1:$SmtpPort)"
                Say ''
                Say 'Şimdi Java''yı bu klasörde, aynı PowerShell penceresinde çalıştır:' 'Cyan'
                $envLine = "  `$env:DB_PORT=`"$DbPort`"; `$env:REDIS_PORT=`"$RedisPort`"; `$env:MAILPIT_SMTP_PORT=`"$SmtpPort`""
                if ($Port -ne $BaseAppPort) {
                    $envLine += "; `$env:PORT=`"$Port`"; `$env:APP_BASE_URL=`"http://localhost:$Port`"; `$env:CORS_ALLOWED_ORIGINS=`"http://localhost:$Port`""
                }
                Say $envLine
                Say "  .\mvnw.cmd spring-boot:run `"-Dspring-boot.run.profiles=$profiles`""
                Say ''
                Say "Uygulama: http://localhost:$Port   Bitince: Ctrl+C, sonra .\scripts\dev.ps1 down$(Get-NameArg)"
                break
            }

            Say "Sturnio derleniyor ve başlatılıyor (kopya: $Name)..." 'Cyan'
            Say 'İlk sefer birkaç dakika sürebilir; sonrakiler yalnızca değişeni derler.'
            Invoke-Compose @('up', '-d', '--build', '--remove-orphans')
            if ($LASTEXITCODE -ne 0) { Fail 'Başlatılamadı; yukarıdaki mesaja bak.' }

            $ok = Wait-AppHealthy $Port
            $script:Existing = @(Get-ProjectContainers $Name)
            $mode = 'mailpit'
            $appC = Get-ServiceContainer 'app'
            if ($appC) {
                $m = Get-Label $appC 'com.sturnio.mail-mode'
                if ($m) { $mode = $m }
            }
            Show-Summary $mode ([bool]$ok)
            if (-not $ok) {
                Say ''
                Say "Uygulama henüz 'hazır' demedi. Ne olduğunu görmek için: .\scripts\dev.ps1 logs$(Get-NameArg)" 'Yellow'
                exit 1
            }
        }

        'down' {
            Assert-CopyExists
            Invoke-Compose @('down', '--remove-orphans')
            if ($LASTEXITCODE -ne 0) { Fail 'Durdurulamadı; yukarıdaki mesaja bak.' }
            Say ''
            Say "'$Name' durduruldu. Verin duruyor; tekrar açmak için: .\scripts\dev.ps1 up$(Get-NameArg)" 'Green'
        }

        'restart' {
            Assert-CopyExists
            Invoke-Compose @('restart')
            if ($LASTEXITCODE -ne 0) { Fail 'Yeniden başlatılamadı.' }
            $appC = Get-ServiceContainer 'app'
            if ($appC) { $null = Wait-AppHealthy $Port }
            Say "'$Name' yeniden başladı: http://localhost:$Port" 'Green'
        }

        'logs' {
            Assert-CopyExists
            $logArgs = @('logs', '--tail', '200')
            if (-not $NoFollow) {
                $logArgs += '-f'
                Say 'Çıkmak için Ctrl+C (yığın çalışmaya devam eder).' 'DarkGray'
            }
            if ($Service -ne 'all') { $logArgs += $Service }
            Invoke-Compose $logArgs
        }

        'status' {
            Say "Kopya: $Name" 'Cyan'
            if ($script:Existing.Count -eq 0) {
                Say "  Çalışmıyor. Başlatmak için: .\scripts\dev.ps1 up$(Get-NameArg)"
            } else {
                foreach ($c in ($script:Existing | Sort-Object { Get-ContainerName $_ })) {
                    Say ('  {0,-28} {1}' -f (Get-ContainerName $c), (Get-StateText $c))
                }
                Say ''
                Say "  Uygulama   http://localhost:$Port"
                Say "  Mailpit    http://localhost:$MailPort"
                Say "  Postgres   localhost:$DbPort   (veri: $DbVolume)"
            }
            Show-AllCopies
        }

        'clean' {
            $toRemove = @()
            if ($Volumes) {
                $toRemove = @(@($DbVolume, $RedisVolume) | Where-Object { $allVolumes -contains $_ })
            }
            # 'down' sonrası konteyner kalmaz ama imaj ve ağ kalabilir.
            $leftovers = @(& docker image ls -q "$Name-app") + @(& docker network ls -q --filter "label=com.docker.compose.project=$Name")
            $hasStack = ($script:Existing.Count -gt 0) -or (@($leftovers | Where-Object { $_ }).Count -gt 0)
            if (-not $hasStack -and $toRemove.Count -eq 0) {
                Say "'$Name' adlı kopyadan kaldırılacak bir şey yok." 'Green'
                break
            }
            if ($Volumes -and $toRemove.Count -gt 0) {
                Say ''
                Say 'Şu veri birimleri KALICI olarak silinecek:' 'Yellow'
                foreach ($v in $toRemove) { Say "  $v" 'Yellow' }
                if ($Name -eq $DefaultName) {
                    Say ''
                    Say 'Bu ANA kopyanın verisi: panoların, kullanıcıların, her şey. Geri alınamaz.' 'Red'
                    if ($Force) { Fail '-Force ana kopyada kullanılamaz; onayı elle ver.' }
                }
                if (-not $Force) {
                    $answer = $null
                    try {
                        $answer = Read-Host "Onaylamak için kopyanın adını yaz ($Name)"
                    } catch {
                        Say 'Onay sorulamadı (bu pencere soru soramıyor).' 'Yellow'
                        if ($Name -ne $DefaultName) { Say 'Ajan kopyasıysa -Force ile onaysız silinebilir.' 'Yellow' }
                    }
                    if ($answer -ne $Name) {
                        Say 'Vazgeçildi; hiçbir şey silinmedi.' 'Green'
                        break
                    }
                }
            }
            if ($hasStack) {
                # --rmi local: yalnızca bu kopya için derlenen uygulama imajı silinir;
                # postgres/redis/mailpit gibi indirilen imajlar kalır.
                Invoke-Compose @('down', '--remove-orphans', '--rmi', 'local')
                if ($LASTEXITCODE -ne 0) { Fail 'Kaldırılamadı; yukarıdaki mesaja bak.' }
            }
            foreach ($v in $toRemove) {
                & docker volume rm $v | Out-Null
                if ($LASTEXITCODE -ne 0) { Fail "'$v' silinemedi." }
                Say "  silindi: $v"
            }
            Say ''
            if ($Volumes) {
                Say "'$Name' verisiyle birlikte kaldırıldı." 'Green'
            } else {
                Say "'$Name' kaldırıldı. Veri birimi duruyor: $DbVolume (silmek için: -Volumes)" 'Green'
            }
        }

        'mail' {
            Assert-CopyExists
            $mp = Get-ServiceContainer 'mailpit'
            if (-not $mp -or $mp.State.Status -ne 'running') {
                Fail "Mailpit çalışmıyor. Başlatmak için: .\scripts\dev.ps1 up$(Get-NameArg)"
            }
            $url = "http://localhost:$MailPort"
            $appC = Get-ServiceContainer 'app'
            if ($appC -and (Get-Label $appC 'com.sturnio.mail-mode') -eq 'gercek') {
                Say 'Not: bu kopya GERÇEK posta modunda; postalar Mailpit''e değil gerçek adreslere gidiyor.' 'Yellow'
            }
            Say "Mailpit: $url"
            if (-not $NoBrowser) { Start-Process $url }
        }

        'db' {
            Assert-CopyExists
            $pg = Get-ServiceContainer 'postgres'
            if (-not $pg) { Fail 'Bu kopyada Postgres konteyneri yok.' }
            $dbName = Get-ContainerEnv $pg 'POSTGRES_DB'
            $dbUser = Get-ContainerEnv $pg 'POSTGRES_USER'
            $dbPass = Get-ContainerEnv $pg 'POSTGRES_PASSWORD'
            $passText = '.env dosyandaki DB_PASSWORD'
            if ($dbPass -eq 'postgres') { $passText = 'postgres' }
            Say "Veritabanı — kopya: $Name ($(Get-StateText $pg))" 'Cyan'
            Say '  Sunucu (Host)   localhost'
            Say "  Kapı (Port)     $DbPort"
            Say "  Veritabanı      $dbName"
            Say "  Kullanıcı       $dbUser"
            Say "  Parola          $passText"
            Say "  JDBC adresi     jdbc:postgresql://localhost:$DbPort/$dbName"
            Say "  Veri birimi     $DbVolume"
            Say ''
            Say 'Komut satırından bağlanmak için:'
            Say "  docker exec -it $(Get-ContainerName $pg) psql -U $dbUser -d $dbName"
        }

        'config' {
            $json = (Invoke-Compose @('config', '--format', 'json')) -join "`n"
            if ($LASTEXITCODE -ne 0 -or -not $json) { Fail 'docker-compose.yml okunamadı; yukarıdaki mesaja bak.' }
            # Çıktıda .env'den gelen değerler de var; yalnızca sır olmayan alanlar yazdırılır.
            $cfg = ConvertFrom-Json -InputObject $json
            Say "Kopya (Compose projesi): $($cfg.name)" 'Cyan'
            foreach ($svc in @('app', 'postgres', 'redis', 'mailpit')) {
                $s = $cfg.services.$svc
                $pub = @($s.ports | Where-Object { $_ } | ForEach-Object { "$($_.published)->$($_.target)" }) -join ', '
                if (-not $pub) { $pub = '(dışarı açık değil)' }
                Say ('  {0,-9} kapılar: {1}' -f $svc, $pub)
            }
            $labels = $cfg.services.app.labels
            Say '  Etiketler:'
            foreach ($prop in ($labels.PSObject.Properties | Where-Object { $_.Name -like 'com.sturnio.*' } | Sort-Object Name)) {
                Say "    $($prop.Name)=$($prop.Value)"
            }
            Say '  Veri birimleri:'
            foreach ($vk in @('postgres_data', 'redis_data')) {
                $v = $cfg.volumes.$vk
                $ext = 'hayır'
                if ($v.external) { $ext = 'evet (down -v silemez)' }
                Say "    $($v.name)   dışarıdan: $ext"
            }
        }
    }
} finally {
    foreach ($k in $saved.Keys) { [Environment]::SetEnvironmentVariable($k, $saved[$k], 'Process') }
}
