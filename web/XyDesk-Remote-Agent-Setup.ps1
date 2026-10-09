# XyDesk Remote — Agent Host setup (Windows) — XyVerse Technology Global
#
# Skenario utama: VM/PC yang dikendalikan lewat RDP (mis. runner GitHub Actions)
# dan perlu audio dua arah ke HP TANPA bergantung pada kanal audio RDP.
#
# Pakai:
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\agent-setup.ps1
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\agent-setup.ps1 -VirtualAudio
#
#   -VirtualAudio : hentikan UmRdpService supaya VB-CABLE / XyDesk Virtual
#                   Microphone kelihatan di sesi RDP, lalu agent bisa:
#                     mic HP  -> CABLE Input  (jadi input mikrofon di PC)
#                     suara PC-> loopback     (dikirim ke HP)
#                   Konsekuensi: redirection audio/drive bawaan RDP mati.

param(
    [switch]$VirtualAudio,
    [string]$BaseUrl = "https://www.xydeskremote.biz.id",
    [string]$InstallDir = "C:\XyDesk-Remote-Host",
    [switch]$NoRun
)

$ErrorActionPreference = "SilentlyContinue"

function Say([string]$m) { Write-Host $m }
function Section([string]$m) { Write-Host ""; Write-Host "== $m" }

$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Say "Jalankan PowerShell sebagai Administrator, lalu ulangi."
    exit 1
}

Section "XyDesk Remote Host Agent"
Say "  Sumber unduhan : $BaseUrl"
Say "  Folder install : $InstallDir"

Section "[1/6] Unduh + ekstrak agent"
New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null
$zip = Join-Path $InstallDir "XyDesk-Remote-Host-Agent-win64.zip"
try {
    Invoke-WebRequest -UseBasicParsing "$BaseUrl/XyDesk-Remote-Host-Agent-win64.zip" -OutFile $zip
    Expand-Archive -Force -Path $zip -DestinationPath $InstallDir
    Say ("  exe   : " + (Join-Path $InstallDir "XyDeskRemoteHost.exe"))
    Say ("  quic  : " + (Join-Path $InstallDir "xydesk_quic.dll"))
    Say ("  core  : " + (Join-Path $InstallDir "xydesk_host_core.dll"))
} catch {
    Say "  Gagal mengunduh agent: $($_.Exception.Message)"
    exit 1
}

Section "[2/6] Firewall (UDP 4433 + RDP 3389)"
netsh advfirewall firewall add rule name="XyDesk Remote QUIC UDP" dir=in action=allow protocol=UDP localport=4433 | Out-Null
netsh advfirewall firewall add rule name="XyDesk Remote RDP TCP" dir=in action=allow protocol=TCP localport=3389 | Out-Null
netsh advfirewall firewall add rule name="XyDesk Remote RDP UDP" dir=in action=allow protocol=UDP localport=3389 | Out-Null
Say "  rule: XyDesk Remote QUIC UDP (4433), RDP TCP/UDP (3389)"

Section "[3/6] Kanal audio RDP (untuk fallback tanpa agent)"
$rdpTcp = "HKLM:\System\CurrentControlSet\Control\Terminal Server\WinStations\RDP-Tcp"
New-ItemProperty -Path $rdpTcp -Name "fDisableAudio" -PropertyType DWord -Value 0 -Force | Out-Null
New-ItemProperty -Path $rdpTcp -Name "fDisableAudioCapture" -PropertyType DWord -Value 0 -Force | Out-Null
Say "  fDisableAudio=0, fDisableAudioCapture=0"

Section "[4/6] Perangkat audio virtual"
if ($VirtualAudio) {
    Stop-Service UmRdpService -Force
    Restart-Service AudioEndpointBuilder -Force
    Restart-Service Audiosrv -Force
    Say "  UmRdpService dihentikan; audio/redirection RDP dialihkan ke agent."
    Say "  Catatan: redirection drive & audio bawaan RDP ikut mati sampai service dinyalakan lagi."
} else {
    Say "  Dilewati (jalankan dengan -VirtualAudio kalau VB-CABLE / XyDesk Virtual"
    Say "  Microphone tidak terlihat di sesi RDP ini)."
}

Section "[5/6] Endpoint audio yang terlihat sekarang"
$eps = Get-CimInstance Win32_PnPEntity -Filter "PNPClass='AudioEndpoint'" |
    Select-Object -ExpandProperty Name
if ($eps) { $eps | ForEach-Object { Say "  - $_" } } else { Say "  (tidak ada AudioEndpoint terbaca)" }

Section "[6/6] Jalankan agent"
if ($NoRun) {
    Say "  -NoRun dipakai; jalankan manual: $InstallDir\XyDeskRemoteHost.exe"
} else {
    Get-Process -Name "XyDeskRemoteHost" -ErrorAction SilentlyContinue | Stop-Process -Force
    Start-Process -FilePath (Join-Path $InstallDir "XyDeskRemoteHost.exe") -WorkingDirectory $InstallDir -WindowStyle Minimized
    Say "  XyDeskRemoteHost.exe jalan (window minimized). Restore untuk lihat log"
    Say "  baris [xydesk-quic/audio] = endpoint yang dipakai agent."
}

Write-Host ""
Write-Host "Checklist uji (HP <-> VM/PC):"
Write-Host "  1. APK XyDesk Remote -> Edit koneksi -> Audio = 'Putar di komputer remote' (mode Remote)."
Write-Host "  2. Mikrofon = ON (izin mikrofon harus diberikan)."
Write-Host "  3. Log sesi HP harus memuat: AUDIO: host ok / audio:terkunci / mic:render."
Write-Host "  4. mmsys.cpl -> Recording: pilih 'CABLE Output' atau 'XyDesk Virtual Microphone'"
Write-Host "     sebagai default, supaya aplikasi di PC merekam suara dari HP."
Write-Host "  5. mmsys.cpl -> Playback: biarkan 'CABLE Input' menerima audio aplikasi"
Write-Host "     yang ingin didengar di HP (agent menangkap endpoint yang bersuara)."
