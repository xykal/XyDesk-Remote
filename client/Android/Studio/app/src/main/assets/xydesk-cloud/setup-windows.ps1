#Requires -Version 5.1
# XyDesk Remote - RDP VM setup (dipanggil workflow rdp-vm.yml)
#
# Prasyarat VM:
#   - Berjalan sebagai self-hosted runner GitHub dengan label 'xydesk-win'
#   - Tailscale terpasang (atau akan di-install otomatis oleh skrip ini)
#   - Environment variable XYDESK_TAILSCALE_AUTH_KEY ter-set di runner
#     (pre-auth key dari Tailscale admin — multi-use disarankan)
#
# Hasil: user 'xydesk' + password acak, RDP on, VM join tailnet,
# kredensial ditulis ke C:\ProgramData\xydesk\rdp-credentials.json
$ErrorActionPreference = 'Stop'

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
$authKey = $env:XYDESK_TAILSCALE_AUTH_KEY
if (-not $authKey) {
    throw 'XYDESK_TAILSCALE_AUTH_KEY belum diset di environment self-hosted runner'
}
tailscale up --authkey=$authKey --hostname="$env:COMPUTERNAME" --reset 2>$null
Start-Sleep -Seconds 5
$dnsName = (tailscale status --json | ConvertFrom-Json).Self.DNSName
if (-not $dnsName) {
    throw 'Tailscale DNS name tidak tersedia (cek status tailscale di VM)'
}

# 3) User RDP dengan password acak
$user = 'xydesk'
$pass = -join ((48..57) + (97..122) + (65..90) | Get-Random -Count 18 | ForEach-Object { [char]$_ })
net user $user $pass /add 2>$null | Out-Null
net localgroup administrators $user /add | Out-Null

# 4) Publish kredensial (dibaca app XyDesk via artifact workflow)
New-Item -ItemType Directory -Force 'C:\ProgramData\xydesk' | Out-Null
[pscustomobject]@{
    host     = $dnsName
    port     = 3389
    user     = $user
    password = $pass
} | ConvertTo-Json | Set-Content 'C:\ProgramData\xydesk\rdp-credentials.json' -Encoding UTF8

Write-Host "[xydesk] ready: $dnsName (user: $user)"
