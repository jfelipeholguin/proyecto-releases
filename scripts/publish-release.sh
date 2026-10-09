#!/usr/bin/env bash
# Publica el APK en GitHub Releases (repo público de releases del proyecto).
#
# Uso:  ./scripts/publish-release.sh 0.1.0            (versionName)
#       ./scripts/publish-release.sh 0.1.0 "Notas..." (con changelog)
#
# Requisitos: gh CLI con sesión activa y el APK ya compilado.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
REPO="jfelipeholguin/proyecto-releases"

VERSION_NAME="${1:?Uso: publish-release.sh <versionName> [notas]}"
NOTES="${2:-Build $VERSION_NAME}"

command -v gh >/dev/null 2>&1 || { echo "ERROR: gh CLI no instalado" >&2; exit 1; }
[[ -f "$APK" ]] || { echo "ERROR: APK no existe — corre ./gradlew :app:assembleDebug primero" >&2; exit 1; }

VERSION_CODE=$(grep -oP 'versionCode\s*=\s*\K[0-9]+' "$ROOT/app/build.gradle.kts")
TAG="v$VERSION_CODE"

echo "==> Publicando $APK como $TAG ($VERSION_NAME) en $REPO"
gh release create "$TAG" "$APK" \
  --repo "$REPO" \
  --title "v$VERSION_NAME" \
  --notes "$NOTES" \
  --latest

echo "==> Listo: https://github.com/$REPO/releases/tag/$TAG"
