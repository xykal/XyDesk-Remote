#Requires -Version 5.1
# XyDesk Remote - RDP VM setup (dipanggil workflow rdp-vm.yml, shell pwsh)
#
# Prasyarat VM:
#   - Berjalan sebagai self-hosted runner GitHub dengan label 'xydesk-win'
#   - PowerShell 7 (pwsh) — dipakai workflow, dan untuk enkripsi kredensial
#   - Tailscale terpasang (atau akan di-install otomatis oleh skrip ini)
#   - Auth key Tailscale: dari input app (XYDESK_TSKEY) ATAU fallback env
#     runner XYDESK_TAILSCALE_AUTH_KEY (pre-auth key, multi-use disarankan)
#
# Input env dari workflow (semua opsional):
#   XYDESK_RDP_USER  nama user RDP   (default xydesk)
#   XYDESK_RDP_PASS  password RDP    (kosong = auto-generate kuat tiap setup)
#   XYDESK_TSKEY     Tailscale auth key (kosong = pakai env runner)
#
# Hasil: user RDP aktif, RDP on, VM join tailnet.
# Kredensial DIIKRIP (RSA-OAEP) dengan setup/pubkey.pem yang dibuat app saat
# "Create & Setup" menjadi C:\ProgramData\xydesk\rdp-credentials.enc —
# hanya app yang bisa mendekripsi. Aman walau repo PUBLIC.
$ErrorActionPreference = 'Stop'

# 0) Wajib pubkey dari app — tanpa ini kredensial bakal kebaca siapa pun
if (-not (Test-Path 'setup\pubkey.pem')) {
    throw 'setup\pubkey.pem tidak ada. Jalankan "Create & Setup" dari app XyDesk Remote (bukan setup manual) supaya kredensial terenkripsi end-to-end.'
}

# 0b) Mask nilai sensitif biar tidak bocor di log Actions
if ($env:XYDESK_RDP_PASS) { Write-Host "::add-mask::$($env:XYDESK_RDP_PASS)" }
if ($env:XYDESK_TSKEY)    { Write-Host "::add-mask::$($env:XYDESK_TSKEY)" }

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
$authKey = $env:XYDESK_TSKEY
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

# 3) User RDP — nama & password dari input app, fallback aman.
# Password kosong = diacak kuat tiap setup (user lama: password SELALU di-reset,
# aman untuk re-run; password lama tidak pernah dipakai ulang)
$user = 'xydesk'
if ($env:XYDESK_RDP_USER -and $env:XYDESK_RDP_USER -match '^[A-Za-z][A-Za-z0-9._-]{0,19}$') {
    $user = [string]$env:XYDESK_RDP_USER
}
$reserved = 'Administrator', 'Guest', 'DefaultAccount', 'WDAGUtilityAccount', 'krbtgt'
if ($reserved -contains $user) {
    throw "Nama user '$user' dipakai Windows — pilih nama lain."
}
$pass = [string]$env:XYDESK_RDP_PASS
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
New-Item -ItemType Directory -Force 'C:\ProgramData\xydesk' | Out-Null
$tmpJson = Join-Path $env:TEMP 'xydesk-creds.json'
$encFile = 'C:\ProgramData\xydesk\rdp-credentials.enc'
[IO.File]::WriteAllText($tmpJson, ([pscustomobject]@{
    host     = $dnsName
    port     = 3389
    user     = $user
    password = $pass
} | ConvertTo-Json))
$rsa = [System.Security.Cryptography.RSA]::Create()
$rsa.ImportFromPem((Get-Content 'setup\pubkey.pem' -Raw))
$enc = $rsa.Encrypt([IO.File]::ReadAllBytes($tmpJson),
                    [System.Security.Cryptography.RSAEncryptionPadding]::OaepSHA1)
[IO.File]::WriteAllBytes($encFile, $enc)
Remove-Item $tmpJson -Force

Write-Host "[xydesk] ready: $dnsName (user: $user) — kredensial terenkripsi di rdp-credentials.enc"
