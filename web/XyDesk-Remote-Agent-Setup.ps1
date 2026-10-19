param(
    [switch]$VirtualAudio,
    [string]$BaseUrl,
    [string]$InstallDir,
    [switch]$LegacyZip,
    [switch]$NoRun
)
# Retired legacy setup entry point. No system changes or downloaded code execution.
$ErrorActionPreference = 'Stop'
Write-Warning 'Installer legacy ditarik karena mengubah kebijakan/privasi/firewall atau menjalankan setup tanpa verifikasi memadai.'
Write-Host 'Tidak ada konfigurasi Windows, password, layanan atau driver yang diubah.'
Write-Host 'Gunakan panel Kesiapan Mic & Game pada host baru setelah tersedia; transport aman dan routing mic masih perlu diuji.'
Write-Host 'Panduan: https://xydeskremote.biz.id/host.html'
exit 1
