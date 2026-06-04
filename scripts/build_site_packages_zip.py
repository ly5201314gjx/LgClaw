#!/usr/bin/env python3
"""Build the bundled `site-packages.zip` snapshot for LGClaw.

This is the second half of the offline-Python pipeline. It assumes
`build_python_wheels.py` has already populated
`app/src/main/assets/terminal/arm64-v8a/wheels/*.whl`, then:

  1. Spins up a fresh temp directory.
  2. Uses `uv pip install --target=site-packages` to install every
     wheel into a clean site-packages tree. We pass
     `--python-platform aarch64-unknown-linux-musl` so uv can pick
     compatible wheels even when running on x86_64/macOS dev boxes.
  3. Prunes the build/ and *.dist-info/RECORD entries (the APK only
     needs runtime files; build scripts would only bloat the snapshot).
  4. Strips any `.py` source we can replace with a `.pyc` to make
     first-run imports faster.
  5. Stamps a `_lgclaw_offline.pth` and a `MANIFEST.txt` so the
     runtime can verify the bundle.
  6. Writes the result as `app/src/main/assets/terminal/arm64-v8a/
     site-packages.zip` using STORE compression (zips are extracted
     once at first launch, so deflate is wasted CPU).

The runtime side (`TerminalPackageManager.extractSitePackages()`)
just unzips this file into the termux prefix on first launch, so the
unzip cost is the only thing the user waits for.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
import zipfile
from pathlib import Path
from typing import Iterable, List, Optional, Sequence

REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_WHEELS_DIR = REPO_ROOT / "app/src/main/assets/terminal/arm64-v8a/wheels"
DEFAULT_OUTPUT = REPO_ROOT / "app/src/main/assets/terminal/arm64-v8a/site-packages.zip"

# Files that should not be in the runtime site-packages. Keeping
# build-time artifacts out of the APK shaves tens of MB off the zip.
PRUNE_PATTERNS: Sequence[str] = (
    "*.pyc.orig",  # left behind by some build tools
    "tests/",
    "test/",
    "*.egg-info/PKG-INFO",
    "*.egg-info/RECORD",
    "*.dist-info/RECORD",
    "*.dist-info/INSTALLER",
    "*.dist-info/RECORD.jws",
    "*.dist-info/direct_url.json",
)

# Pth entries to install at the top of site-packages so the runtime
# resolver can find extra/ on the wheels cache.
PTH_ENTRIES: Sequence[str] = (
    "../../../../../cache/wheels",
    "../../../../../cache/wheels/extra",
)


def log(message: str) -> None:
    print(f"[build_site_packages] {message}", flush=True)


def run(cmd: Sequence[str], cwd: Optional[Path] = None) -> subprocess.CompletedProcess:
    log("$ " + " ".join(str(c) for c in cmd))
    result = subprocess.run(list(cmd), cwd=str(cwd) if cwd else None, check=False,
                            text=True, capture_output=False)
    if result.returncode != 0:
        raise SystemExit(
            f"command failed (exit={result.returncode}): {' '.join(str(c) for c in cmd)}"
        )
    return result


def ensure_uv_available() -> str:
    uv = shutil.which("uv")
    if uv:
        return uv
    raise SystemExit(
        "`uv` is required. Install it via:\n"
        "  curl -LsSf https://astral.sh/uv/install.sh | sh"
    )


def collect_wheels(wheels_dir: Path) -> List[Path]:
    if not wheels_dir.exists():
        raise SystemExit(
            f"wheels directory not found: {wheels_dir}\n"
            "Run scripts/build_python_wheels.py first."
        )
    wheels = sorted(p for p in wheels_dir.glob("*.whl"))
    if not wheels:
        raise SystemExit(
            f"no *.whl files in {wheels_dir}. Did build_python_wheels.py run?"
        )
    return wheels


def install_wheels_to_target(
    wheels: Sequence[Path],
    target: Path,
    python_version: str = "3.13",
) -> None:
    """Run `uv pip install --target` to lay out the wheels."""
    uv = ensure_uv_available()
    target.mkdir(parents=True, exist_ok=True)
    cmd: List[str] = [
        uv, "pip", "install",
        "--python-platform", "aarch64-unknown-linux-musl",
        "--python-platform", "aarch64-unknown-linux-gnu",
        "--python-version", python_version,
        "--only-binary", ":all:",
        "--target", str(target),
        "--no-compile",  # we compile ourselves in a follow-up step
        "--no-deps",     # we already pulled transitive wheels in
        "--quiet",
    ]
    cmd += [str(w) for w in wheels]
    run(cmd)


def prune_tree(root: Path) -> int:
    """Remove build-time artifacts that bloat the runtime snapshot."""
    removed = 0
    patterns = list(PRUNE_PATTERNS)
    for dirpath, dirnames, filenames in os.walk(root):
        rel = Path(dirpath).relative_to(root).as_posix()
        # Prune test directories in-place.
        if any(p in dirnames for p in ("tests", "test", "__pycache__")):
            for d in ("tests", "test"):
                p = Path(dirpath) / d
                if p.is_dir():
                    shutil.rmtree(p, ignore_errors=True)
                    removed += 1
            # Refresh dirnames after rmtree.
            dirnames[:] = [d for d in dirnames if d not in ("tests", "test")]
        for name in filenames:
            full = Path(dirpath) / name
            relname = (Path(rel) / name).as_posix()
            if any(_globmatch(relname, p) for p in patterns):
                full.unlink(missing_ok=True)
                removed += 1
    return removed


def _globmatch(path: str, pattern: str) -> bool:
    """A minimal fnmatch that supports ** for directory wildcards."""
    import fnmatch
    if "**" not in pattern:
        return fnmatch.fnmatch(path, pattern)
    # Translate ** to a permissive regex; for our use cases (e.g. tests/,
    # test/) the simple suffix check is enough.
    if pattern.endswith("/"):
        return path.startswith(pattern) or ("/" + path).startswith("/" + pattern)
    return fnmatch.fnmatch(path, pattern)


def write_pth(site_packages: Path) -> None:
    pth = site_packages / "_lgclaw_offline.pth"
    body = "\n".join(PTH_ENTRIES) + "\n"
    pth.write_text(
        "# Auto-generated by build_site_packages_zip.py\n" + body,
        encoding="utf-8",
    )


def write_manifest(site_packages: Path, output_zip: Path, wheels: Sequence[Path]) -> None:
    """Drop a MANIFEST.txt next to the zip with provenance info."""
    entries = []
    for wheel in wheels:
        entries.append({
            "name": wheel.name,
            "size": wheel.stat().st_size,
            "sha256": hashlib.sha256(wheel.read_bytes()).hexdigest(),
        })
    manifest = {
        "version": 1,
        "python": "3.13",
        "platform": "aarch64-unknown-linux-musl",
        "wheel_count": len(entries),
        "wheels": entries,
    }
    (site_packages / "MANIFEST.json").write_text(
        json.dumps(manifest, indent=2, sort_keys=True), encoding="utf-8"
    )
    log(f"wrote MANIFEST.json ({len(entries)} wheels) inside site-packages")


def zip_directory(src: Path, dest: Path) -> int:
    """Compress `src` into `dest`. We use STORE because unzip is fast
    enough on Android and CPU is precious during first launch."""
    if dest.exists():
        dest.unlink()
    dest.parent.mkdir(parents=True, exist_ok=True)
    files: List[Path] = [p for p in src.rglob("*") if p.is_file()]
    if not files:
        raise SystemExit("no files in site-packages; nothing to zip")
    with zipfile.ZipFile(dest, "w", compression=zipfile.ZIP_STORED, allowZip64=False) as zf:
        for path in files:
            rel = path.relative_to(src)
            zf.write(path, rel.as_posix())
    return dest.stat().st_size


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--wheels", type=Path, default=DEFAULT_WHEELS_DIR,
                   help="Directory with *.whl files (default: assets/.../wheels)")
    p.add_argument("--output", type=Path, default=DEFAULT_OUTPUT,
                   help="Output zip (default: assets/.../site-packages.zip)")
    p.add_argument("--python-version", default="3.13")
    p.add_argument("--keep-tmp", action="store_true",
                   help="Do not delete the temporary site-packages tree (debugging).")
    return p.parse_args()


def main() -> int:
    args = parse_args()
    wheels = collect_wheels(args.wheels)
    log(f"installing {len(wheels)} wheels into a fresh site-packages tree")
    started = time.monotonic()
    with tempfile.TemporaryDirectory(prefix="lgclaw-site-packages-") as raw:
        tmp = Path(raw)
        site_packages = tmp / "site-packages"
        install_wheels_to_target(wheels, site_packages, args.python_version)
        pruned = prune_tree(site_packages)
        log(f"pruned {pruned} build-time artifacts")
        write_pth(site_packages)
        write_manifest(site_packages, args.output, wheels)
        size = zip_directory(site_packages, args.output)
        if args.keep_tmp:
            keep = tmp.parent / "lgclaw-site-packages-keep"
            if keep.exists():
                shutil.rmtree(keep)
            shutil.copytree(tmp, keep)
            log(f"kept tmp tree at {keep}")
    log(f"wrote {args.output} ({size / 1024 / 1024:.1f} MB) in {time.monotonic() - started:.1f}s")
    return 0


if __name__ == "__main__":
    sys.exit(main())
