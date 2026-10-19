# XyDesk recovery diagnostics. READ ONLY: no installation, network traffic,
# registry writes, firewall changes, certificate changes or input injection.
# Run in Windows PowerShell 5.1 inside the Windows/RDP session being tested.
# If policy blocks this file, do NOT disable the policy; report the error instead.
[CmdletBinding()]
param([string]$OutputPath = (Join-Path $env:TEMP 'XyDesk-Readiness.json'))
$ErrorActionPreference = 'Stop'
$notes = New-Object System.Collections.Generic.List[string]
function Read-RegValue([string]$Path, [string]$Name) {
    try { return (Get-ItemProperty -LiteralPath $Path -Name $Name -ErrorAction Stop).$Name }
    catch { return $null }
}
function Read-ServiceStatus([string]$Name) {
    try { return [string](Get-Service -Name $Name -ErrorAction Stop).Status }
    catch { return 'not_available_or_access_denied' }
}
$osInfo = $null
try { $osInfo = Get-CimInstance Win32_OperatingSystem }
catch { $notes.Add('OS CIM query unavailable; no elevation was attempted.') }
$driverInfo = @()
try {
    # Only virtual-controller-related driver metadata. No machine IDs/USB serials.
    $driverInfo = @(Get-CimInstance Win32_PnPSignedDriver | Where-Object {
        $_.DeviceName -match 'ViGEm|vJoy|Virtual HID|Virtual Gamepad' -or
        $_.DriverProviderName -match 'Nefarius|LizardByte'
    } | ForEach-Object {
        [ordered]@{ name=$_.DeviceName; provider=$_.DriverProviderName;
            version=$_.DriverVersion; signed=[bool]$_.IsSigned }
    })
} catch { $notes.Add('Controller driver inventory unavailable; do not interpret this as no driver.') }
$inputInjectorTypePresent = $null
try {
    $typeName = 'Windows.UI.Input.Preview.Injection.InputInjector, Windows.Foundation.UniversalApiContract, ContentType=WindowsRuntime'
    $inputInjectorTypePresent = [bool][Type]::GetType($typeName, $false)
} catch {
    $notes.Add('WinRT metadata projection unavailable in this shell; capability was not tested.')
}
$audioEndpointCount = $null
try {
    $audioEndpointCount = @(Get-CimInstance Win32_PnPEntity -Filter "PNPClass='AudioEndpoint'").Count
} catch { $notes.Add('Audio endpoint inventory unavailable.') }
$rdpPolicy = 'HKLM:\SOFTWARE\Policies\Microsoft\Windows NT\Terminal Services'
$rdpTcp = 'HKLM:\SYSTEM\CurrentControlSet\Control\Terminal Server\WinStations\RDP-Tcp'
$result = [ordered]@{
    schema_version = 1
    observed_utc = [DateTime]::UtcNow.ToString('o')
    read_only = $true
    windows = [ordered]@{
        caption = $osInfo.Caption
        version = $osInfo.Version
        build = $osInfo.BuildNumber
        architecture = $osInfo.OSArchitecture
        process_64bit = [Environment]::Is64BitProcess
        interactive = [Environment]::UserInteractive
        process_session_id = (Get-Process -Id $PID).SessionId
    }
    rdp = [ordered]@{
        service = (Read-ServiceStatus 'TermService')
        connections_denied = (Read-RegValue 'HKLM:\SYSTEM\CurrentControlSet\Control\Terminal Server' 'fDenyTSConnections')
        policy_audio_capture_disabled = (Read-RegValue $rdpPolicy 'fDisableAudioCapture')
        connection_audio_capture_disabled = (Read-RegValue $rdpTcp 'fDisableAudioCapture')
        policy_audio_playback_disabled = (Read-RegValue $rdpPolicy 'fDisableAudio')
        connection_audio_playback_disabled = (Read-RegValue $rdpTcp 'fDisableAudio')
    }
    audio = [ordered]@{
        audio_service = (Read-ServiceStatus 'Audiosrv')
        endpoint_builder = (Read-ServiceStatus 'AudioEndpointBuilder')
        endpoint_count = $audioEndpointCount
        actual_mic_recording_tested = $false
    }
    controller = [ordered]@{
        installed_driver_metadata = $driverInfo
        winrt_input_injector_type_present = $inputInjectorTypePresent
        gamepad_creation_attempted = $false
        actual_device_verified = $false
        note = 'Type availability does not grant inputInjectionBrokered or prove a gamepad exists. No capability/policy/driver was changed.'
    }
    developer_mode_registry_value = (Read-RegValue 'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\AppModelUnlock' 'AllowDevelopmentWithoutDevLicense')
    runtime_acceptance = [ordered]@{
        rdp_frame = 'not_tested'
        pc_stream_frame = 'not_tested'
        microphone = 'not_tested'
        genuine_controller = 'not_tested'
        secure_direct_pairing = 'not_tested'
    }
    notes = @($notes.ToArray())
}
$result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $OutputPath -Encoding UTF8
Write-Output "Read-only readiness report written: $OutputPath"
Write-Output 'No software/driver was installed and no Windows policy was changed.'
Write-Output 'This inventory is NOT a successful mic/frame/controller runtime test.'
