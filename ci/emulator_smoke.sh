#!/usr/bin/env bash
# XyDesk Remote — UI smoke test di Android emulator.
#
# Dipanggil oleh .github/workflows/emulator-tests.yml di dalam langkah
# reactivecircus/android-emulator-runner, yaitu setelah emulator boot.
# Bisa juga dijalankan lokal:
#   APK_PATH=/path/app-x86_64-debug.apk bash ci/emulator_smoke.sh /tmp/out
#
# SENGAJA TIDAK menguji koneksi RDP ke Windows: itu perlu host asli dan ada di
# docs/acceptance/TEMPLATE.md. Fokus di sini: aplikasi tidak crash dan UI tetap
# terbaca dalam kondisi perangkat yang berubah-ubah.

set -euo pipefail

PKG="${SMOKE_PACKAGE:-id.xydesk.remote}"
APK="${APK_PATH:?APK_PATH harus diisi (path ke APK debug x86_64/universal)}"
OUT="${1:-emulator-smoke}"
BOOT_TIMEOUT="${BOOT_TIMEOUT:-180}"

mkdir -p "${OUT}"
fail() { echo "::error::${*}" >&2; exit 1; }
note() { echo "::notice::${*}"; }
step() { echo; echo "===== ${*} ====="; }

GRACE_PID=""
cleanup() {
  if [ -n "${GRACE_PID}" ]; then kill "${GRACE_PID}" 2>/dev/null || true; fi
  # Kembalikan setting emulator supaya tidak mengganggu langkah berikutnya.
  adb shell settings put system accelerometer_rotation 1 >/dev/null 2>&1 || true
  adb shell settings put system user_rotation 0 >/dev/null 2>&1 || true
  adb shell settings put system font_scale 1.0 >/dev/null 2>&1 || true
  adb shell wm density reset >/dev/null 2>&1 || true
  adb shell svc wifi enable >/dev/null 2>&1 || true
  adb shell svc data enable >/dev/null 2>&1 || true
  cp "${OUT}/logcat.txt" "${OUT}/logcat-final.txt" 2>/dev/null || true
}
trap cleanup EXIT

wait_for() {
  local desc="$1" tries="$2" sleep_s="$3"; shift 3
  local i
  for ((i = 1; i <= tries; i++)); do
    if "$@" >/dev/null 2>&1; then return 0; fi
    sleep "${sleep_s}"
  done
  fail "${desc} tidak tercapai setelah $((tries * sleep_s)) detik"
}

# `pidof` tidak selalu ada di image lama; sediakan fallback ke `ps`.
app_alive() {
  local out
  out="$(adb shell pidof "${PKG}" 2>/dev/null | tr -d '\r ')"
  [ -n "${out}" ] && return 0
  adb shell ps 2>/dev/null | grep -q "${PKG}"
}

assert_no_crash() {
  local label="$1" logfile="$2"
  if grep -Eq "FATAL EXCEPTION|ANR in ${PKG}|Input dispatching timed out|signal [0-9]+ \(SIG(SEGV|ABRT|BUS|ILL)\)|tombstone written" "${logfile}"; then
    echo "--- potongan logcat (${label}) ---"
    grep -E -m 20 -A 8 "FATAL EXCEPTION|ANR in ${PKG}|SIG(SEGV|ABRT)|tombstone" "${logfile}" || true
    fail "Crash/ANR terdeteksi pada tahap: ${label}"
  fi
}

snapshot() {
  local name="$1"
  adb exec-out screencap -p > "${OUT}/${name}.png" || note "screencap ${name} gagal (lanjut)"
  adb shell uiautomator dump /sdcard/window_dump.xml >/dev/null 2>&1 \
    && adb pull /sdcard/window_dump.xml "${OUT}/${name}.xml" >/dev/null 2>&1 \
    || note "uiautomator dump ${name} gagal (lanjut)"
  adb logcat -d -v threadtime > "${OUT}/logcat.txt" 2>/dev/null || true
}

# ---------------------------------------------------------------- boot
step "0. Tunggu emulator siap"
adb wait-for-device
wait_for "boot_completed" "$((BOOT_TIMEOUT / 3))" 3 \
  bash -c '[ "$(adb shell getprop sys.boot_completed | tr -d "\r")" = "1" ]'
wait_for "package service" 30 2 adb shell cmd package list packages
adb logcat -c || true
note "Emulator siap: $(adb shell getprop ro.build.version.release | tr -d '\r') / API $(adb shell getprop ro.build.version.sdk | tr -d '\r')"

# ---------------------------------------------------------------- install
step "1. Install APK debug"
[ -f "${APK}" ] || fail "APK tidak ditemukan: ${APK}"
adb install -r "${APK}" | tee "${OUT}/install.log"
adb shell pm list packages | grep -qx "package:${PKG}" \
  || fail "Paket ${PKG} tidak terpasang"

# ---------------------------------------------------------------- cold start
step "2. Cold start"
adb shell am force-stop "${PKG}"
adb shell monkey -p "${PKG}" -c android.intent.category.LAUNCHER 1 \
  > "${OUT}/launch.log" 2>&1
wait_for "proses aplikasi hidup" 20 1 app_alive
sleep 4
snapshot "01-cold-start"
assert_no_crash "cold start" "${OUT}/logcat.txt"

# UI harus benar-benar ter-render: ada node view dan bukan dialog crash.
[ -s "${OUT}/01-cold-start.xml" ] || fail "UI hierarchy kosong — aplikasi tidak merender apa pun"
grep -qi "${PKG}\|android.widget.FrameLayout" "${OUT}/01-cold-start.xml" \
  || fail "UI hierarchy tidak berisi view yang dikenal"
if grep -qi "isn.t responding\|has stopped\|Unfortunately," "${OUT}/01-cold-start.xml"; then
  fail "Dialog crash/ANR muncul di layar awal"
fi

# ---------------------------------------------------------------- rotasi
step "3. Rotasi ke landscape"
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
sleep 3
wait_for "proses hidup setelah rotasi" 15 1 app_alive
snapshot "02-landscape"
assert_no_crash "rotasi" "${OUT}/logcat.txt"
adb shell settings put system user_rotation 0
sleep 1

# ---------------------------------------------------------------- aksesibilitas dasar
step "4. Density & font besar"
adb shell wm density 480
adb shell settings put system font_scale 1.3
sleep 3
wait_for "proses hidup saat font besar" 15 1 app_alive
snapshot "03-large-font"
assert_no_crash "font besar" "${OUT}/logcat.txt"
if grep -qi "isn.t responding\|has stopped" "${OUT}/03-large-font.xml" 2>/dev/null; then
  fail "Dialog crash muncul saat font/density besar"
fi
adb shell settings put system font_scale 1.0
adb shell wm density reset
sleep 1

# ---------------------------------------------------------------- process death
step "5. Process death → launch ulang"
adb shell input keyevent KEYCODE_HOME
sleep 1
adb shell am kill "${PKG}" || true
sleep 2
adb shell monkey -p "${PKG}" -c android.intent.category.LAUNCHER 1 \
  >> "${OUT}/launch.log" 2>&1
wait_for "proses hidup setelah di-kill" 20 1 app_alive
sleep 3
snapshot "04-after-process-death"
assert_no_crash "process death" "${OUT}/logcat.txt"

# ---------------------------------------------------------------- jaringan
step "6. Jaringan dimatikan lalu dinyalakan"
adb root >/dev/null 2>&1 || true
adb shell svc wifi disable >/dev/null 2>&1 || note "svc wifi disable tidak tersedia (best effort)"
adb shell svc data disable >/dev/null 2>&1 || note "svc data disable tidak tersedia (best effort)"
sleep 4
adb shell monkey -p "${PKG}" -c android.intent.category.LAUNCHER 1 \
  >> "${OUT}/launch.log" 2>&1
sleep 3
wait_for "proses hidup tanpa jaringan" 15 1 app_alive
snapshot "05-offline"
assert_no_crash "offline" "${OUT}/logcat.txt"
adb shell svc wifi enable >/dev/null 2>&1 || true
adb shell svc data enable >/dev/null 2>&1 || true
sleep 2

# ---------------------------------------------------------------- final
step "7. Rekap"
adb logcat -d -v threadtime > "${OUT}/logcat.txt" 2>/dev/null || true
assert_no_crash "final" "${OUT}/logcat.txt"
{
  echo "package=${PKG}"
  echo "apk=${APK}"
  echo "api=$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
  echo "release=$(adb shell getprop ro.build.version.release | tr -d '\r')"
  echo "abi=$(adb shell getprop ro.product.cpu.abi | tr -d '\r')"
  echo "result=PASS"
} | tee "${OUT}/summary.txt"

note "Smoke test lulus. Artefak ada di ${OUT}/"
