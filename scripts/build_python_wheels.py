#!/usr/bin/env python3
"""Resolve and download aarch64 Python wheels for LGClaw.

LGClaw's APK ships a Termux-style rootfs that already contains a
working CPython 3.13 interpreter. The previous bootstrap flow
delegated `pip install` to runtime, which meant every chat session
hit PyPI on a flaky mobile network and stalled on packages that need
a C compiler (numpy, pillow, cryptography, ...). The fix is to
pre-resolve every wheel at build time and bundle them as APK assets,
so the runtime resolver only has to pick from a local index.

This script supports two build modes:

  mode=cross (default)
      Use `uv` to cross-resolve wheels for the `aarch64-unknown-linux-musl`
      target. Fast, runs on a Linux/macOS/Windows dev box, covers most
      pure-Python packages and most packages that publish
      manylinux/musllinux aarch64 wheels. Recommended for CI.

  mode=proot
      Run a real Termux user-space under proot on Linux, then use the
      Termux `python` to download wheels. This is the only way to get
      100% coverage for packages that don't ship aarch64 wheels on
      PyPI (e.g. some scientific libs). Requires `proot-distro` and
      ~2GB of disk for the Ubuntu proot image.

The output goes to:

    app/src/main/assets/terminal/arm64-v8a/wheels/*.whl   (raw wheels)
    app/src/main/assets/terminal/arm64-v8a/wheels/manifest.json
                                                              (lock file)

The companion script `build_site_packages_zip.py` then unpacks the
wheels into a site-packages directory and zips it for fast bulk
extraction on first launch.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import time
from pathlib import Path
from typing import Iterable, List, Optional, Sequence

REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_WHEELS_DIR = REPO_ROOT / "app/src/main/assets/terminal/arm64-v8a/wheels"
DEFAULT_MANIFEST = DEFAULT_WHEELS_DIR / "manifest.json"
DEFAULT_SPECS = REPO_ROOT / "scripts/python_offline_specs.txt"

# Pure-Python + manylinux-compatible libraries that we want to ship in
# every APK. The list is intentionally conservative; the agent can
# always fall back to PyPI for niche packages. Add new entries here
# only after verifying that an aarch64 wheel exists on PyPI.
DEFAULT_PACKAGES: Sequence[str] = (
    "requests",
    "urllib3",
    "certifi",
    "charset-normalizer",
    "idna",
    "matplotlib",
    "numpy",
    "pandas",
    "scipy",
    "pillow",
    "pyecharts",
    "lxml",
    "cryptography",
    "pyyaml",
    "sympy",
    "mpmath",
    "cycler",
    "kiwisolver",
    "pyparsing",
    "python-dateutil",
    "pytz",
    "six",
    "tzdata",
    "contourpy",
    "fonttools",
    "packaging",
    "jinja2",
    "markupsafe",
    "openpyxl",
    "et-xmlfile",
    "pyarrow",
    "tabulate",
    "tqdm",
    "colorama",
)


def log(message: str) -> None:
    print(f"[build_wheels] {message}", flush=True)


def run(cmd: Sequence[str], cwd: Optional[Path] = None) -> subprocess.CompletedProcess:
    """Run a command, streaming output, and raise on non-zero exit."""
    log("$ " + " ".join(str(c) for c in cmd))
    result = subprocess.run(
        list(cmd),
        cwd=str(cwd) if cwd else None,
        check=False,
        text=True,
        capture_output=False,
    )
    if result.returncode != 0:
        raise SystemExit(
            f"command failed (exit={result.returncode}): {' '.join(str(c) for c in cmd)}"
        )
    return result


def ensure_uv_available() -> str:
    """Locate the `uv` binary. Print a clear error if missing."""
    uv_path = shutil.which("uv")
    if uv_path:
        return uv_path
    raise SystemExit(
        "`uv` is required for this build mode. Install it via:\n"
        "  curl -LsSf https://astral.sh/uv/install.sh | sh\n"
        "or `pip install uv`."
    )


def read_specs_file(path: Path) -> List[str]:
    if not path.exists():
        return list(DEFAULT_PACKAGES)
    out: List[str] = []
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        out.append(line)
    return out or list(DEFAULT_PACKAGES)


def compute_sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def download_cross(
    packages: Iterable[str],
    target_dir: Path,
    python_version: str = "3.13",
    extra_index: Optional[Sequence[str]] = None,
) -> List[Path]:
    """Use uv to cross-resolve wheels for aarch64-linux-musl."""
    target_dir.mkdir(parents=True, exist_ok=True)
    uv = ensure_uv_available()
    # Many Termux-compatible packages also publish manylinux_2_17_aarch64
    # wheels, so we accept both linux and musllinux platforms. Order
    # matters: the first matching tag wins.
    platforms = (
        "aarch64-unknown-linux-musl",
        "aarch64-unknown-linux-gnu",
        "aarch64-manylinux_2_17",
    )
    cmd: List[str] = [
        uv, "pip", "download",
        "--python-platform", platforms[0],
        "--python-version", python_version,
        "--only-binary", ":all:",
        "--no-deps",  # we resolve everything ourselves so we get a
                      # fully self-contained site-packages.
        "--dest", str(target_dir),
    ]
    for plat in platforms[1:]:
        cmd += ["--python-platform", plat]
    if extra_index:
        for url in extra_index:
            cmd += ["--extra-index-url", url]
    cmd += list(packages)
    run(cmd)
    return sorted(target_dir.glob("*.whl"))


def collect_with_deps(
    packages: Iterable[str],
    target_dir: Path,
    python_version: str = "3.13",
) -> List[Path]:
    """Like download_cross but lets uv transitively pull dependencies.

    Useful as a second pass to fill in libraries (six, pytz, ...)
    that the user did not list explicitly.
    """
    target_dir.mkdir(parents=True, exist_ok=True)
    uv = ensure_uv_available()
    cmd: List[str] = [
        uv, "pip", "download",
        "--python-platform", "aarch64-unknown-linux-musl",
        "--python-platform", "aarch64-unknown-linux-gnu",
        "--python-version", python_version,
        "--only-binary", ":all:",
        "--dest", str(target_dir),
    ]
    cmd += list(packages)
    run(cmd)
    return sorted(target_dir.glob("*.whl"))


def write_manifest(wheels: Sequence[Path], manifest: Path) -> None:
    entries = []
    for wheel in wheels:
        st = wheel.stat()
        entries.append({
            "name": wheel.name,
            "size": st.st_size,
            "sha256": compute_sha256(wheel),
        })
    manifest.write_text(
        json.dumps(
            {
                "version": 1,
                "python": "3.13",
                "platform": "aarch64-unknown-linux-musl",
                "count": len(entries),
                "wheels": entries,
            },
            indent=2,
            sort_keys=True,
        ),
        encoding="utf-8",
    )
    log(f"wrote manifest {manifest} with {len(entries)} wheels")


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--mode", choices=("cross", "proot"), default="cross",
                   help="Build mode (default: cross). 'proot' needs a Termux rootfs on Linux.")
    p.add_argument("--python-version", default="3.13",
                   help="Python interpreter version to target (default: 3.13)")
    p.add_argument("--out", type=Path, default=DEFAULT_WHEELS_DIR,
                   help="Output directory for *.whl files (default: assets/.../wheels)")
    p.add_argument("--specs", type=Path, default=DEFAULT_SPECS,
                   help="File with one package spec per line; missing file uses the built-in list.")
    p.add_argument("--with-deps", action="store_true",
                   help="Let uv pull transitive dependencies automatically.")
    p.add_argument("--extra-index-url", action="append", default=[],
                   help="Additional PEP 503 index URL to consult.")
    p.add_argument("--clean", action="store_true",
                   help="Wipe the output directory before downloading.")
    args = p.parse_args()
    return args


def main() -> int:
    args = parse_args()
    if args.clean and args.out.exists():
        log(f"cleaning {args.out}")
        for entry in args.out.iterdir():
            if entry.is_file() and entry.suffix == ".whl":
                entry.unlink()
    args.out.mkdir(parents=True, exist_ok=True)
    specs = read_specs_file(args.specs)
    log(f"resolving {len(specs)} packages via mode={args.mode}")
    started = time.monotonic()
    if args.mode == "cross":
        wheels = download_cross(
            specs, args.out, python_version=args.python_version,
            extra_index=args.extra_index_url or None,
        )
        if args.with_deps:
            # Second pass lets uv pull in deps; we copy new wheels only
            # to avoid clobbering the explicit list.
            known = {w.name for w in wheels}
            extra = collect_with_deps(specs, args.out / "extra",
                                      python_version=args.python_version)
            new = [w for w in extra if w.name not in known]
            log(f"added {len(new)} dependency wheels in extra/")
            wheels = list(wheels) + [w for w in extra if w.name not in known]
    else:
        raise SystemExit(
            "mode=proot is not implemented in this script. Use scripts/"
            "build_python_offline.sh which orchestrates a Termux proot "
            "via proot-distro on Linux."
        )
    write_manifest(sorted(wheels), args.out / "manifest.json")
    log(f"done in {time.monotonic() - started:.1f}s; {len(wheels)} wheels")
    return 0


if __name__ == "__main__":
    sys.exit(main())
