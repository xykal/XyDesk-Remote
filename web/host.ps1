$ErrorActionPreference = "SilentlyContinue"
Set-ItemProperty -Path "HKLM:\System\CurrentControlSet\Control\Terminal Server" -Name "fDenyTSConnections" -Value 0
Set-ItemProperty -Path "HKLM:\System\CurrentControlSet\Control\Terminal Server" -Name "fSingleSessionPerUser" -Value 0
$tsPolicy = "HKLM:\SOFTWARE\Policies\Microsoft\Windows NT\Terminal Services"
if (!(Test-Path $tsPolicy)) { New-Item -Path $tsPolicy -Force | Out-Null }
New-ItemProperty -Path $tsPolicy -Name "fDenyTSConnections" -PropertyType DWord -Value 0 -Force | Out-Null
New-ItemProperty -Path $tsPolicy -Name "fSingleSessionPerUser" -PropertyType DWord -Value 0 -Force | Out-Null
New-ItemProperty -Path $tsPolicy -Name "bEnumerateHWBeforeSW" -PropertyType DWord -Value 1 -Force | Out-Null
New-ItemProperty -Path $tsPolicy -Name "AVC444ModePreferred" -PropertyType DWord -Value 1 -Force | Out-Null
New-ItemProperty -Path $tsPolicy -Name "AVCHardwareEncodePreferred" -PropertyType DWord -Value 1 -Force | Out-Null
New-ItemProperty -Path $tsPolicy -Name "VGAdapter" -PropertyType DWord -Value 1 -Force | Out-Null
New-ItemProperty -Path $tsPolicy -Name "fAllowFontAntiAlias" -PropertyType DWord -Value 1 -Force | Out-Null
New-ItemProperty -Path $tsPolicy -Name "fAllowDesktopComposition" -PropertyType DWord -Value 1 -Force | Out-Null
New-ItemProperty -Path $tsPolicy -Name "SelectTransport" -PropertyType DWord -Value 0 -Force | Out-Null
New-ItemProperty -Path "HKLM:\System\CurrentControlSet\Control\Terminal Server\WinStations\RDP-Tcp" -Name "fDisableAudio" -Value 0 -Force | Out-Null
New-ItemProperty -Path "HKLM:\System\CurrentControlSet\Control\Terminal Server\WinStations\RDP-Tcp" -Name "fDisableAudioCapture" -Value 0 -Force | Out-Null
Enable-NetFirewallRule -DisplayGroup "Remote Desktop"
netsh advfirewall firewall add rule name="XyDesk Remote RDP TCP" dir=in action=allow protocol=TCP localport=3389 | Out-Null
netsh advfirewall firewall add rule name="XyDesk Remote RDP UDP" dir=in action=allow protocol=UDP localport=3389 | Out-Null
netsh advfirewall firewall add rule name="XyDesk Remote QUIC UDP" dir=in action=allow protocol=UDP localport=4433 | Out-Null
Start-Service TermService
Start-Service Audiosrv
Write-Host "XyDesk Host v0.5.35 Ready (GPU AVC444 + ClearType + QUIC UDP 4433 Audio/Mic Bridge)"
