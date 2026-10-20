@echo off
title XyDesk Remote - Setup On-Mic + Virtual Device + 120FPS (Untuk RDP Beli / VPS)
color 0B
echo.
echo ============================================================================
echo   XYDESK REMOTE - SETUP ON-MIC + VIRTUAL DEVICE + 120FPS (v1.1.18)
echo   Khusus untuk RDP Beli Sendiri / Windows Server / Windows 10 ^& 11
echo ============================================================================
echo.

net session >nul 2>&1
if %errorLevel% neq 0 (
    echo [!] Meminta hak Administrator ^(UAC^)...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
)

echo [*] Menjalankan konfigurasi On-Mic, Izin Privasi Mic, Virtual Audio, ^& 120FPS...
powershell -NoProfile -ExecutionPolicy Bypass -Command "& { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; try { irm https://xydeskremote.biz.id/onmic.ps1 | iex } catch { irm https://api.xydeskremote.biz.id/onmic.ps1 | iex } }"

echo.
echo [*] Selesai! Silakan reconnect sesi RDP Anda dari XyDesk Remote.
pause
