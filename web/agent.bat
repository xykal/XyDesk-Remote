@echo off
setlocal
set "SCRIPT=%TEMP%\xydesk-agent.ps1"
echo Downloading the XyDesk Remote host helper for review only...
powershell.exe -NoProfile -Command "$ErrorActionPreference='Stop'; $file=Join-Path $env:TEMP 'xydesk-agent.ps1'; Invoke-WebRequest -UseBasicParsing 'https://www.xydeskremote.biz.id/agent.ps1' -OutFile $file; Get-FileHash $file -Algorithm SHA256; Start-Process notepad.exe -ArgumentList $file"
if errorlevel 1 (
  echo Download failed. No script was run.
  exit /b 1
)
echo.
echo The script was downloaded and opened for inspection; it was NOT run.
echo Review all commands before deciding whether to execute it as Administrator.
pause
