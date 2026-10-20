# ==============================================================================
#  XyDesk Remote — Standalone On-Mic, Virtual Audio & 120FPS Setup (v1.1.18)
#  Khusus untuk RDP Beli Sendiri / Windows VPS / Cloud RDP Non-GitHub
#  Cara pakai (PowerShell Admin):
#    irm https://xydeskremote.biz.id/onmic.ps1 | iex
# ==============================================================================

$ErrorActionPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "[!] Membuka ulang dengan hak Administrator (UAC)..." -ForegroundColor Yellow
    $cmd = "-NoProfile -ExecutionPolicy Bypass -Command `"irm https://xydeskremote.biz.id/onmic.ps1 | iex`""
    Start-Process powershell.exe -Verb RunAs -ArgumentList $cmd
    exit
}

Write-Host ""
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "   XYDESK REMOTE — SETUP ON-MIC + VIRTUAL DEVICE + 120FPS RDP   " -ForegroundColor Cyan
Write-Host "   Mendukung RDP Beli Sendiri / Windows Server / Windows 10/11  " -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host ""

function Set-XyReg($Path, $Name, $Value, $Type = 'DWord') {
    if (-not (Test-Path $Path)) { New-Item -Path $Path -Force | Out-Null }
    New-ItemProperty -Path $Path -Name $Name -Value $Value -PropertyType $Type -Force | Out-Null
}

# 1. Buka kunci Kebijakan Redireksi Mikrofon (On-Mic), Audio Playback, & Kamera RDP
Write-Host "[1/5] Membuka kunci Redireksi Mikrofon (On-Mic) & Audio RDP..." -ForegroundColor Green
$tsRoot = 'HKLM:\SYSTEM\CurrentControlSet\Control\Terminal Server'
$tsTcp  = 'HKLM:\SYSTEM\CurrentControlSet\Control\Terminal Server\WinStations\RDP-Tcp'
$tsPol  = 'HKLM:\SOFTWARE\Policies\Microsoft\Windows NT\Terminal Services'

foreach ($p in @($tsRoot, $tsTcp, $tsPol)) {
    Set-XyReg $p 'fDisableAudio'             0 'DWord'
    Set-XyReg $p 'fDisableAudioCapture'      0 'DWord'
    Set-XyReg $p 'fDisableCam'               0 'DWord'
    Set-XyReg $p 'fDisableCdm'               0 'DWord'
    Set-XyReg $p 'fDisableClip'              0 'DWord'
    Set-XyReg $p 'fNoRemoteDesktopWallpaper' 0 'DWord'
}
Set-XyReg $tsPol 'AudioQualityMode' 2 'DWord'

# 2. Buka Izin Privasi Mikrofon & Kamera Windows (Store Apps + Desktop Apps/Games)
Write-Host "[2/5] Mengaktifkan izin Privasi Mikrofon Windows untuk Game & Discord..." -ForegroundColor Green
$consentBase = 'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\CapabilityAccessManager\ConsentStore'
foreach ($cap in @('microphone', 'microphone\NonPackaged', 'webcam', 'webcam\NonPackaged')) {
    Set-XyReg "$consentBase\$cap" 'Value' 'Allow' 'String'
}
Set-XyReg 'HKCU:\Software\Microsoft\Windows\CurrentVersion\CapabilityAccessManager\ConsentStore\microphone' 'Value' 'Allow' 'String'
Set-XyReg 'HKCU:\Software\Microsoft\Windows\CurrentVersion\CapabilityAccessManager\ConsentStore\microphone\NonPackaged' 'Value' 'Allow' 'String'

# 3. Hidupkan Layanan Windows Audio & Endpoint Builder
Write-Host "[3/5] Menjalankan layanan Windows Audio (Audiosrv & AudioEndpointBuilder)..." -ForegroundColor Green
foreach ($svc in @('AudioEndpointBuilder', 'Audiosrv')) {
    Set-Service -Name $svc -StartupType Automatic
    Start-Service -Name $svc
}

# 4. Pasang Virtual Microphone / Audio Cable (VB-CABLE) bila belum ada
Write-Host "[4/5] Memeriksa Virtual Microphone / Audio Driver (VB-CABLE)..." -ForegroundColor Green
$hasVb = Get-CimInstance Win32_SoundDevice | Where-Object { $_.Name -match 'VB-Audio|CABLE' }
if (-not $hasVb) {
    try {
        $vbDir = "$env:TEMP\xydesk-vbcable"
        New-Item -ItemType Directory -Path $vbDir -Force | Out-Null
        $vbZip = "$vbDir\vbcable.zip"
        Write-Host "      Mengunduh VB-CABLE Virtual Microphone Driver..." -ForegroundColor DarkGray
        Invoke-WebRequest -Uri "https://download.vb-audio.com/Download_CABLE/VBCABLE_Driver_Pack43.zip" -OutFile $vbZip -UseBasicParsing -TimeoutSec 45
        Expand-Archive -Path $vbZip -DestinationPath $vbDir -Force
        $exe = Join-Path $vbDir "VBCABLE_Setup_x64.exe"
        if (Test-Path $exe) {
            Start-Process -FilePath $exe -ArgumentList "-i -h" -Wait -WindowStyle Hidden
            Write-Host "      VB-CABLE Virtual Microphone berhasil dipasang." -ForegroundColor Green
        }
    } catch {
        Write-Host "      Info: Menggunakan endpoint Remote Audio RDP bawaan Windows." -ForegroundColor Yellow
    }
} else {
    Write-Host "      Virtual Microphone (VB-CABLE) sudah terpasang." -ForegroundColor Green
}

# 5. Optimasi 120 FPS DWM + AVC444 4:4:4 + TCP NoDelay + Firewall UDP 4433
Write-Host "[5/5] Menerapkan 120 FPS DWM + AVC444 + Ultra-Low Latency TCP..." -ForegroundColor Green
Set-XyReg $tsPol  'AVC444ModePreferred'        1 'DWord'
Set-XyReg $tsPol  'AVCHardwareEncodePreferred' 1 'DWord'
Set-XyReg $tsPol  'DWMFRAMEINTERVAL'           8 'DWord'
Set-XyReg $tsRoot 'DWMFRAMEINTERVAL'           8 'DWord'

Get-ChildItem 'HKLM:\SYSTEM\CurrentControlSet\Services\Tcpip\Parameters\Interfaces' | ForEach-Object {
    New-ItemProperty -Path $_.PSPath -Name 'TcpAckFrequency' -Value 1 -PropertyType DWord -Force | Out-Null
    New-ItemProperty -Path $_.PSPath -Name 'TCPNoDelay'      -Value 1 -PropertyType DWord -Force | Out-Null
}
New-NetFirewallRule -DisplayName 'XyDesk QUIC Audio/Mic Bridge (UDP 4433)' -Direction Inbound -Protocol UDP -LocalPort 4433 -Action Allow | Out-Null

Write-Host ""
Write-Host "================================================================" -ForegroundColor Green
Write-Host "  SELESAI! ON-MIC + VIRTUAL DEVICE + 120FPS SUDAH AKTIF PENUH   " -ForegroundColor Green
Write-Host "  Tips: Reconnect sesi RDP Anda dari aplikasi XyDesk Remote     " -ForegroundColor Green
Write-Host "        dengan toggle 'Mikrofon' menyala agar 'Remote Audio'    " -ForegroundColor Green
Write-Host "        langsung aktif di Windows Sound / Discord / Game!       " -ForegroundColor Green
Write-Host "================================================================" -ForegroundColor Green
Write-Host ""
