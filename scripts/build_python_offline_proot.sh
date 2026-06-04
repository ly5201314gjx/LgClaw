#!/usr/bin/env bash
# Termux-proot build of the offline Python bundle. Use this when
# `build_python_offline.sh --mode=cross` cannot resolve aarch64
# wheels for every package (most common with scientific libraries
# that bundle C extensions built against Android Bionic).
#
# Requirements (Linux only):
#   * proot-distro  (apt install proot-distro)
#   * ~2 GB free disk for the Ubuntu proot image
#   * Working internet during the build
#
# This script is intentionally separate from the cross-resolve
# build: cross is the default for CI speed, proot is the fallback
# for completeness.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WHEELS_DIR="$REPO_ROOT/app/src/main/assets/terminal/arm64-v8a/wheels"
SITE_PACKAGES_ZIP="$REPO_ROOT/app/src/main/assets/terminal/arm64-v8a/site-packages.zip"
SPECS_FILE="$REPO_ROOT/scripts/python_offline_specs.txt"
PROOT_IMAGE="ubuntu-24.04"

log() { printf "[build_python_offline_proot] %s\n" "$*"; }
die() { printf "[build_python_offline_proot] ERROR: %s\n" "$*" >&2; exit 1; }

command -v proot-distro >/dev/null 2>&1 || die "proot-distro not found; install it first"
[[ -f "$SPECS_FILE" ]] || die "specs file missing: $SPECS_FILE"

log "booting $PROOT_IMAGE proot (this may take a minute on first run)"
proot-distro install "$PROOT_IMAGE" 2>/dev/null || true

log "installing python3 + pip inside the proot"
proot-distro login "$PROOT_IMAGE" -- bash -lc "set -e; export DEBIAN_FRONTEND=noninteractive; \
  apt-get update -qq && \
  apt-get install -y -qq python3 python3-pip python3-venv >/dev/null && \
  python3 -m pip install --quiet --upgrade pip uv"

log "resolving wheels inside the proot"
WHEELS_DIR_ABS="$WHEELS_DIR"
mkdir -p "$WHEELS_DIR_ABS"
proot-distro login "$PROOT_IMAGE" -- bash -lc "set -e; \
  mkdir -p /opt/lgclaw/wheels && \
  cd /opt/lgclaw/wheels && \
  python3 -m pip download --quiet --only-binary=:all: --dest . -r /mnt/specs.txt && \
  python3 -m pip download --quiet --only-binary=:all: --dest /opt/lgclaw/wheels/extra -r /mnt/specs.txt"
# Note: the spec file is bind-mounted at /mnt/specs.txt by the caller
# (we just symlink the host file into the proot's view below).
ln -sf "$SPECS_FILE" "/tmp/lgclaw-specs.txt" 2>/dev/null || true
proot-distro login "$PROOT_IMAGE" --bind "$WHEELS_DIR_ABS:/opt/lgclaw/wheels:rw" --bind "$SPECS_FILE:/mnt/specs.txt:ro" -- bash -lc "set -e; \
  rm -rf /opt/lgclaw/wheels/* /opt/lgclaw/wheels/extra/* && \
  python3 -m pip download --only-binary=:all: --dest /opt/lgclaw/wheels -r /mnt/specs.txt && \
  python3 -m pip download --only-binary=:all: --dest /opt/lgclaw/wheels/extra -r /mnt/specs.txt"

log "wheels written to $WHEELS_DIR_ABS; now building site-packages.zip"
"$(command -v python3)" "$REPO_ROOT/scripts/build_site_packages_zip.py" \
  --wheels "$WHEELS_DIR_ABS" \
  --output "$SITE_PACKAGES_ZIP"

log "done. Verify with: scripts/build_python_offline.sh --check"
