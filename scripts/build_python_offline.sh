#!/usr/bin/env bash
# Build the offline Python bundle (wheels + site-packages.zip) for
# LGClaw. Designed to run in CI on Ubuntu but works fine on a
# developer machine. Windows users should run this from WSL.
#
# Usage:
#   scripts/build_python_offline.sh                # cross-resolve via uv
#   scripts/build_python_offline.sh --with-deps     # also pull transitive deps
#   scripts/build_python_offline.sh --mode=proot    # use real Termux proot
#   scripts/build_python_offline.sh --check         # verify the bundle is intact
#
# The script never touches the user's home directory. All artifacts
# land in:
#   app/src/main/assets/terminal/arm64-v8a/wheels/
#   app/src/main/assets/terminal/arm64-v8a/site-packages.zip
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WHEELS_DIR="$REPO_ROOT/app/src/main/assets/terminal/arm64-v8a/wheels"
SITE_PACKAGES_ZIP="$REPO_ROOT/app/src/main/assets/terminal/arm64-v8a/site-packages.zip"
SPECS_FILE="$REPO_ROOT/scripts/python_offline_specs.txt"

log() { printf "[build_python_offline] %s\n" "$*"; }
die() { printf "[build_python_offline] ERROR: %s\n" "$*" >&2; exit 1; }

PYTHON_VERSION="3.13"
MODE="cross"
WITH_DEPS=0
CHECK_ONLY=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --with-deps) WITH_DEPS=1; shift ;;
    --mode)      MODE="$2"; shift 2 ;;
    --check)     CHECK_ONLY=1; shift ;;
    --python-version) PYTHON_VERSION="$2"; shift 2 ;;
    -h|--help)
      sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *) die "unknown argument: $1" ;;
  esac
done

# Locate Python. We prefer python3 on PATH; fall back to common
# locations on macOS and WSL.
PYTHON_BIN="${PYTHON_BIN:-python3}"
if ! command -v "$PYTHON_BIN" >/dev/null 2>&1; then
  for candidate in /usr/bin/python3 /opt/homebrew/bin/python3 /usr/local/bin/python3; do
    [[ -x "$candidate" ]] && PYTHON_BIN="$candidate" && break
  done
fi
[[ -x "$(command -v "$PYTHON_BIN" || true)" ]] || die "python3 not found on PATH"

check_bundle() {
  log "verifying bundle"
  [[ -d "$WHEELS_DIR" ]] || die "missing $WHEELS_DIR (run build_python_wheels.py)"
  local count
  count=$(find "$WHEELS_DIR" -maxdepth 2 -name '*.whl' | wc -l | tr -d ' ')
  if [[ "$count" -eq 0 ]]; then
    die "no wheels in $WHEELS_DIR"
  fi
  log "found $count wheels in $WHEELS_DIR"
  if [[ ! -f "$SITE_PACKAGES_ZIP" ]]; then
    log "WARNING: $SITE_PACKAGES_ZIP is missing; first launch will be slow"
    return 0
  fi
  local size
  size=$(du -h "$SITE_PACKAGES_ZIP" | cut -f1)
  log "site-packages.zip is $size"
  log "verifying zip integrity"
  if ! unzip -tqq "$SITE_PACKAGES_ZIP" >/dev/null; then
    die "site-packages.zip is corrupt; rebuild with: $0"
  fi
  log "bundle looks healthy"
}

if [[ "$CHECK_ONLY" -eq 1 ]]; then
  check_bundle
  exit 0
fi

log "build mode: $MODE"
log "python: $($PYTHON_BIN --version 2>&1)"
log "wheels dir: $WHEELS_DIR"
log "site-packages zip: $SITE_PACKAGES_ZIP"

# Step 1: resolve and download wheels.
log "step 1/2: resolving wheels"
ARGS=(--mode "$MODE" --python-version "$PYTHON_VERSION" --out "$WHEELS_DIR" --clean)
[[ -f "$SPECS_FILE" ]] && ARGS+=(--specs "$SPECS_FILE")
[[ "$WITH_DEPS" -eq 1 ]] && ARGS+=(--with-deps)
"$PYTHON_BIN" "$REPO_ROOT/scripts/build_python_wheels.py" "${ARGS[@]}"

# Step 2: assemble site-packages.zip.
log "step 2/2: assembling site-packages.zip"
"$PYTHON_BIN" "$REPO_ROOT/scripts/build_site_packages_zip.py" \
  --wheels "$WHEELS_DIR" \
  --output "$SITE_PACKAGES_ZIP" \
  --python-version "$PYTHON_VERSION"

# Step 3: verify.
check_bundle
log "done. Now rebuild the APK with: ./gradlew assembleDebug"
