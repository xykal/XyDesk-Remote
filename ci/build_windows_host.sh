#!/usr/bin/env bash
# Run only when a native host build is explicitly authorized. Produces ONE EXE.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${1:?Usage: build_windows_host.sh OUTPUT_DIRECTORY}"
mkdir -p "$OUT"
"${CXX:-x86_64-w64-mingw32-g++}" -std=c++17 -O2 -mwindows -DXYDESK_STATIC \
  "$ROOT/host-agent/src/main_host.cpp" \
  "$ROOT/host-agent/src/xydesk_quic.cpp" \
  "$ROOT/host-agent/src/xydesk_host_core.cpp" \
  -I"$ROOT/host-agent/include" -o "$OUT/XyDeskRemoteHost.exe" \
  -static -static-libgcc -static-libstdc++ \
  -lws2_32 -lole32 -ldxgi -ladvapi32 -liphlpapi -luser32 -lgdi32 -lshell32 -ldwmapi -lwtsapi32 -lwinhttp
