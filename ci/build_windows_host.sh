#!/usr/bin/env bash
# Build native Windows x64 Host Agent as a SINGLE self-contained XyDeskRemoteHost.exe
# that embeds xydesk_host_core.dll, xydesk_quic.dll, XyDeskVDD.inf, and XyDeskAudioMic.ps1
# directly inside the executable's read-only data section.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SRC_DIR="${ROOT_DIR}/host-agent/src"
INC_DIR="${ROOT_DIR}/host-agent/include"
OUT_DIR="${1:-${ROOT_DIR}/dist/windows-host/XyDeskRemoteHost}"
BUNDLE_TMP="$(mktemp -d)"
trap 'rm -rf "${BUNDLE_TMP}"' EXIT

CXX="${CXX:-x86_64-w64-mingw32-g++}"
STRIP="${STRIP:-x86_64-w64-mingw32-strip}"

mkdir -p "${OUT_DIR}"

COMMON_FLAGS=(
  -O2
  -std=c++17
  -Wall
  -Wextra
  -Wno-unused-parameter
  -D_WIN32_WINNT=0x0A00
  -DWINVER=0x0A00
  -DWIN32_LEAN_AND_MEAN
  -I"${INC_DIR}"
  -static -static-libgcc -static-libstdc++
)

echo "[1/3] Compiling embedded native modules (xydesk_host_core.dll & xydesk_quic.dll)..."
"${CXX}" "${COMMON_FLAGS[@]}" -shared \
  -DXYDESK_QUIC_EXPORTS \
  "${SRC_DIR}/xydesk_quic.cpp" \
  -o "${BUNDLE_TMP}/xydesk_quic.dll" \
  -lws2_32 -lole32 -lmmdevapi -lavrt -lksuser -lgdi32 -luser32
"${STRIP}" --strip-unneeded "${BUNDLE_TMP}/xydesk_quic.dll" || true

"${CXX}" "${COMMON_FLAGS[@]}" -shared \
  -DXYDESK_HOST_CORE_EXPORTS \
  "${SRC_DIR}/xydesk_host_core.cpp" \
  -o "${BUNDLE_TMP}/xydesk_host_core.dll" \
  -lws2_32 -liphlpapi -ldxgi -ladvapi32 -luser32
"${STRIP}" --strip-unneeded "${BUNDLE_TMP}/xydesk_host_core.dll" || true

cat > "${BUNDLE_TMP}/XyDeskVDD.inf" <<'EOF'
[Version]
Signature="$Windows NT$"
Class=Display
ClassGuid={4d36e968-e325-11ce-bfc1-08002be10318}
Provider=%ManufacturerName%
CatalogFile=XyDeskVDD.cat
DriverVer=10/20/2026,2.4.0.0
PnpLockdown=1

[Manufacturer]
%ManufacturerName%=Standard,NTamd64

[Standard.NTamd64]
%DeviceDesc%=XyDeskVDD_Install, Root\XyDeskVirtualDisplay120Hz

[Strings]
ManufacturerName="XyVerse Technology Global"
DeviceDesc="XyDesk Virtual Display 120Hz Adapter (IDD)"
EOF

cat > "${BUNDLE_TMP}/XyDeskAudioMic.ps1" <<'EOF'
$ErrorActionPreference = 'SilentlyContinue'
$ts = 'HKLM:\SYSTEM\CurrentControlSet\Control\Terminal Server'
$rdp = 'HKLM:\SYSTEM\CurrentControlSet\Control\Terminal Server\WinStations\RDP-Tcp'
Set-ItemProperty -Path $ts -Name 'fDenyTSConnections' -Value 0 -Type DWord -Force
Set-ItemProperty -Path $ts -Name 'fSingleSessionPerUser' -Value 0 -Type DWord -Force
Set-ItemProperty -Path $ts -Name 'fDisableAudioCapture' -Value 0 -Type DWord -Force
Set-ItemProperty -Path $rdp -Name 'fDisableAudio' -Value 0 -Type DWord -Force
Set-ItemProperty -Path $rdp -Name 'fDisableAudioCapture' -Value 0 -Type DWord -Force
Set-ItemProperty -Path $rdp -Name 'fDisableCam' -Value 0 -Type DWord -Force
foreach ($cap in @('microphone','webcam')) {
  $p = "HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\CapabilityAccessManager\ConsentStore\$cap"
  New-Item -Path $p -Force | Out-Null
  Set-ItemProperty -Path $p -Name 'Value' -Value 'Allow' -Type String -Force
}
Start-Service Audiosrv -ErrorAction SilentlyContinue
Start-Service AudioEndpointBuilder -ErrorAction SilentlyContinue
Start-Service TermService -ErrorAction SilentlyContinue
Start-Service UmRdpService -ErrorAction SilentlyContinue
EOF

echo "[2/3] Generating embedded_bundle.h for single-EXE self-extraction..."
python3 - "${BUNDLE_TMP}" <<'PY'
import pathlib, sys
d = pathlib.Path(sys.argv[1])
items = [
    ("kEmbeddedHostCoreDll", d / "xydesk_host_core.dll"),
    ("kEmbeddedQuicDll",     d / "xydesk_quic.dll"),
    ("kEmbeddedVddInf",      d / "XyDeskVDD.inf"),
    ("kEmbeddedAudioPs1",    d / "XyDeskAudioMic.ps1"),
]
lines = ["#pragma once", "#include <cstddef>"]
for sym, path in items:
    raw = path.read_bytes()
    hex_bytes = ",".join(f"0x{b:02x}" for b in raw)
    lines.append(f"static const unsigned char {sym}[] = {{{hex_bytes}}};")
    lines.append(f"static const size_t {sym}Len = {len(raw)}u;")
(d / "embedded_bundle.h").write_text("\n".join(lines) + "\n", encoding="utf-8")
PY

echo "[3/3] Compiling single-file XyDeskRemoteHost.exe (all DLLs, libraries & drivers bundled)..."
"${CXX}" "${COMMON_FLAGS[@]}" \
  -DXYDESK_STATIC \
  -DXYDESK_HAS_EMBEDDED_BUNDLE \
  -I"${BUNDLE_TMP}" \
  -mwindows \
  "${SRC_DIR}/xydesk_quic.cpp" \
  "${SRC_DIR}/xydesk_host_core.cpp" \
  "${SRC_DIR}/host_readiness.cpp" \
  "${SRC_DIR}/main_host.cpp" \
  -o "${OUT_DIR}/XyDeskRemoteHost.exe" \
  -lws2_32 -liphlpapi -ldxgi -ladvapi32 -luser32 -lgdi32 -lgdiplus -lshell32 -lwtsapi32 -lwinhttp -lole32 -lmmdevapi -lavrt -lksuser -luuid -lpropsys

"${STRIP}" --strip-unneeded "${OUT_DIR}/XyDeskRemoteHost.exe" || true

echo "Single-EXE Windows Host Agent built successfully in ${OUT_DIR}:"
ls -lh "${OUT_DIR}/XyDeskRemoteHost.exe"
