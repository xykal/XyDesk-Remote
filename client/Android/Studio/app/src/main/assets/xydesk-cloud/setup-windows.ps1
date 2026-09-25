#Requires -Version 5.1
# XyDesk Remote - RDP VM setup (dipanggil workflow rdp-vm.yml, shell pwsh)
#
# Dua fase (dipilih app via env XYDESK_PHASE):
#   prepare : generate pasangan kunci RSA host (sekali per mesin) di
#             C:\ProgramData\xydesk\hostkey.pem (privat, tidak pernah ke repo)
#             + host-pubkey.pem (publik, di-upload artifact, dipakai app buat
#             enkripsi rahasia).
#   setup   : dekripsi setup/secrets.enc (user/pass/tskey) dgn kunci privat
#             host -> RDP on, join Tailscale, buat user RDP, lalu kredensial
#             di-enskripsi balik ke pubkey.pem milik app (end-to-end).
#
# Prasyarat VM:
#   - Self-hosted runner GitHub label 'xydesk-win', PowerShell 7 (pwsh)
#   - Tailscale terpasang (di-install otomatis bila perlu); auth key dari
#     app (via secrets.enc) atau fallback env runner XYDESK_TAILSCALE_AUTH_KEY
#
# Hasil: user RDP aktif, RDP on, VM join tailnet, kredensial ter-enskripsi di
# C:\ProgramData\xydesk\rdp-credentials.enc (hanya app yang bisa dekripsi).
$ErrorActionPreference = 'Stop'

$dir = 'C:\ProgramData\xydesk'
$privPath = Join-Path $dir 'hostkey.pem'
$pubPath  = Join-Path $dir 'host-pubkey.pem'

function Ensure-HostKey {
    New-Item -ItemType Directory -Force $dir | Out-Null
    if (-not (Test-Path $privPath)) {
        Write-Host '[xydesk] generate kunci host (sekali per mesin)...'
        $g = [System.Security.Cryptography.RSA]::Create(2048)
        [IO.File]::WriteAllText($privPath, $g.ExportPkcs8PrivateKeyPem())
        [IO.File]::WriteAllText($pubPath, $g.ExportSubjectPublicKeyInfoPem())
        $g.Dispose()
    } elseif (-not (Test-Path $pubPath)) {
        $g = [System.Security.Cryptography.RSA]::Create()
        $g.ImportFromPem((Get-Content $privPath -Raw))
        [IO.File]::WriteAllText($pubPath, $g.ExportSubjectPublicKeyInfoPem())
        $g.Dispose()
    }
}

$phase = [string]$env:XYDESK_PHASE
if (-not $phase) { $phase = 'setup' }

if ($phase -eq 'prepare') {
    Ensure-HostKey
    Write-Host '[xydesk] host public key siap (host-pubkey.pem)'
    return
}

# ====================== fase SETUP ======================

# 0) Wajib: pubkey app (untuk enkripsi balik) + secrets.enc (rahasia user)
if (-not (Test-Path 'setup\pubkey.pem')) {
    throw 'setup\pubkey.pem tidak ada. Jalankan "Create & Setup" dari app XyDesk Remote (bukan setup manual).'
}
if (-not (Test-Path 'setup\secrets.enc')) {
    throw 'setup\secrets.enc tidak ada. Jalankan "Create & Setup" dari app XyDesk Remote.'
}
Ensure-HostKey

# 0a) Dekripsi rahasia dari app (tiap field di-RSA-OAEP-kan terpisah ke pubkey host)
$rsaHost = [System.Security.Cryptography.RSA]::Create()
$rsaHost.ImportFromPem((Get-Content $privPath -Raw))
$pad = [System.Security.Cryptography.RSAEncryptionPadding]::OaepSHA1
$blobs = Get-Content 'setup\secrets.enc' -Raw | ConvertFrom-Json
$user    = [Text.Encoding]::UTF8.GetString($rsaHost.Decrypt([Convert]::FromBase64String($blobs.u), $pad))
$pass    = [Text.Encoding]::UTF8.GetString($rsaHost.Decrypt([Convert]::FromBase64String($blobs.p), $pad))
$tskey   = [Text.Encoding]::UTF8.GetString($rsaHost.Decrypt([Convert]::FromBase64String($blobs.t), $pad))

# 0b) Mask nilai sensitif biar tidak bocor di log Actions
if ($pass)  { Write-Host "::add-mask::$pass" }
if ($tskey) { Write-Host "::add-mask::$tskey" }

# 1) RDP on + firewall
Set-ItemProperty -Path 'HKLM:\System\CurrentControlSet\Control\Terminal Server' `
    -Name 'fDenyTSConnections' -Value 0
Enable-NetFirewallRule -DisplayGroup 'Remote Desktop' -ErrorAction SilentlyContinue

# 2) Tailscale — install bila perlu, join tailnet
if (-not (Get-Command tailscale -ErrorAction SilentlyContinue)) {
    Write-Host '[xydesk] installing tailscale...'
    Invoke-WebRequest 'https://downloads.tailscale.com/windows/tailscale-setup-latest.exe' `
        -OutFile "$env:TEMP\ts-setup.exe"
    Start-Process "$env:TEMP\ts-setup.exe" -ArgumentList '/quiet','/install' -Wait
    Start-Sleep -Seconds 10
}
$authKey = $tskey
if (-not $authKey) { $authKey = $env:XYDESK_TAILSCALE_AUTH_KEY }
if (-not $authKey) {
    throw 'Tailscale auth key belum ada — isi "Tailscale auth key" di app saat Create & Setup, atau set env XYDESK_TAILSCALE_AUTH_KEY di runner.'
}
tailscale up --authkey=$authKey --hostname="$env:COMPUTERNAME" --reset 2>$null
Start-Sleep -Seconds 5
$dnsName = (tailscale status --json | ConvertFrom-Json).Self.DNSName
if (-not $dnsName) {
    throw 'Tailscale DNS name tidak tersedia (cek status tailscale di VM)'
}

# 3) User RDP — nama & password dari app (ter-enskripsi), fallback aman.
# Password kosong = diacak kuat tiap setup (user lama: password SELALU di-reset,
# aman untuk re-run; password lama tidak pernah dipakai ulang)
if ($user -and $user -match '^[A-Za-z][A-Za-z0-9._-]{0,19}$') {
    # ok
} else {
    $user = 'xydesk'
}
$reserved = 'Administrator', 'Guest', 'DefaultAccount', 'WDAGUtilityAccount', 'krbtgt'
if ($reserved -contains $user) {
    throw "Nama user '$user' dipakai Windows — pilih nama lain."
}
if ($pass) {
    if ($pass -notmatch '^[A-Za-z0-9!@#$%^&*._-]{12,64}$') {
        throw 'Password RDP tidak valid — min 12 karakter, boleh huruf/angka/!@#$%^&*._- (tanpa spasi/kutip).'
    }
} else {
    $pass = -join ((48..57) + (97..122) + (65..90) | Get-Random -Count 18 | ForEach-Object { [char]$_ })
}
net user $user $pass /add 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    net user $user $pass | Out-Null
}
net localgroup administrators $user /add 2>$null | Out-Null

# 4) Enkripsi kredensial dengan pubkey app (RSA-OAEP SHA-1 + MGF1 SHA-1,
#    kompatibel decrypt di Android) — plaintext TIDAK pernah menyentuh disk
$tmpJson = Join-Path $env:TEMP 'xydesk-creds.json'
$encFile = Join-Path $dir 'rdp-credentials.enc'
[IO.File]::WriteAllText($tmpJson, ([pscustomobject]@{
    host     = $dnsName
    port     = 3389
    user     = $user
    password = $pass
} | ConvertTo-Json))
$rsaApp = [System.Security.Cryptography.RSA]::Create()
$rsaApp.ImportFromPem((Get-Content 'setup\pubkey.pem' -Raw))
$enc = $rsaApp.Encrypt([IO.File]::ReadAllBytes($tmpJson), $pad)
[IO.File]::WriteAllBytes($encFile, $enc)
Remove-Item $tmpJson -Force

Write-Host "[xydesk] ready: $dnsName (user: $user) — kredensial terenkripsi di rdp-credentials.enc"
