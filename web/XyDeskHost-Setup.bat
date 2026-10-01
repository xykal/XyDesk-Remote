@echo off
title XyDesk Remote Host v0.5.23 - XyVerse Technology Global
echo ================================================================
echo   XyDesk Remote v0.5.23 - Windows PC Host ^& Native QUIC Setup
echo   Powered by XyVerse Technology Global
echo ================================================================
echo.
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo Meminta hak Administrator Windows...
    powershell -Command "Start-Process cmd -ArgumentList '/c \"\"%~f0\"\"' -Verb RunAs"
    exit /b
)

cd /d "%~dp0"

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference = 'SilentlyContinue'; ^
   Write-Host '[1/4] Mengaktifkan layanan Remote Desktop (TCP/UDP 3389 + QUIC UDP 4433 + Multi-Session)...'; ^
   Set-ItemProperty -Path 'HKLM:\System\CurrentControlSet\Control\Terminal Server' -Name 'fDenyTSConnections' -Value 0; ^
   Set-ItemProperty -Path 'HKLM:\System\CurrentControlSet\Control\Terminal Server' -Name 'fSingleSessionPerUser' -Value 0; ^
   Enable-NetFirewallRule -DisplayGroup 'Remote Desktop'; ^
   netsh advfirewall firewall add rule name='XyDesk Remote RDP TCP' dir=in action=allow protocol=TCP localport=3389 | Out-Null; ^
   netsh advfirewall firewall add rule name='XyDesk Remote RDP UDP' dir=in action=allow protocol=UDP localport=3389 | Out-Null; ^
   netsh advfirewall firewall add rule name='XyDesk Remote QUIC UDP' dir=in action=allow protocol=UDP localport=4433 | Out-Null; ^
   Write-Host '[2/4] Mengaktifkan Akselerasi GPU Hardware (AVC 4:4:4 + ClearType Font + 60 FPS)...'; ^
   $tsPolicy = 'HKLM:\SOFTWARE\Policies\Microsoft\Windows NT\Terminal Services'; ^
   if (!(Test-Path $tsPolicy)) { New-Item -Path $tsPolicy -Force | Out-Null }; ^
   New-ItemProperty -Path $tsPolicy -Name 'fDenyTSConnections' -PropertyType DWord -Value 0 -Force | Out-Null; ^
   New-ItemProperty -Path $tsPolicy -Name 'fSingleSessionPerUser' -PropertyType DWord -Value 0 -Force | Out-Null; ^
   New-ItemProperty -Path $tsPolicy -Name 'bEnumerateHWBeforeSW' -PropertyType DWord -Value 1 -Force | Out-Null; ^
   New-ItemProperty -Path $tsPolicy -Name 'AVC444ModePreferred' -PropertyType DWord -Value 1 -Force | Out-Null; ^
   New-ItemProperty -Path $tsPolicy -Name 'AVCHardwareEncodePreferred' -PropertyType DWord -Value 1 -Force | Out-Null; ^
   New-ItemProperty -Path $tsPolicy -Name 'VGAdapter' -PropertyType DWord -Value 1 -Force | Out-Null; ^
   New-ItemProperty -Path $tsPolicy -Name 'fAllowFontAntiAlias' -PropertyType DWord -Value 1 -Force | Out-Null; ^
   New-ItemProperty -Path $tsPolicy -Name 'fAllowDesktopComposition' -PropertyType DWord -Value 1 -Force | Out-Null; ^
   New-ItemProperty -Path $tsPolicy -Name 'SelectTransport' -PropertyType DWord -Value 0 -Force | Out-Null; ^
   New-ItemProperty -Path $tsPolicy -Name 'fDisableAudio' -PropertyType DWord -Value 0 -Force | Out-Null; ^
   New-ItemProperty -Path $tsPolicy -Name 'fDisableAudioCapture' -PropertyType DWord -Value 0 -Force | Out-Null; ^
   New-ItemProperty -Path $tsPolicy -Name 'fDisableCam' -PropertyType DWord -Value 0 -Force | Out-Null; ^
   $rdpTcp = 'HKLM:\SYSTEM\CurrentControlSet\Control\Terminal Server\WinStations\RDP-Tcp'; ^
   if (Test-Path $rdpTcp) { ^
     New-ItemProperty -Path $rdpTcp -Name 'fDisableAudio' -PropertyType DWord -Value 0 -Force | Out-Null; ^
     New-ItemProperty -Path $rdpTcp -Name 'fDisableAudioCapture' -PropertyType DWord -Value 0 -Force | Out-Null; ^
     New-ItemProperty -Path $rdpTcp -Name 'fDisableCam' -PropertyType DWord -Value 0 -Force | Out-Null; ^
     New-ItemProperty -Path $rdpTcp -Name 'MaxMonitors' -PropertyType DWord -Value 16 -Force | Out-Null; ^
   }; ^
   Start-Service -Name 'AudioEndpointBuilder' -ErrorAction SilentlyContinue; ^
   Start-Service -Name 'Audiosrv' -ErrorAction SilentlyContinue; ^
   New-ItemProperty -Path 'HKLM:\SYSTEM\CurrentControlSet\Control\Terminal Server\WinStations' -Name 'DWMFRAMEINTERVAL' -PropertyType DWord -Value 15 -Force | Out-Null; ^
   $gpus = (Get-CimInstance Win32_VideoController | Select-Object -ExpandProperty Name) -join ', '; ^
   Write-Host ('[3/4] GPU Terdeteksi: ' + $gpus); ^
   Write-Host '[4/4] Menghitung ID PC XyDesk Anda...'; ^
   $ips = Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' }; ^
   Write-Host ''; ^
   Write-Host '================================================================'; ^
   Write-Host '  XYDESK HOST AKTIF (GPU AVC444 + CLEARTYPE + QUIC UDP 4433)'; ^
   Write-Host '================================================================'; ^
   foreach ($ipObj in $ips) { ^
     $parts = $ipObj.IPAddress.Split('.'); ^
     $num = ([uint64]$parts[0] -shl 24) -bor ([uint64]$parts[1] -shl 16) -bor ([uint64]$parts[2] -shl 8) -bor ([uint64]$parts[3]); ^
     $d = $num.ToString('0000000000'); ^
     $pcId = $d.Substring(0,3) + '-' + $d.Substring(3,3) + '-' + $d.Substring(6,4); ^
     Write-Host ('  ID PC (' + $ipObj.InterfaceAlias + '): ' + $pcId + '   [IP: ' + $ipObj.IPAddress + ']'); ^
   }; ^
   Write-Host ('  Username Windows Anda : ' + $env:USERNAME); ^
   Write-Host '  Password              : Gunakan Password Login Windows Anda'; ^
   Write-Host '================================================================'; ^
   Write-Host ''"

if exist "%~dp0XyDeskRemoteHost.exe" (
    echo Menjalankan XyDesk Native C++ Host Agent di latar belakang ^(UDP QUIC + WASAPI Audio/Mic Bridge :4433^)...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Stop-Process -Name 'XyDeskRemoteHost' -Force -ErrorAction SilentlyContinue; Start-Process -FilePath '%~dp0XyDeskRemoteHost.exe' -WindowStyle Minimized"
    echo [READY] XyDeskRemoteHost.exe berjalan aktif di latar belakang.
    timeout /t 4
) else (
    echo.
    pause
)
