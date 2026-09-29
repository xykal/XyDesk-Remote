@echo off
title XyDesk Host Setup - XyVerse Technology Global
echo ================================================================
echo   XyDesk Remote - Windows PC Host ^& GPU Gaming Setup
echo   Powered by XyVerse Technology Global
echo ================================================================
echo.
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo Meminta hak Administrator Windows...
    powershell -Command "Start-Process cmd -ArgumentList '/c \"\"%~f0\"\"' -Verb RunAs"
    exit /b
)

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference = 'SilentlyContinue'; ^
   Write-Host '[1/4] Mengaktifkan layanan Remote Desktop (TCP & UDP 3389)...'; ^
   Set-ItemProperty -Path 'HKLM:\System\CurrentControlSet\Control\Terminal Server' -Name 'fDenyTSConnections' -Value 0; ^
   Enable-NetFirewallRule -DisplayGroup 'Remote Desktop'; ^
   Write-Host '[2/4] Mengaktifkan Akselerasi GPU Hardware (AVC444 + 60 FPS)...'; ^
   $tsPolicy = 'HKLM:\SOFTWARE\Policies\Microsoft\Windows NT\Terminal Services'; ^
   if (!(Test-Path $tsPolicy)) { New-Item -Path $tsPolicy -Force | Out-Null }; ^
   New-ItemProperty -Path $tsPolicy -Name 'bEnumerateHWBeforeSW' -PropertyType DWord -Value 1 -Force | Out-Null; ^
   New-ItemProperty -Path $tsPolicy -Name 'AVC444ModePreferred' -PropertyType DWord -Value 1 -Force | Out-Null; ^
   New-ItemProperty -Path $tsPolicy -Name 'AVCHardwareEncodePreferred' -PropertyType DWord -Value 1 -Force | Out-Null; ^
   New-ItemProperty -Path $tsPolicy -Name 'SelectTransport' -PropertyType DWord -Value 0 -Force | Out-Null; ^
   New-ItemProperty -Path 'HKLM:\SYSTEM\CurrentControlSet\Control\Terminal Server\WinStations' -Name 'DWMFRAMEINTERVAL' -PropertyType DWord -Value 15 -Force | Out-Null; ^
   Write-Host '[3/4] Menghitung ID PC XyDesk Anda...'; ^
   $ips = Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' }; ^
   Write-Host ''; ^
   Write-Host '================================================================'; ^
   Write-Host '  XYDESK HOST AKTIF (GPU AVC444 60 FPS + UDP 3389 READY)'; ^
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
echo.
pause
