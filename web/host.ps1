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
New-ItemProperty -Path $tsPolicy -Name "fDisableAudio" -PropertyType DWord -Value 0 -Force | Out-Null
New-ItemProperty -Path $tsPolicy -Name "fDisableAudioCapture" -PropertyType DWord -Value 0 -Force | Out-Null
New-ItemProperty -Path $tsPolicy -Name "fDisableCam" -PropertyType DWord -Value 0 -Force | Out-Null
New-ItemProperty -Path $tsPolicy -Name "DWMFRAMEINTERVAL" -PropertyType DWord -Value 16 -Force | Out-Null
New-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Control\Terminal Server" -Name "fDisableAudio" -PropertyType DWord -Value 0 -Force | Out-Null
New-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Control\Terminal Server" -Name "fDisableAudioCapture" -PropertyType DWord -Value 0 -Force | Out-Null
New-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Control\Terminal Server\WinStations\RDP-Tcp" -Name "fDisableAudio" -PropertyType DWord -Value 0 -Force | Out-Null
New-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Control\Terminal Server\WinStations\RDP-Tcp" -Name "fDisableAudioCapture" -PropertyType DWord -Value 0 -Force | Out-Null
New-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Control\Terminal Server\WinStations" -Name "DWMFRAMEINTERVAL" -PropertyType DWord -Value 16 -Force | Out-Null
foreach ($c in @(
    "HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\CapabilityAccessManager\ConsentStore\microphone",
    "HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\CapabilityAccessManager\ConsentStore\microphone\NonPackaged",
    "HKCU:\Software\Microsoft\Windows\CurrentVersion\CapabilityAccessManager\ConsentStore\microphone",
    "HKCU:\Software\Microsoft\Windows\CurrentVersion\CapabilityAccessManager\ConsentStore\microphone\NonPackaged"
)) {
    if (-not (Test-Path $c)) { New-Item -Path $c -Force | Out-Null }
    Set-ItemProperty -Path $c -Name "Value" -Value "Allow" -Force | Out-Null
}
Enable-NetFirewallRule -DisplayGroup "Remote Desktop"
netsh advfirewall firewall add rule name="XyDesk Remote RDP TCP" dir=in action=allow protocol=TCP localport=3389 | Out-Null
netsh advfirewall firewall add rule name="XyDesk Remote RDP UDP" dir=in action=allow protocol=UDP localport=3389 | Out-Null
netsh advfirewall firewall add rule name="XyDesk Remote QUIC UDP" dir=in action=allow protocol=UDP localport=4433 | Out-Null
Set-Service -Name "AudioEndpointBuilder" -StartupType Automatic
Start-Service -Name "AudioEndpointBuilder"
Set-Service -Name "Audiosrv" -StartupType Automatic
Start-Service -Name "Audiosrv"
Start-Service TermService
Write-Host "XyDesk Host Ready (120Hz GPU AVC444 + ClearType + On-Mic AudioCapture + QUIC UDP 4433)"
