#!/usr/bin/env bash
# Pemeriksa sintaks Kotlin cepat. Lihat ci/tools/KotlinSyntaxCheck.java.
#
#   ci/kotlin_syntax_check.sh              # cek semua *.kt di repo
#   ci/kotlin_syntax_check.sh a.kt b.kt   # cek file tertentu
#
# Tidak butuh Android SDK / Gradle. Yang dibutuhkan: curl, unzip, java, javac.
set -euo pipefail

KOTLIN_VERSION="${KOTLIN_VERSION:-2.2.21}"
CACHE_ROOT="${KOTLIN_SYNTAX_CACHE:-${RUNNER_TEMP:-/tmp}/xydesk-kotlinc-$KOTLIN_VERSION}"
ZIP_URL="https://github.com/JetBrains/kotlin/releases/download/v${KOTLIN_VERSION}/kotlin-compiler-${KOTLIN_VERSION}.zip"

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

if [[ ! -f "$CACHE_ROOT/kotlinc/lib/kotlin-compiler.jar" ]]; then
  echo "==> Mengunduh Kotlin compiler $KOTLIN_VERSION (sekitar 76 MB, sekali saja) ..."
  mkdir -p "$CACHE_ROOT"
  curl -fsSL --retry 3 --max-time 300 "$ZIP_URL" -o "$CACHE_ROOT/kotlin-compiler.zip"
  unzip -q -o "$CACHE_ROOT/kotlin-compiler.zip" -d "$CACHE_ROOT"
fi

build_dir="$CACHE_ROOT/classes"
mkdir -p "$build_dir"
CP="$(find "$CACHE_ROOT/kotlinc/lib" -name '*.jar' -print | paste -sd: -)"

# -proc:none penting: classpath compiler memuat service annotation processor
# (SisuIndexAPT6) yang tidak ada di sini dan bikin javac gagal.
javac -nowarn -proc:none -encoding UTF-8 -cp "$CP" -d "$build_dir" ci/tools/KotlinSyntaxCheck.java

if [[ $# -gt 0 ]]; then
  mapfile -t files < <(printf '%s\n' "$@")
else
  mapfile -t files < <(find . -name '*.kt' -type f -not -path './.git/*' | sort)
fi

if [[ ${#files[@]} -eq 0 ]]; then
  echo "Tidak ada file .kt ditemukan."
  exit 2
fi

echo "==> Memeriksa ${#files[@]} file Kotlin (parser PSI $KOTLIN_VERSION) ..."
java -Xmx2g -Didea.home.path="$CACHE_ROOT/kotlinc" \
  -cp "$build_dir:$CP" KotlinSyntaxCheck "${files[@]}" | tail -n 25
